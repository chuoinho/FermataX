package me.aap.fermata.addon.web.tiktok;

import androidx.annotation.Nullable;

import me.aap.fermata.auto.AutomotiveNavigationController;
import me.aap.fermata.auto.OpenOnCarKind;
import me.aap.fermata.auto.OpenOnCarRequest;
import me.aap.fermata.auto.OpenOnCarToken;

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

	public AutomotiveNavigationController.OpenResult handOff(String url) {
		if (!installed || !acceptsRoute(url)) {
			return AutomotiveNavigationController.OpenResult.FAILED;
		}
		String targetUrl = normalizeRouteForCar(url);
		AutomotiveNavigationController nav = AutomotiveNavigationController.get();
		OpenOnCarToken token = nav.captureSourceToken(1L);
		OpenOnCarRequest request = new OpenOnCarRequest(
				OpenOnCarKind.WEB_URL,
				me.aap.fermata.R.id.tiktok_fragment,
				targetUrl,
				token);
		return nav.open(request, () -> true).peek();
	}
}
