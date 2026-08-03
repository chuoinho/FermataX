package me.aap.fermata.addon.web.yt;

import me.aap.utils.net.NetUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Maintains a stable title for the current YouTube playback identity. */
final class YoutubePlaybackMetadata {
	private static final String SIGNAL_PREFIX = "ytv1|";
	private static final String SIGNAL_PREFIX_V2 = "ytv2|";
	private String pageUrl = "";
	private String title = "";

	synchronized boolean apply(Signal signal) {
		boolean changed = false;
		String page = clean(signal.pageUrl());

		if (!page.isEmpty() && !page.equals(pageUrl)) {
			pageUrl = page;
			if (!title.isEmpty()) {
				title = "";
				changed = true;
			}
		}

		String candidate = normalizeTitle(signal.title());
		if (!candidate.isEmpty() && !candidate.equals(title)) {
			title = candidate;
			changed = true;
		}
		return changed;
	}

	synchronized String getTitle() {
		return title;
	}

	synchronized boolean matches(String metadataTitle) {
		return !title.isEmpty() && title.equals(normalizeTitle(metadataTitle));
	}

	static Signal parse(String data, String fallbackPageUrl) {
		String fallback = clean(fallbackPageUrl);
		if ((data != null) && data.startsWith(SIGNAL_PREFIX_V2)) {
			String[] fields = data.split("\\|", -1);
			if ((fields.length == 6) || (fields.length == 8)) {
				String page = decode(fields[1]);
				String media = decode(fields[2]);
				String title = decode(fields[3]);
				return new Signal(page.isEmpty() ? fallback : page, media, title,
						parseGeneration(fields[4]), decode(fields[5]),
						(fields.length == 8) && "1".equals(fields[6]),
						(fields.length == 8) ? parseVolume(fields[7]) : -1d);
			}
		} else if ((data != null) && data.startsWith(SIGNAL_PREFIX)) {
			String[] fields = data.split("\\|", -1);
			if ((fields.length == 4) || (fields.length == 5)) {
				String page = decode(fields[1]);
				String media = decode(fields[2]);
				String title = decode(fields[3]);
				return new Signal(page.isEmpty() ? fallback : page, media, title,
						(fields.length == 5) ? parseGeneration(fields[4]) : 0L, "");
			}
		}

		return new Signal(fallback, clean(data), "");
	}

	static boolean isStructuredSignal(String data) {
		if (data == null) return false;
		String[] fields = data.split("\\|", -1);
		if (data.startsWith(SIGNAL_PREFIX_V2)) {
			if ((fields.length != 6) && (fields.length != 8)) return false;
		} else if (data.startsWith(SIGNAL_PREFIX)) {
			if ((fields.length != 4) && (fields.length != 5)) return false;
		} else {
			return false;
		}
		if (fields.length == 4 || fields[4].isEmpty()) return true;
		return parseGeneration(fields[4]) >= 0L;
	}

	static long playbackGeneration(String data) {
		if (!isStructuredSignal(data)) return 0L;
		String[] fields = data.split("\\|", -1);
		return (fields.length == 4) ? 0L : Math.max(0L, parseGeneration(fields[4]));
	}

	static boolean hasConsistentVideoIdentity(Signal signal) {
		String videoId = clean(signal.videoId());
		if (videoId.isEmpty()) return true;
		try {
			return videoId.equals(YoutubeItem.fromPageUrl(signal.pageUrl(), "", 0L).videoId());
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	static boolean hasConsistentVideoIdentity(String data, Signal signal) {
		if ((data != null) && data.startsWith(SIGNAL_PREFIX_V2) &&
				clean(signal.videoId()).isEmpty()) return false;
		return hasConsistentVideoIdentity(signal);
	}

	static boolean hasTrustedPlaybackIdentity(String data, Signal signal) {
		return (data != null) && data.startsWith(SIGNAL_PREFIX_V2) &&
				isStructuredSignal(data) && !clean(signal.videoId()).isEmpty() &&
				hasConsistentVideoIdentity(signal);
	}

	static String normalizeTitle(String value) {
		String title = clean(value);
		if (title.isEmpty()) return "";
		title = title.replaceFirst("^\\(\\d+\\)\\s*", "").trim();
		String lower = title.toLowerCase(Locale.ROOT);
		for (String suffix : new String[]{" - youtube", " | youtube"}) {
			if (lower.endsWith(suffix)) {
				title = title.substring(0, title.length() - suffix.length()).trim();
				lower = title.toLowerCase(Locale.ROOT);
				break;
			}
		}
		if (lower.equals("youtube") || lower.startsWith("http://") ||
				lower.startsWith("https://") || lower.equals("m.youtube.com") ||
				lower.equals("www.youtube.com")) return "";
		return title;
	}

	private static String decode(String value) {
		try {
			return NetUtils.urlDecode(value);
		} catch (IllegalArgumentException ex) {
			return clean(value);
		}
	}

	private static long parseGeneration(String value) {
		if ((value == null) || value.isEmpty()) return 0L;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException ignored) {
			return -1L;
		}
	}

	private static double parseVolume(String value) {
		try {
			double volume = Double.parseDouble(value);
			return Double.isFinite(volume) ? Math.max(0d, Math.min(1d, volume)) : -1d;
		} catch (NumberFormatException ignored) {
			return -1d;
		}
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	record Signal(String pageUrl, String mediaUrl, String title, long generation, String videoId,
			boolean muted, double volume) {
		Signal(String pageUrl, String mediaUrl, String title, long generation, String videoId) {
			this(pageUrl, mediaUrl, title, generation, videoId, false, -1d);
		}

		Signal(String pageUrl, String mediaUrl, String title) {
			this(pageUrl, mediaUrl, title, 0L, "", false, -1d);
		}

		boolean isAudible() {
			return !muted && (volume > 0d);
		}
	}
}
