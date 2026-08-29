package me.aap.fermata.addon.web.stremio;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;

/** Keeps Stremio's SPA player alive while the Fermata host presents it fullscreen. */
final class StremioChromeClient extends FermataChromeClient {
	private boolean hostFallbackFullScreen;
	private boolean barsHiddenBeforeFullScreen;

	StremioChromeClient(StremioWebView web, ViewGroup fullScreenView) {
		super(web, fullScreenView);
	}

	@Override
	protected FermataChromeClient createReplacement(FermataWebView web) {
		return new StremioChromeClient((StremioWebView) web, getFullScreenView());
	}

	@Override
	public boolean canEnterFullScreen() {
		return (getWebView() instanceof StremioWebView stremio) &&
				stremio.supportsManualFullscreen();
	}

	@Override
	public boolean isFullScreen() {
		return hostFallbackFullScreen || super.isFullScreen();
	}

	@Override
	public FutureSupplier<Void> enterFullScreen() {
		if (isFullScreen() || !canEnterFullScreen()) return completedVoid();
		// A WebView custom view makes Stremio tear down its SPA player when Back exits
		// fullscreen. The host presentation is enough to make the active video immersive
		// while keeping Stremio's player and MediaSession alive.
		enterHostFallback();
		return completedVoid();
	}

	@Override
	public FutureSupplier<Void> exitFullScreen() {
		if (!hostFallbackFullScreen) {
			return super.exitFullScreen();
		}
		hostFallbackFullScreen = false;
		MainActivityDelegate activity = getLiveActivity(getWebView().getContext());
		if (activity != null) {
			activity.setBarsHiddenNow(barsHiddenBeforeFullScreen);
			activity.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
		}
		return completedVoid();
	}

	@Override
	public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
		if (canEnterFullScreen()) {
			callback.onCustomViewHidden();
			if (!hostFallbackFullScreen) enterHostFallback();
			return;
		}
		super.onShowCustomView(view, callback);
	}

	@Override
	protected void setFullScreen(MainActivityDelegate activity, boolean fullScreen) {
		if (fullScreen) {
			barsHiddenBeforeFullScreen = activity.isBarsHidden();
			activity.setBarsHiddenNow(true);
		} else {
			activity.setBarsHiddenNow(barsHiddenBeforeFullScreen);
		}
	}

	private void enterHostFallback() {
		MainActivityDelegate activity = getLiveActivity(getWebView().getContext());
		if ((activity == null) || !canEnterFullScreen()) return;
		barsHiddenBeforeFullScreen = activity.isBarsHidden();
		hostFallbackFullScreen = true;
		activity.setBarsHiddenNow(true);
	}

}
