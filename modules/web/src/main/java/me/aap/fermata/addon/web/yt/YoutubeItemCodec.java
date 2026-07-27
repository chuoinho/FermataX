package me.aap.fermata.addon.web.yt;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Versioned persistence format for immutable YouTube items. */
final class YoutubeItemCodec {
	private static final String HEADER_V1 = "yt-items-v1";
	private static final String HEADER_V2 = "yt-items-v2";

	private YoutubeItemCodec() {
	}

	static String encode(List<YoutubeItem> items) {
		StringBuilder data = new StringBuilder(HEADER_V2);
		for (YoutubeItem item : items) {
			if (item == null) throw new NullPointerException("YouTube item");
			data.append('\n').append(field(item.videoId()))
					.append('|').append(field(item.pageUrl()))
					.append('|').append(field(item.title()))
					.append('|').append(field(item.thumbnailUrl()))
					.append('|').append(item.durationMillis())
					.append('|').append(item.lastPlayedAtMillis());
		}
		return data.toString();
	}

	static List<YoutubeItem> decode(String data) {
		return decodeResult(data).items();
	}

	static DecodeResult decodeResult(String data) {
		if ((data == null) || data.isBlank())
			return new DecodeResult(Status.SUPPORTED, List.of());
		String[] lines = data.split("\\R", -1);
		if (lines.length == 0) return new DecodeResult(Status.SUPPORTED, List.of());
		boolean v1 = HEADER_V1.equals(lines[0]);
		if (!v1 && !HEADER_V2.equals(lines[0]))
			return new DecodeResult(Status.UNSUPPORTED, List.of());

		List<YoutubeItem> items = new ArrayList<>(Math.max(0, lines.length - 1));
		for (int i = 1; i < lines.length; i++) {
			String[] fields = lines[i].split("\\|", -1);
			if (fields.length != (v1 ? 4 : 6)) continue;
			try {
				if (v1) {
					items.add(new YoutubeItem(value(fields[0]), value(fields[1]), value(fields[2]),
							Long.parseLong(fields[3])));
				} else {
					items.add(new YoutubeItem(value(fields[0]), value(fields[1]), value(fields[2]),
							value(fields[3]), Long.parseLong(fields[4]), Long.parseLong(fields[5])));
				}
			} catch (IllegalArgumentException ignored) {
				// One damaged entry must not make the rest of the history unreadable.
			}
		}
		return new DecodeResult(Status.SUPPORTED, List.copyOf(items));
	}

	enum Status {
		SUPPORTED,
		UNSUPPORTED
	}

	record DecodeResult(Status status, List<YoutubeItem> items) {
		DecodeResult {
			items = List.copyOf(items);
		}

		boolean isUnsupported() {
			return status == Status.UNSUPPORTED;
		}
	}

	private static String field(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String value(String field) {
		return URLDecoder.decode(field, StandardCharsets.UTF_8);
	}
}
