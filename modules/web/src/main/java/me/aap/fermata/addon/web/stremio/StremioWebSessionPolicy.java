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

	static String replaceLegacyPlayerRoute(String persistedUrl, String previousDetailUrl) {
		if (isPersistableRoute(persistedUrl)) return persistedUrl;
		return isDetailRoute(previousDetailUrl) ? previousDetailUrl : HOME_URL;
	}

	/**
	 * Stremio is an SPA, so WebView history can retain stale document entries after Player teardown.
	 * Keep the same hierarchy as Fermata's native add-ons instead of replaying that history.
	 */
	static String backTarget(String currentUrl, String previousDetailUrl, boolean playerActive) {
		if (!isHostedRoute(currentUrl)) return null;
		if (playerActive || isPlayerRoute(currentUrl)) {
			return isDetailRoute(previousDetailUrl) ? previousDetailUrl : HOME_URL;
		}
		return isHomeUrl(currentUrl) ? null : HOME_URL;
	}

	static boolean isPersistableRoute(String url) {
		return isHostedRoute(url) && !isPlayerRoute(url);
	}

	static boolean isHostedRoute(String url) {
		if (url == null) return false;
		try {
			URI uri = URI.create(url);
			return "https".equalsIgnoreCase(uri.getScheme()) &&
					"web.stremio.com".equalsIgnoreCase(uri.getHost());
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	static boolean isPlayerRoute(String url) {
		if (url == null) return false;
		try {
			return isPlayerRoute(URI.create(url));
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static boolean isPlayerRoute(URI uri) {
		return "https".equalsIgnoreCase(uri.getScheme()) &&
				"web.stremio.com".equalsIgnoreCase(uri.getHost()) &&
				(uri.getFragment() != null) && uri.getFragment().startsWith("/player/");
	}

	static boolean isHomeUrl(String url) {
		return HOME_URL.equals(url);
	}

	static boolean isDetailRoute(String url) {
		if (!isPersistableRoute(url)) return false;
		try {
			String fragment = URI.create(url).getFragment();
			return (fragment != null) && fragment.startsWith("/detail/");
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}
}
