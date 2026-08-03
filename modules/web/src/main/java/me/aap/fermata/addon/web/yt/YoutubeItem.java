package me.aap.fermata.addon.web.yt;

import me.aap.utils.net.NetUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Immutable, persistable identity and display data for one YouTube video. */
record YoutubeItem(String videoId, String pageUrl, String title, String thumbnailUrl,
		long durationMillis, long lastPlayedAtMillis) {
	private static final String MOBILE_ORIGIN = "https://m.youtube.com";
	private static final String THUMBNAIL_ORIGIN = "https://i.ytimg.com/vi/";

	YoutubeItem(String videoId, String pageUrl, String title, long lastPlayedAtMillis) {
		this(videoId, pageUrl, title, "", 0L, lastPlayedAtMillis);
	}

	YoutubeItem {
		videoId = clean(videoId);
		if (!isVideoId(videoId)) throw new IllegalArgumentException("Invalid YouTube video ID");

		PageIdentity page = parsePageUrl(pageUrl);
		if ((page == null) || !videoId.equals(page.videoId())) {
			throw new IllegalArgumentException("Page URL does not match YouTube video ID");
		}

		pageUrl = page.canonicalUrl();
		title = YoutubePlaybackMetadata.normalizeTitle(title);
		thumbnailUrl = clean(thumbnailUrl);
		if (thumbnailUrl.isEmpty()) thumbnailUrl = THUMBNAIL_ORIGIN + videoId + "/hqdefault.jpg";
		if (durationMillis < 0L) throw new IllegalArgumentException("Duration cannot be negative");
		if (lastPlayedAtMillis < 0L) {
			throw new IllegalArgumentException("Last-played time cannot be negative");
		}
	}

	static YoutubeItem fromPageUrl(String pageUrl, String title, long lastPlayedAtMillis) {
		PageIdentity page = parsePageUrl(pageUrl);
		if (page == null) throw new IllegalArgumentException("Not a YouTube video URL");
		return new YoutubeItem(page.videoId(), page.canonicalUrl(), title, "", 0L,
				lastPlayedAtMillis);
	}

	String stableId() {
		return "youtube:video:" + videoId;
	}

	YoutubeItem withTitle(String title) {
		return new YoutubeItem(videoId, pageUrl, title, thumbnailUrl, durationMillis,
				lastPlayedAtMillis);
	}

	YoutubeItem withPlaybackMetadata(String title, String thumbnailUrl, long durationMillis) {
		return new YoutubeItem(videoId, pageUrl, title, thumbnailUrl, durationMillis,
				lastPlayedAtMillis);
	}

	YoutubeItem playedAt(long lastPlayedAtMillis) {
		return new YoutubeItem(videoId, pageUrl, title, thumbnailUrl, durationMillis,
				lastPlayedAtMillis);
	}

	private static PageIdentity parsePageUrl(String value) {
		String url = clean(value);
		if (url.isEmpty()) return null;

		try {
			URI uri = new URI(url);
			String scheme = lower(uri.getScheme());
			String host = lower(uri.getHost());
			if (!("http".equals(scheme) || "https".equals(scheme)) || (host == null)) {
				return null;
			}

			String path = uri.getPath();
			if (path == null) return null;
			String videoId;
			boolean shorts = false;

			if ("youtu.be".equals(host)) {
				videoId = pathSegment(path, 0);
			} else if (("youtube.com".equals(host) || host.endsWith(".youtube.com")) &&
					!"tv.youtube.com".equals(host) && !host.endsWith(".tv.youtube.com")) {
				if ("/watch".equals(path)) {
					videoId = queryValue(uri.getRawQuery(), "v");
				} else if (path.startsWith("/shorts/")) {
					videoId = pathSegment(path, 1);
					shorts = true;
				} else if (path.startsWith("/embed/")) {
					videoId = pathSegment(path, 1);
				} else {
					return null;
				}
			} else {
				return null;
			}

			videoId = decode(videoId);
			if (!isVideoId(videoId)) return null;
			String canonicalUrl = shorts ? MOBILE_ORIGIN + "/shorts/" + videoId :
					MOBILE_ORIGIN + "/watch?v=" + videoId;
			return new PageIdentity(videoId, canonicalUrl);
		} catch (URISyntaxException | IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String pathSegment(String path, int index) {
		int start = 0;
		for (int i = 0; i <= index; i++) {
			while ((start < path.length()) && (path.charAt(start) == '/')) start++;
			if (i == index) break;
			start = path.indexOf('/', start);
			if (start < 0) return null;
		}
		if (start >= path.length()) return null;
		int end = path.indexOf('/', start);
		return path.substring(start, (end < 0) ? path.length() : end);
	}

	private static String queryValue(String query, String name) {
		if ((query == null) || query.isEmpty()) return null;
		for (String part : query.split("&")) {
			int split = part.indexOf('=');
			String key = decode((split < 0) ? part : part.substring(0, split));
			if (name.equals(key)) return (split < 0) ? "" : part.substring(split + 1);
		}
		return null;
	}

	private static String decode(String value) {
		return (value == null) ? null : NetUtils.urlDecode(value);
	}

	private static boolean isVideoId(String value) {
		if ((value == null) || value.isEmpty()) return false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (!Character.isLetterOrDigit(c) && (c != '-') && (c != '_')) return false;
		}
		return true;
	}

	private static String clean(String value) {
		return (value == null) ? "" : value.trim();
	}

	private static String lower(String value) {
		return (value == null) ? null : value.toLowerCase(Locale.ROOT);
	}

	private record PageIdentity(String videoId, String canonicalUrl) {
	}
}
