package me.aap.fermata.addon.web.yt;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.fermata.addon.web.yt.YoutubeFullscreenGate.NO_REQUEST;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.FermataWebClient.DiagnosticsSnapshot;
import me.aap.fermata.addon.web.FermataWebClient.FullscreenEvent;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeChromeClient extends FermataChromeClient {
	private final BrowserEntryGate browserEntryGate;
	private long automaticEntryRequest = NO_REQUEST;

	public YoutubeChromeClient(FermataWebView web, VideoView videoView) {
		this(web, videoView, request -> ((YoutubeWebView) web).acceptsBrowserFullScreen(request));
	}

	YoutubeChromeClient(FermataWebView web, VideoView videoView, BrowserEntryGate browserEntryGate) {
		super(web, videoView);
		this.browserEntryGate = browserEntryGate;
	}

	@Override
	public VideoView getFullScreenView() {
		return (VideoView) super.getFullScreenView();
	}

	@Override
	public YoutubeChromeClient createReplacement(FermataWebView web) {
		return new YoutubeChromeClient(web, getFullScreenView());
	}

	protected void addCustomView(View view) {
		VideoView vv = getFullScreenView();
		((ViewGroup) vv.getChildAt(0)).addView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		vv.setVisibility(VISIBLE);
	}

	protected void removeCustomView(View view) {
		VideoView vv = getFullScreenView();
		((ViewGroup) vv.getChildAt(0)).removeView(view);
		vv.setVisibility(GONE);
	}

	protected void setFullScreen(MainActivityDelegate a, boolean fullScreen) {
		if (((YoutubeWebView) getWebView()).usesAutomotiveHost()) {
			((YoutubeWebView) getWebView()).onBrowserFullScreenChanged(fullScreen);
		} else {
			a.setVideoMode(fullScreen, getFullScreenView());
		}
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
		long request = automaticEntryRequest;
		automaticEntryRequest = NO_REQUEST;
		if (!browserEntryGate.accepts(request)) {
			diagnosticsObserver.onFullscreen(FullscreenEvent.CUSTOM_VIEW_REJECTED,
					DiagnosticsSnapshot.builder().request(request).result(true, false)
							.web(getWebView() != null, (getWebView() != null) &&
									getWebView().isAttachedToWindow(),
									(getWebView() == null) ? 0 : getWebView().getWidth(),
									(getWebView() == null) ? 0 : getWebView().getHeight())
							.build());
			cancelPendingFullScreenEntry();
			callback.onCustomViewHidden();
			FermataWebView web = getWebView();
			if (web instanceof YoutubeWebView youtube) youtube.onBrowserFullScreenRejected();
			return;
		}
		diagnosticsObserver.onFullscreen(FullscreenEvent.BROWSER_CALLBACK_RECEIVED,
				DiagnosticsSnapshot.builder().request(request).result(true, true)
						.web(getWebView() != null, (getWebView() != null) &&
								getWebView().isAttachedToWindow(),
								(getWebView() == null) ? 0 : getWebView().getWidth(),
								(getWebView() == null) ? 0 : getWebView().getHeight())
							.build());
		view.setOnTouchListener(this::onTouchEvent);
		getWebView().setVisibility(GONE);
		super.onShowCustomView(view, callback);
	}

	FutureSupplier<Void> enterAutomaticFullScreen(long request) {
		automaticEntryRequest = request;
		diagnosticsObserver.onFullscreen(FullscreenEvent.BROWSER_REQUEST_DISPATCHED,
				DiagnosticsSnapshot.builder().request(request).result(true, true).build());
		return enterFullScreen();
	}

	@Override
	public boolean cancelPendingFullScreenEntry() {
		automaticEntryRequest = NO_REQUEST;
		return super.cancelPendingFullScreenEntry();
	}

	boolean expirePendingFullScreenWait() {
		return super.cancelPendingFullScreenEntry();
	}

	@Override
	public boolean canEnterFullScreen() {
		MainActivityDelegate a = getLiveActivity(getWebView().getContext());
		if (a == null) return false;
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (!((cb.getEngine() instanceof YoutubeMediaEngine))) return false;
		int st = cb.getPlaybackState().getState();
		return (st == STATE_PLAYING) || (st == STATE_PAUSED);
	}

	protected boolean onTouchEvent(View v, MotionEvent event) {
		if (!isFullScreen()) return false;
		MainActivityDelegate a = getLiveActivity(v.getContext());
		if (a == null) return false;
		a.getControlPanel().onVideoViewTouch(getFullScreenView(), event);
		return true;
	}

	@FunctionalInterface
	interface BrowserEntryGate {
		boolean accepts(long request);
	}
}
