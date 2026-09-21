package me.aap.fermata.addon.web.stremio;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import java.util.Objects;

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
	private StremioWebAudioBridge audioBridge;
	private StremioOpenOnCarBridge openOnCarBridge;
	@Nullable
	private String pendingFreshDocumentUrl;
	@Nullable
	private String preparedDocumentUrl;
	@Nullable
	private PlayerTransfer pendingPlayerTransfer;
	@Nullable
	private PlayerTransfer armedPlayerTransfer;
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
		audioBridge = new StremioWebAudioBridge(this);
		audioBridge.install();
		openOnCarBridge = new StremioOpenOnCarBridge(this);
		openOnCarBridge.install();
	}

	@Override
	public void loadUrl(String url) {
		if ((url == null) || !url.regionMatches(true, 0, "javascript:", 0, 11))
			prepareDocumentNavigation(url);
		super.loadUrl(url);
	}

	/** Called by the client for main-frame navigations not initiated through {@link #loadUrl(String)}. */
	void onMainFrameStarted(String url) {
		if (getSourceFragment() instanceof StremioWebFragment fragment &&
				fragment.interceptPlayerRoute(this, url)) {
			stopLoading();
			fragment.restoreAfterPlayerInterception(this);
			return;
		}
		if (getSourceFragment() instanceof StremioWebFragment fragment)
			fragment.onStremioDocumentStarted(this);
		if (!Objects.equals(preparedDocumentUrl, url)) prepareDocumentNavigation(url);
		preparedDocumentUrl = null;
		PlayerTransfer transfer = armedPlayerTransfer;
		if ((transfer == null) || !transfer.route.equals(url)) return;
		armedPlayerTransfer = null;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if ((bridge != null) && transfer.isCurrent(this)) bridge.armTransferredPlayer(transfer.current);
		else if (bridge != null) bridge.cancelTransferredPlayer();
	}

	private void prepareDocumentNavigation(String url) {
		preparedDocumentUrl = url;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if (bridge != null) bridge.onDocumentNavigation(url);
		StremioWebAudioBridge webAudio = audioBridge;
		if (webAudio != null) webAudio.onDocumentNavigation(url);
		StremioOpenOnCarBridge openOnCar = openOnCarBridge;
		if (openOnCar != null) openOnCar.onDocumentNavigation(url);
	}

	@Override
	protected boolean shouldPersistLoadedPage(String url) {
		return super.shouldPersistLoadedPage(url) && StremioWebSessionPolicy.isPersistableRoute(url);
	}

	@Override
	protected void pageLoaded(String url) {
		if ("about:blank".equals(url) && (pendingFreshDocumentUrl != null)) {
			String target = pendingFreshDocumentUrl;
			PlayerTransfer transfer = pendingPlayerTransfer;
			pendingFreshDocumentUrl = null;
			pendingPlayerTransfer = null;
			clearingFreshDocumentHistory = true;
			if ((transfer != null) && !transfer.isCurrent(this)) {
				clearingFreshDocumentHistory = false;
				clearHistory();
				return;
			}
			armedPlayerTransfer = transfer;
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
		if (!StremioWebSessionPolicy.isPersistableRoute(url)) return;
		cancelTransferredPlayer();
		if (pendingFreshDocumentUrl != null) {
			pendingFreshDocumentUrl = url;
			return;
		}
		pendingFreshDocumentUrl = url;
		stopLoading();
		clearHistory();
		loadUrl("about:blank");
	}

	me.aap.fermata.auto.AutomotiveNavigationController.OpenResult openTransferredPlayer(
			me.aap.fermata.auto.OpenOnCarRequest request,
			java.util.function.BooleanSupplier current) {
		if ((request.kind() != me.aap.fermata.auto.OpenOnCarKind.STREMIO_PLAYER) ||
				(request.addonId() != me.aap.fermata.R.id.stremio_fragment) ||
				!(request.payload() instanceof String route) ||
				!StremioOpenOnCarPolicy.acceptsPlayerRoute(route))
			return me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.FAILED;
		if (!current.getAsBoolean())
			return me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.CANCELLED;
		if (!isProjectionDestination() || !isAttachedToWindow())
			return me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.NOT_READY;

		cancelTransferredPlayer();
		pendingFreshDocumentUrl = route;
		pendingPlayerTransfer = new PlayerTransfer(route, current);
		stopLoading();
		clearHistory();
		loadUrl("about:blank");
		return me.aap.fermata.auto.AutomotiveNavigationController.OpenResult.LOAD_DISPATCHED;
	}

	void cancelTransferredPlayer() {
		if (StremioOpenOnCarPolicy.acceptsPlayerRoute(pendingFreshDocumentUrl))
			pendingFreshDocumentUrl = null;
		pendingPlayerTransfer = null;
		armedPlayerTransfer = null;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if (bridge != null) bridge.cancelTransferredPlayer();
	}

	void setOpenOnCarCapture(boolean enabled, @Nullable StremioOpenOnCarBridge.Listener listener) {
		StremioOpenOnCarBridge bridge = openOnCarBridge;
		if (bridge == null) return;
		bridge.setListener(enabled ? listener : null);
		bridge.setCaptureEnabled(enabled);
	}

	boolean interceptPlayerRoute(String route) {
		return (getSourceFragment() instanceof StremioWebFragment fragment) &&
				fragment.interceptPlayerRoute(this, route);
	}

	boolean isPhoneOpenOnCarSource() {
		return getRuntimeHostMode() == me.aap.fermata.ui.policy.RuntimeHostMode.PHONE;
	}

	private boolean isProjectionDestination() {
		return getRuntimeHostMode() == me.aap.fermata.ui.policy.RuntimeHostMode.AA_PROJECTION;
	}

	void endAutomotiveSession() {
		cancelTransferredPlayer();
		pendingFreshDocumentUrl = null;
		preparedDocumentUrl = null;
		clearingFreshDocumentHistory = false;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		if (bridge != null) bridge.endAutomotiveSession();
		StremioWebAudioBridge webAudio = audioBridge;
		if (webAudio != null) webAudio.endAutomotiveSession();
		stopLoading();
		clearHistory();
		loadUrl("about:blank");
	}

	void resetToHomeForNewSession() {
		cancelTransferredPlayer();
		pendingFreshDocumentUrl = null;
		preparedDocumentUrl = null;
		clearingFreshDocumentHistory = false;
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
	protected String getRecoveryUrl() {
		// Renderer recovery is not a new source selection. Never replay a player payload.
		return StremioWebSessionPolicy.entryUrl(false, super.getRecoveryUrl());
	}

	@Override
	public void destroy() {
		cancelTransferredPlayer();
		pendingFreshDocumentUrl = null;
		preparedDocumentUrl = null;
		clearingFreshDocumentHistory = false;
		StremioWebMediaSessionBridge bridge = mediaSessionBridge;
		mediaSessionBridge = null;
		if (bridge != null) bridge.close();
		StremioWebAudioBridge webAudio = audioBridge;
		audioBridge = null;
		if (webAudio != null) webAudio.close();
		StremioOpenOnCarBridge openOnCar = openOnCarBridge;
		openOnCarBridge = null;
		if (openOnCar != null) openOnCar.close();
		super.destroy();
	}

	StremioWebMediaSessionBridge getMediaSessionBridge() {
		return mediaSessionBridge;
	}

	private record PlayerTransfer(String route, java.util.function.BooleanSupplier current) {
		boolean isCurrent(StremioWebView web) {
			return current.getAsBoolean() && web.isAttachedToWindow() && web.isProjectionDestination();
		}
	}
}
