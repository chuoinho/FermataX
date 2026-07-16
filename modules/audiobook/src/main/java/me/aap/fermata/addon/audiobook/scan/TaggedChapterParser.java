package me.aap.fermata.addon.audiobook.scan;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import me.aap.fermata.addon.audiobook.scan.EmbeddedChapterParser.ChapterMark;

/** Bounded parser for ID3 CHAP and Xiph CHAPTERxx comments. */
final class TaggedChapterParser {
	private static final int MAX_TAG_BYTES = 32 * 1024 * 1024;
	private static final int MAX_FRAME_BYTES = 1024 * 1024;
	private static final int MAX_COMMENT_BYTES = 4 * 1024 * 1024;
	private static final int MAX_COMMENTS = 100_000;
	private final Context context;

	TaggedChapterParser(Context context) {
		this.context = context.getApplicationContext();
	}

	List<ChapterMark> parse(Uri uri, String extension) {
		try (SeekableMediaInput input = SeekableMediaInput.open(context, uri)) {
			return switch (extension) {
				case "mp3" -> parseId3(input);
				case "ogg", "opus" -> parseOgg(input);
				case "flac" -> parseFlac(input);
				default -> List.of();
			};
		} catch (Throwable ignore) {
			return List.of();
		}
	}

	private static List<ChapterMark> parseId3(SeekableMediaInput input) throws IOException {
		byte[] header = new byte[10];
		input.readFully(0, header);
		if ((header[0] != 'I') || (header[1] != 'D') || (header[2] != '3')) return List.of();
		int version = header[3] & 0xff;
		if ((version != 3) && (version != 4)) return List.of();
		long tagSize = synchsafe(header, 6);
		if ((tagSize <= 0) || (tagSize > MAX_TAG_BYTES)) return List.of();
		long position = 10;
		long end = Math.min(input.length(), position + tagSize);
		if ((header[5] & 0x40) != 0) {
			byte[] size = new byte[4];
			input.readFully(position, size);
			long extended = (version == 4) ? synchsafe(size, 0) : unsignedInt(size, 0) + 4;
			if ((extended < 4) || (position + extended > end)) return List.of();
			position += extended;
		}

		List<ChapterMark> result = new ArrayList<>();
		byte[] frameHeader = new byte[10];
		while (position + frameHeader.length <= end) {
			input.readFully(position, frameHeader);
			if ((frameHeader[0] == 0) && (frameHeader[1] == 0) &&
					(frameHeader[2] == 0) && (frameHeader[3] == 0)) break;
			String id = new String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1);
			long frameSize = (version == 4) ? synchsafe(frameHeader, 4) :
					unsignedInt(frameHeader, 4);
			long payload = position + frameHeader.length;
			if ((frameSize <= 0) || (payload + frameSize > end)) break;
			if (id.equals("CHAP") && (frameSize <= MAX_FRAME_BYTES)) {
				byte[] data = new byte[(int) frameSize];
				input.readFully(payload, data);
				ChapterMark chapter = parseChapFrame(data, version);
				if (chapter != null) result.add(chapter);
			}
			position = payload + frameSize;
		}
		return result;
	}

	private static ChapterMark parseChapFrame(byte[] data, int version) {
		int cursor = 0;
		while ((cursor < data.length) && (data[cursor] != 0)) cursor++;
		if (cursor + 17 > data.length) return null;
		cursor++;
		long start = unsignedInt(data, cursor);
		cursor += 16;
		String title = "";
		while (cursor + 10 <= data.length) {
			String id = new String(data, cursor, 4, StandardCharsets.ISO_8859_1);
			long size = (version == 4) ? synchsafe(data, cursor + 4) :
					unsignedInt(data, cursor + 4);
			cursor += 10;
			if ((size <= 0) || (size > Integer.MAX_VALUE) || (cursor + size > data.length)) break;
			if (id.equals("TIT2")) title = decodeText(data, cursor, (int) size);
			cursor += (int) size;
		}
		return new ChapterMark(start, title);
	}

	private static String decodeText(byte[] data, int offset, int length) {
		if (length <= 1) return "";
		int encoding = data[offset] & 0xff;
		Charset charset = switch (encoding) {
			case 1 -> StandardCharsets.UTF_16;
			case 2 -> StandardCharsets.UTF_16BE;
			case 3 -> StandardCharsets.UTF_8;
			default -> StandardCharsets.ISO_8859_1;
		};
		String value = new String(data, offset + 1, length - 1, charset);
		int zero = value.indexOf('\0');
		return ((zero < 0) ? value : value.substring(0, zero)).trim();
	}

	private static List<ChapterMark> parseOgg(SeekableMediaInput input) throws IOException {
		long position = 0;
		long fileLength = input.length();
		ByteArrayOutputStream packet = new ByteArrayOutputStream();
		for (int page = 0; (page < 64) && (position + 27 <= fileLength); page++) {
			byte[] header = new byte[27];
			input.readFully(position, header);
			if ((header[0] != 'O') || (header[1] != 'g') ||
					(header[2] != 'g') || (header[3] != 'S')) return List.of();
			int segmentCount = header[26] & 0xff;
			byte[] laces = new byte[segmentCount];
			input.readFully(position + 27, laces);
			int payloadSize = 0;
			for (byte lace : laces) payloadSize += lace & 0xff;
			if ((payloadSize < 0) || (packet.size() + payloadSize > MAX_COMMENT_BYTES)) return List.of();
			byte[] payload = new byte[payloadSize];
			input.readFully(position + 27 + segmentCount, payload);
			int payloadOffset = 0;
			for (byte laceValue : laces) {
				int lace = laceValue & 0xff;
				packet.write(payload, payloadOffset, lace);
				payloadOffset += lace;
				if (lace < 255) {
					byte[] completed = packet.toByteArray();
					int commentOffset = commentOffset(completed);
					if (commentOffset >= 0) return parseVorbisComments(completed, commentOffset);
					packet.reset();
				}
			}
			position += 27L + segmentCount + payloadSize;
		}
		return List.of();
	}

	private static int commentOffset(byte[] packet) {
		byte[] opus = "OpusTags".getBytes(StandardCharsets.US_ASCII);
		if (startsWith(packet, opus, 0)) return opus.length;
		byte[] vorbis = "vorbis".getBytes(StandardCharsets.US_ASCII);
		if ((packet.length > 7) && (packet[0] == 3) && startsWith(packet, vorbis, 1)) return 7;
		return -1;
	}

	private static List<ChapterMark> parseFlac(SeekableMediaInput input) throws IOException {
		byte[] signature = new byte[4];
		input.readFully(0, signature);
		if (!startsWith(signature, "fLaC".getBytes(StandardCharsets.US_ASCII), 0)) {
			return List.of();
		}
		long position = 4;
		for (int block = 0; block < 128; block++) {
			byte[] header = new byte[4];
			input.readFully(position, header);
			boolean last = (header[0] & 0x80) != 0;
			int type = header[0] & 0x7f;
			int length = ((header[1] & 0xff) << 16) | ((header[2] & 0xff) << 8) |
					(header[3] & 0xff);
			position += 4;
			if (type == 4) {
				if (length > MAX_COMMENT_BYTES) return List.of();
				byte[] comments = new byte[length];
				input.readFully(position, comments);
				return parseVorbisComments(comments, 0);
			}
			position += length;
			if (last) break;
		}
		return List.of();
	}

	private static List<ChapterMark> parseVorbisComments(byte[] data, int offset) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
			buffer.position(offset);
			int vendorLength = boundedLength(buffer.getInt(), buffer.remaining());
			buffer.position(buffer.position() + vendorLength);
			int count = buffer.getInt();
			if ((count < 0) || (count > MAX_COMMENTS)) return List.of();
			TreeMap<Integer, Long> starts = new TreeMap<>();
			TreeMap<Integer, String> names = new TreeMap<>();
			for (int index = 0; index < count; index++) {
				int length = boundedLength(buffer.getInt(), buffer.remaining());
				byte[] bytes = new byte[length];
				buffer.get(bytes);
				visitComment(new String(bytes, StandardCharsets.UTF_8), starts, names);
			}
			List<ChapterMark> result = new ArrayList<>(starts.size());
			for (Map.Entry<Integer, Long> entry : starts.entrySet()) {
				result.add(new ChapterMark(entry.getValue(),
						names.getOrDefault(entry.getKey(), "")));
			}
			return result;
		} catch (RuntimeException ignore) {
			return List.of();
		}
	}

	private static void visitComment(String comment, Map<Integer, Long> starts,
			Map<Integer, String> names) {
		int equals = comment.indexOf('=');
		if (equals <= 0) return;
		String key = comment.substring(0, equals).trim().toUpperCase(Locale.ROOT);
		if (!key.startsWith("CHAPTER")) return;
		String suffix = key.substring("CHAPTER".length());
		boolean name = suffix.endsWith("NAME");
		if (name) suffix = suffix.substring(0, suffix.length() - "NAME".length());
		try {
			int chapter = Integer.parseInt(suffix);
			String value = comment.substring(equals + 1).trim();
			if (name) names.put(chapter, value);
			else {
				long start = EmbeddedChapterParser.parseVorbisTime(value);
				if (start >= 0) starts.put(chapter, start);
			}
		} catch (NumberFormatException ignore) {
		}
	}

	private static int boundedLength(int length, int remaining) {
		if ((length < 0) || (length > remaining)) throw new IllegalArgumentException();
		return length;
	}

	private static boolean startsWith(byte[] value, byte[] prefix, int offset) {
		if (value.length - offset < prefix.length) return false;
		for (int index = 0; index < prefix.length; index++) {
			if (value[offset + index] != prefix[index]) return false;
		}
		return true;
	}

	private static long synchsafe(byte[] value, int offset) {
		return ((long) (value[offset] & 0x7f) << 21) |
				((long) (value[offset + 1] & 0x7f) << 14) |
				((long) (value[offset + 2] & 0x7f) << 7) |
				(value[offset + 3] & 0x7f);
	}

	private static long unsignedInt(byte[] value, int offset) {
		return ((long) (value[offset] & 0xff) << 24) |
				((long) (value[offset + 1] & 0xff) << 16) |
				((long) (value[offset + 2] & 0xff) << 8) |
				(value[offset + 3] & 0xff);
	}
}
