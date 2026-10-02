package me.aap.fermata.addon.web.tiktok;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.WebBrowserAddon;

/**
 * Specialized WebView for TikTok Web player.
 * Configures pure black AMOLED background, automotive script injection,
 * native CntBridge JS interface, and MediaSession steering wheel controls.
 */
public final class TikTokWebView extends FermataWebView {
	private TikTokMediaSessionBridge mediaSessionBridge;
	private TikTokJsBridge jsBridge;
	private TikTokOpenOnCarBridge openOnCarBridge;

	public TikTokWebView(Context context) {
		super(context);
	}

	public TikTokWebView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public TikTokWebView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
			FermataChromeClient chromeClient) {
		super.init(addon, webClient, chromeClient);
		setBackgroundColor(0xFF000000);

		jsBridge = new TikTokJsBridge();
		mediaSessionBridge = new TikTokMediaSessionBridge(this);
		jsBridge.setListener(mediaSessionBridge);
		addJavascriptInterface(jsBridge, TikTokJsBridge.INTERFACE_NAME);
		mediaSessionBridge.install();

		openOnCarBridge = new TikTokOpenOnCarBridge(this);
		openOnCarBridge.install();
	}

	@Override
	public void loadUrl(String url) {
		if ((url != null) && !url.regionMatches(true, 0, "javascript:", 0, 11)) {
			if (mediaSessionBridge != null) mediaSessionBridge.onDocumentNavigation(url);
			if (openOnCarBridge != null) openOnCarBridge.onDocumentNavigation(url);
		}
		super.loadUrl(url);
	}

	void onMainFrameStarted(String url) {
		if (mediaSessionBridge != null) {
			mediaSessionBridge.onDocumentNavigation(url);
		}
	}

	@Override
	protected boolean shouldPersistLoadedPage(String url) {
		return super.shouldPersistLoadedPage(url) && TikTokWebSessionPolicy.isPersistableRoute(url);
	}

	@Override
	protected void pageLoaded(String url) {
		super.pageLoaded(url);
		if (TikTokWebSessionPolicy.isHostedRoute(url)) {
			evaluateJavascript(TikTokScripts.injectSource(), null);
		}
		WebBrowserAddon addon = getAddon();
		if (addon instanceof TikTokAddon tikTokAddon) {
			tikTokAddon.onPageCommitted(url);
		}
	}

	@Nullable
	public TikTokMediaSessionBridge getMediaSessionBridge() {
		return mediaSessionBridge;
	}

	@Nullable
	public TikTokJsBridge getJsBridge() {
		return jsBridge;
	}

	@Nullable
	public TikTokOpenOnCarBridge getOpenOnCarBridge() {
		return openOnCarBridge;
	}

	@Override
	public void destroy() {
		if (mediaSessionBridge != null) {
			mediaSessionBridge.close();
			mediaSessionBridge = null;
		}
		if (openOnCarBridge != null) {
			openOnCarBridge.close();
			openOnCarBridge = null;
		}
		super.destroy();
	}
}
