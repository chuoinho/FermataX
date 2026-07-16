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

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
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
		a.setVideoMode(fullScreen, getFullScreenView());
		if (BuildConfig.AUTO) a.setBarsHidden(fullScreen);
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
		long request = automaticEntryRequest;
		automaticEntryRequest = NO_REQUEST;
		if (!browserEntryGate.accepts(request)) {
			cancelPendingFullScreenEntry();
			callback.onCustomViewHidden();
			return;
		}
		view.setOnTouchListener(this::onTouchEvent);
		getWebView().setVisibility(GONE);
		super.onShowCustomView(view, callback);
	}

	FutureSupplier<Void> enterAutomaticFullScreen(long request) {
		automaticEntryRequest = request;
		return enterFullScreen();
	}

	@Override
	public boolean cancelPendingFullScreenEntry() {
		automaticEntryRequest = NO_REQUEST;
		return super.cancelPendingFullScreenEntry();
	}

	@Override
	public boolean canEnterFullScreen() {
		MainActivityDelegate a = MainActivityDelegate.get(getWebView().getContext());
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (!((cb.getEngine() instanceof YoutubeMediaEngine))) return false;
		int st = cb.getPlaybackState().getState();
		return (st == STATE_PLAYING) || (st == STATE_PAUSED);
	}

	protected boolean onTouchEvent(View v, MotionEvent event) {
		if (!isFullScreen()) return false;
		MainActivityDelegate.get(v.getContext()).getControlPanel().onVideoViewTouch(getFullScreenView(), event);
		return true;
	}

	@FunctionalInterface
	interface BrowserEntryGate {
		boolean accepts(long request);
	}
}
