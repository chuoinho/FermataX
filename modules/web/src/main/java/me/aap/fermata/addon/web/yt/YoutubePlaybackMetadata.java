package me.aap.fermata.addon.web.yt;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Maintains a stable title for the current YouTube playback identity. */
final class YoutubePlaybackMetadata {
	private static final String SIGNAL_PREFIX = "ytv1|";
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
		if ((data != null) && data.startsWith(SIGNAL_PREFIX)) {
			String[] fields = data.split("\\|", -1);
			if (fields.length >= 4) {
				String page = decode(fields[1]);
				String media = decode(fields[2]);
				String title = decode(fields[3]);
				return new Signal(page.isEmpty() ? fallback : page, media, title);
			}
		}

		return new Signal(fallback, clean(data), "");
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
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			return clean(value);
		}
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	record Signal(String pageUrl, String mediaUrl, String title) {
	}
}
