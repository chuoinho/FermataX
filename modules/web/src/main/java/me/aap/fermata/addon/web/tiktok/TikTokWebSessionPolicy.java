package me.aap.fermata.addon.web.tiktok;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Manages URL validation, trust boundaries, search formatting, and navigation
 * hierarchy for the TikTok Web client.
 */
public final class TikTokWebSessionPolicy {
	public static final String HOME_URL = "https://www.tiktok.com/foryou";
	private static final int MAX_ROUTE_BYTES = 64 * 1024;

	private TikTokWebSessionPolicy() {
	}

	public static String entryUrl(boolean homeRequired, String persistedUrl) {
		return homeRequired || !isPersistableRoute(persistedUrl) ? HOME_URL : persistedUrl;
	}

	public static String backTarget(String currentUrl) {
		if (!isHostedRoute(currentUrl)) return HOME_URL;
		return isHomeUrl(currentUrl) ? null : HOME_URL;
	}

	public static boolean isHomeUrl(String url) {
		if (url == null) return false;
		if (HOME_URL.equals(url)) return true;
		if ("https://www.tiktok.com".equals(url) || "https://www.tiktok.com/".equals(url)) return true;
		if ("https://tiktok.com".equals(url) || "https://tiktok.com/".equals(url)) return true;
		try {
			URI uri = URI.create(url);
			if (!isHostedRoute(uri)) return false;
			String path = uri.getPath();
			return path == null || path.isEmpty() || "/".equals(path) || "/foryou".equals(path) || "/foryou/".equals(path);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	public static boolean isHostedRoute(String url) {
		if (!isBoundedRoute(url)) return false;
		try {
			URI uri = URI.create(url);
			return isHostedRoute(uri);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static boolean isHostedRoute(URI uri) {
		String host = uri.getHost();
		if (host == null) return false;
		boolean validHost = "www.tiktok.com".equalsIgnoreCase(host) ||
				"tiktok.com".equalsIgnoreCase(host) ||
				"m.tiktok.com".equalsIgnoreCase(host);
		return "https".equalsIgnoreCase(uri.getScheme()) &&
				validHost &&
				(uri.getRawUserInfo() == null) &&
				((uri.getPort() == -1) || (uri.getPort() == 443));
	}

	public static boolean isPersistableRoute(String url) {
		if (!isHostedRoute(url)) return false;
		try {
			URI uri = URI.create(url);
			String path = uri.getPath();
			if (path == null || path.isEmpty() || "/".equals(path)) return true;
			// Exclude non-persistable auth/transient routes
			if (path.startsWith("/login") || path.startsWith("/signup") ||
					path.startsWith("/logout")) {
				return false;
			}
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	public static String searchUrl(String query) {
		if (query == null || query.trim().isEmpty()) return HOME_URL;
		try {
			String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
			return "https://www.tiktok.com/search?q=" + encoded;
		} catch (Exception e) {
			return "https://www.tiktok.com/search?q=" + query.trim();
		}
	}

	public static String formatDesktopUserAgent(String defaultUa) {
		if (defaultUa == null) return "";
		return defaultUa.replace("; wv", "")
				.replace("Version/4.0 ", "")
				.replaceFirst("\\(Linux; Android[^)]*\\)", "(Windows NT 10.0; Win64; x64)")
				.replace(" Mobile Safari", " Safari");
	}

	private static boolean isBoundedRoute(String url) {
		return (url != null) && (url.length() <= MAX_ROUTE_BYTES) &&
				(url.getBytes(StandardCharsets.UTF_8).length <= MAX_ROUTE_BYTES);
	}
}
