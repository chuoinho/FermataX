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
	private static final String REQUEST_VIDEO_FULLSCREEN = """
			(function(){
				var videos=document.querySelectorAll('video');
				var video=null;
				for(var i=0;i<videos.length;i++){
					var candidate=videos[i];
					if(candidate.isConnected&&!candidate.paused&&!candidate.ended&&candidate.readyState>0){
						video=candidate;
						break;
					}
					if(!video&&candidate.isConnected) video=candidate;
				}
				var request=video&&(video.webkitRequestFullscreen||video.requestFullscreen);
				if(request) request.call(video);
			})();
			""";
	private StremioWebMediaSessionBridge mediaSessionBridge;
	@Nullable
	private String pendingFreshDocumentUrl;
	private boolean clearingFreshDocumentHistory;

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
		if ("about:blank".equals(url) && (pendingFreshDocumentUrl != null)) {
			String target = pendingFreshDocumentUrl;
			pendingFreshDocumentUrl = null;
			clearingFreshDocumentHistory = true;
			loadUrl(target);
			return;
		}
		if (clearingFreshDocumentHistory) {
			// The blank transition is only a recovery boundary. It must not become an
			// apparent browser parent that steals Fermata's normal Back-to-Dashboard action.
			clearingFreshDocumentHistory = false;
			clearHistory();
		}
		super.pageLoaded(url);
		WebBrowserAddon addon = getAddon();
		if (addon instanceof StremioWebAddon stremio) stremio.onPageCommitted(url);
	}

	/**
	 * Player teardown can leave Stremio's SPA at a valid hash with an empty render root. Start a
	 * new hosted document instead of relying on a same-document hash transition to recover it.
	 */
	void loadFreshDocument(String url) {
		if (url == null) return;
		if (pendingFreshDocumentUrl != null) {
			pendingFreshDocumentUrl = url;
			return;
		}
		pendingFreshDocumentUrl = url;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if (bridge != null) bridge.onDocumentNavigation(null);
		stopLoading();
		clearHistory();
		super.loadUrl("about:blank");
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
	protected boolean requestFullScreen() {
		evaluateJavascript(REQUEST_VIDEO_FULLSCREEN, null);
		return true;
	}

	boolean supportsManualFullscreen() {
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		return (bridge != null) && bridge.isPlaybackActive();
	}

	boolean isPlayerActive() {
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		return (bridge != null) && bridge.isPlaybackActive();
	}

	@Override
	protected StremioWebView createReplacementView(Context context) {
		return new StremioWebView(context);
	}

	@Override
	public void destroy() {
		pendingFreshDocumentUrl = null;
		clearingFreshDocumentHistory = false;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		mediaSessionBridge = null;
		if (bridge != null) bridge.close();
		super.destroy();
	}

	StremioWebMediaSessionBridge getMediaSessionBridge() {
		return mediaSessionBridge;
	}
}
