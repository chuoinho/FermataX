package me.aap.fermata.addon.web.yt;

import org.json.JSONObject;

import java.net.URI;

/** Owns the WebView-side generation captured by full-page and SPA callbacks. */
final class YoutubeNavigationCoordinator {
	private YoutubeAddon addon;
	private long generation;
	private long pageGeneration;

	void open(YoutubeAddon addon) {
		this.addon = addon;
		generation = addon.beginNavigationRuntime();
		pageGeneration = 0L;
	}

	boolean begin(boolean explicitTarget) {
		long next = addon.beginNavigation(explicitTarget);
		if (next <= 0L) return false;
		generation = next;
		pageGeneration = 0L;
		return true;
	}

	long current() {
		return generation;
	}

	void pageStarted() {
		pageGeneration = generation;
	}

	boolean acceptsPage(String callbackUrl, String currentUrl) {
		return accepts(pageGeneration) && (callbackUrl != null) && callbackUrl.equals(currentUrl);
	}

	Navigation acceptSpa(String data, String currentUrl) {
		try {
			JSONObject value = new JSONObject(data);
			long candidate = value.optLong("generation", 0L);
			String url = value.optString("url", "");
			if (!accepts(candidate) || !url.equals(currentUrl)) return null;
			String host = new URI(url).getHost();
			if ((host == null) || !(host.equals("youtube.com") || host.endsWith(".youtube.com")))
				return null;
			return new Navigation(candidate, url);
		} catch (Exception ignored) {
			return null;
		}
	}

	private boolean accepts(long candidate) {
		return (candidate > 0L) && (candidate == generation) &&
				addon.isNavigationGenerationCurrent(candidate);
	}

	record Navigation(long generation, String url) {
	}
}
