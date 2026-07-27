package me.aap.fermata.addon.stremio.subtitle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import me.aap.fermata.media.sub.FileSubtitles;

/** Bounded text decoding, format sniffing, conversion and parser validation. */
public final class SubtitlePayloadProcessor {
	private static final Pattern ASS_TAG = Pattern.compile("\\{[^}]*}");
	private static final Pattern TTML_CLOCK = Pattern.compile(
			"(?:(\\d+):)?(\\d{1,2}):(\\d{2})(?:[.,](\\d{1,3}))?");

	private SubtitlePayloadProcessor() {
	}

	public static byte[] process(byte[] payload, SubtitleFormat declared) throws IOException {
		if ((payload == null) || (payload.length == 0)) {
			throw new IOException("Subtitle payload is empty");
		}
		String text = decode(payload).replace("\r\n", "\n").replace('\r', '\n');
		SubtitleFormat actual = sniff(text, declared);
		String normalized = switch (actual) {
			case SUBRIP, WEBVTT -> text;
			case ASS, SSA -> assToSrt(text);
			case TTML -> ttmlToSrt(text);
			default -> throw new IOException("Unsupported subtitle payload format");
		};
		byte[] output = normalized.getBytes(StandardCharsets.UTF_8);
		if (output.length > SubtitleAggregator.MAX_SUBTITLE_FILE_BYTES) {
			throw new IOException("Subtitle payload exceeds the size limit");
		}
		if (FileSubtitles.load(new ByteArrayInputStream(output)).isEmpty()) {
			throw new IOException("Subtitle payload contains no valid cues");
		}
		return output;
	}

	static SubtitleFormat sniff(String text, SubtitleFormat declared) {
		String value = text.stripLeading();
		if (value.regionMatches(true, 0, "WEBVTT", 0, 6)) return SubtitleFormat.WEBVTT;
		if (value.startsWith("<") &&
				(value.contains("<tt") || value.contains(":tt"))) return SubtitleFormat.TTML;
		if (value.contains("[Events]") || value.contains("Dialogue:")) {
			return (declared == SubtitleFormat.SSA) ? SubtitleFormat.SSA : SubtitleFormat.ASS;
		}
		if (value.contains(" --> ")) return SubtitleFormat.SUBRIP;
		return declared;
	}

	private static String decode(byte[] payload) throws IOException {
		int offset = 0;
		if ((payload.length >= 3) && ((payload[0] & 0xff) == 0xef) &&
				((payload[1] & 0xff) == 0xbb) && ((payload[2] & 0xff) == 0xbf)) offset = 3;
		if ((payload.length >= 2) && ((payload[0] & 0xff) == 0xff) &&
				((payload[1] & 0xff) == 0xfe)) {
			return new String(payload, 2, payload.length - 2, StandardCharsets.UTF_16LE);
		}
		if ((payload.length >= 2) && ((payload[0] & 0xff) == 0xfe) &&
				((payload[1] & 0xff) == 0xff)) {
			return new String(payload, 2, payload.length - 2, StandardCharsets.UTF_16BE);
		}
		try {
			CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(payload, offset, payload.length - offset));
			return decoded.toString();
		} catch (CharacterCodingException invalidUtf8) {
			return new String(payload, offset, payload.length - offset,
					java.nio.charset.Charset.forName("windows-1252"));
		}
	}

	private static String assToSrt(String text) throws IOException {
		List<String> fields = List.of("layer", "start", "end", "style", "name",
				"marginl", "marginr", "marginv", "effect", "text");
		List<Cue> cues = new ArrayList<>();
		boolean events = false;
		for (String line : text.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
				events = "[events]".equals(trimmed.toLowerCase(Locale.ROOT));
				continue;
			}
			if (!events) continue;
			if (trimmed.regionMatches(true, 0, "Format:", 0, 7)) {
				fields = java.util.Arrays.stream(trimmed.substring(7).split(","))
						.map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
			} else if (trimmed.regionMatches(true, 0, "Dialogue:", 0, 9)) {
				String[] values = trimmed.substring(9).split(",", fields.size());
				int start = fields.indexOf("start");
				int end = fields.indexOf("end");
				int body = fields.indexOf("text");
				if ((start < 0) || (end < 0) || (body < 0) || (values.length <= body)) continue;
				String value = ASS_TAG.matcher(values[body]).replaceAll("")
						.replace("\\N", "\n").replace("\\n", "\n").trim();
				if (!value.isEmpty()) cues.add(new Cue(assTime(values[start]),
						assTime(values[end]), value));
			}
		}
		if (cues.isEmpty()) throw new IOException("ASS subtitle contains no cues");
		return toSrt(cues);
	}

	private static long assTime(String value) throws IOException {
		String[] hms = value.trim().split(":");
		if (hms.length != 3) throw new IOException("Invalid ASS timestamp");
		try {
			return (Long.parseLong(hms[0]) * 3_600_000L) +
					(Long.parseLong(hms[1]) * 60_000L) +
					Math.round(Double.parseDouble(hms[2].replace(',', '.')) * 1_000d);
		} catch (NumberFormatException error) {
			throw new IOException("Invalid ASS timestamp", error);
		}
	}

	private static String ttmlToSrt(String text) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			var document = factory.newDocumentBuilder().parse(
					new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
			var paragraphs = document.getElementsByTagNameNS("*", "p");
			List<Cue> cues = new ArrayList<>();
			for (int i = 0; i < paragraphs.getLength(); i++) {
				var paragraph = (org.w3c.dom.Element) paragraphs.item(i);
				long begin = ttmlTime(paragraph.getAttribute("begin"));
				String endValue = paragraph.getAttribute("end");
				long end = endValue.isBlank() ?
						begin + ttmlTime(paragraph.getAttribute("dur")) : ttmlTime(endValue);
				String value = paragraph.getTextContent().replaceAll("\\s+", " ").trim();
				if (!value.isEmpty() && (end > begin)) cues.add(new Cue(begin, end, value));
			}
			if (cues.isEmpty()) throw new IOException("TTML subtitle contains no cues");
			return toSrt(cues);
		} catch (IOException error) {
			throw error;
		} catch (Exception error) {
			throw new IOException("Invalid TTML subtitle", error);
		}
	}

	private static long ttmlTime(String value) throws IOException {
		String normalized = value.trim();
		try {
			if (normalized.endsWith("ms")) {
				return Math.round(Double.parseDouble(
						normalized.substring(0, normalized.length() - 2)));
			}
			if (normalized.endsWith("s")) {
				return Math.round(Double.parseDouble(
						normalized.substring(0, normalized.length() - 1)) * 1_000d);
			}
			Matcher match = TTML_CLOCK.matcher(normalized);
			if (!match.matches()) throw new NumberFormatException();
			long hours = (match.group(1) == null) ? 0L : Long.parseLong(match.group(1));
			long millis = (match.group(4) == null) ? 0L :
					Long.parseLong((match.group(4) + "000").substring(0, 3));
			return hours * 3_600_000L + Long.parseLong(match.group(2)) * 60_000L +
					Long.parseLong(match.group(3)) * 1_000L + millis;
		} catch (NumberFormatException error) {
			throw new IOException("Invalid TTML timestamp", error);
		}
	}

	private static String toSrt(List<Cue> cues) {
		StringBuilder output = new StringBuilder();
		for (int i = 0; i < cues.size(); i++) {
			Cue cue = cues.get(i);
			output.append(i + 1).append('\n').append(time(cue.start())).append(" --> ")
					.append(time(cue.end())).append('\n').append(cue.text()).append("\n\n");
		}
		return output.toString();
	}

	private static String time(long millis) {
		long hours = millis / 3_600_000L;
		millis %= 3_600_000L;
		long minutes = millis / 60_000L;
		millis %= 60_000L;
		long seconds = millis / 1_000L;
		return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
				hours, minutes, seconds, millis % 1_000L);
	}

	private record Cue(long start, long end, String text) {
	}
}
