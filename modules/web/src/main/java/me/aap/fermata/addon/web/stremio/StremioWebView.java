package me.aap.fermata.addon.web.stremio;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.WebBrowserAddon;

/** Stremio-only WebView host for the document-start compatibility layer. */
public final class StremioWebView extends FermataWebView {
	private StremioWebMediaSessionBridge mediaSessionBridge;

	public StremioWebView(Context context) {
		super(context);
	}

	public StremioWebView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public StremioWebView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
			FermataChromeClient chromeClient) {
		super.init(addon, webClient, chromeClient);
		mediaSessionBridge = new StremioWebMediaSessionBridge(this);
		mediaSessionBridge.install();
	}

	@Override
	public void loadUrl(String url) {
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if ((bridge != null) && ((url == null) || !url.regionMatches(true, 0,
				"javascript:", 0, 11))) bridge.onDocumentNavigation(url);
		super.loadUrl(url);
	}

	@Override
	protected boolean shouldPersistLoadedPage(String url) {
		return super.shouldPersistLoadedPage(url) && StremioWebSessionPolicy.isPersistableRoute(url);
	}

	@Override
	protected void pageLoaded(String url) {
		super.pageLoaded(url);
		WebBrowserAddon addon = getAddon();
		if (addon instanceof StremioWebAddon stremio) stremio.onPageCommitted(url);
	}

	void endAutomotiveSession() {
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if (bridge != null) bridge.endAutomotiveSession();
		stopLoading();
		clearHistory();
		super.loadUrl("about:blank");
	}

	void resetToHomeForNewSession() {
		stopLoading();
		clearHistory();
		loadUrl(StremioWebAddon.HOME_URL);
	}

	@Override
	protected StremioWebView createReplacementView(Context context) {
		return new StremioWebView(context);
	}

	@Override
	public void destroy() {
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		mediaSessionBridge = null;
		if (bridge != null) bridge.close();
		super.destroy();
	}

	StremioWebMediaSessionBridge getMediaSessionBridge() {
		return mediaSessionBridge;
	}
}
