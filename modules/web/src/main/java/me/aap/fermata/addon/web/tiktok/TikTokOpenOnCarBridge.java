package me.aap.fermata.addon.web.tiktok;

import androidx.annotation.Nullable;


/**
 * Manages phone-to-car handoff of TikTok URLs.
 */
public final class TikTokOpenOnCarBridge {
	@Nullable
	private final TikTokWebView web;
	private boolean installed;

	public TikTokOpenOnCarBridge(@Nullable TikTokWebView web) {
		this.web = web;
	}

	public void install() {
		installed = true;
	}

	public void close() {
		installed = false;
	}

	public void onDocumentNavigation(String url) {
		// Document updated
	}

	public static boolean acceptsRoute(String url) {
		return TikTokWebSessionPolicy.isPersistableRoute(url);
	}

	public static String normalizeRouteForCar(String url) {
		if (url == null) return TikTokWebSessionPolicy.HOME_URL;
		if (url.startsWith("https://m.tiktok.com/")) {
			return "https://www.tiktok.com/" + url.substring("https://m.tiktok.com/".length());
		}
		return url;
	}

}
