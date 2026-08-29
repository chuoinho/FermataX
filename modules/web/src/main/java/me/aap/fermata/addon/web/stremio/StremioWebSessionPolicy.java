package me.aap.fermata.addon.web.stremio;

import java.net.URI;

/** Keeps the hosted account session while preventing stale Player routes from becoming entry state. */
final class StremioWebSessionPolicy {
	static final String HOME_URL = "https://web.stremio.com/#/";

	private StremioWebSessionPolicy() {
	}

	static String entryUrl(boolean homeRequired, String persistedUrl) {
		return homeRequired || !isPersistableRoute(persistedUrl) ? HOME_URL : persistedUrl;
	}

	static boolean isPersistableRoute(String url) {
		if (url == null) return false;
		try {
			URI uri = URI.create(url);
			if (!"https".equalsIgnoreCase(uri.getScheme()) ||
					!"web.stremio.com".equalsIgnoreCase(uri.getHost())) return false;
			String fragment = uri.getFragment();
			return (fragment == null) || !fragment.startsWith("/player/");
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	static boolean isHomeUrl(String url) {
		return HOME_URL.equals(url);
	}
}
