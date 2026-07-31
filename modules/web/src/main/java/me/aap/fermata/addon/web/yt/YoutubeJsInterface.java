package me.aap.fermata.addon.web.yt;

import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.utils.async.Completable;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeJsInterface extends FermataJsInterface {
	public static final int JS_VIDEO_FOUND = JS_LAST + 1;
	public static final int JS_VIDEO_PLAYING = JS_LAST + 2;
	public static final int JS_VIDEO_PAUSED = JS_LAST + 3;
	public static final int JS_VIDEO_ENDED = JS_LAST + 4;
	public static final int JS_VIDEO_QUALITIES = JS_LAST + 5;
	public static final int JS_VIDEO_READY = JS_LAST + 6;
	public static final int JS_VIDEO_TOUCHED = JS_LAST + 7;
	public static final int JS_AD_SIGNAL = JS_LAST + 8;
	public static final int JS_VIDEO_FULLSCREEN_TAP = JS_LAST + 9;
	public static final int JS_PLAYBACK_INTENT = JS_LAST + 10;
	private final YoutubeMediaEngine engine;
	private Promise<String> result;

	public YoutubeJsInterface(FermataWebView webView, YoutubeMediaEngine engine) {
		super(webView);
		this.engine = engine;
	}

	Promise<String> getResultPromise() {
		if (result != null) result.cancel();
		return result = new Promise<>();
	}

	void onUserExitFullScreen() {
		engine.onUserExitFullScreen();
	}

	YoutubeFullscreenCoordinator.Suspension suspendFullscreenForHostInterruption() {
		return engine.suspendFullscreenForHostInterruption();
	}

	boolean resumeFullscreenAfterHostInterruption(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		return engine.resumeFullscreenAfterHostInterruption(suspension);
	}

	void discardFullscreenHostInterruption(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		engine.discardFullscreenHostInterruption(suspension);
	}

	boolean isFullscreenHostInterruptionCurrent(
			YoutubeFullscreenCoordinator.Suspension suspension) {
		return engine.isFullscreenHostInterruptionCurrent(suspension);
	}

	boolean onPlayerBack(boolean appVideoMode, boolean browserFullScreen) {
		return engine.onPlayerBack(appVideoMode, browserFullScreen);
	}

	boolean acceptsBrowserFullScreen(long request) {
		return engine.acceptsBrowserFullScreen(request);
	}

	void onBrowserFullScreenChanged(boolean fullScreen) {
		engine.onBrowserFullScreenChanged(fullScreen);
	}

	long grantManualFullScreenEntry() {
		return engine.grantManualFullScreenEntry();
	}

	void expireManualFullScreenEntry(long permit) {
		engine.expireManualFullScreenEntry(permit);
	}

	boolean enterManualAppFullScreen() {
		return engine.enterManualAppFullScreen();
	}

	void onPlaybackGesture(long eventTime) {
		engine.onPlaybackGesture(eventTime);
	}

	void armExplicitPlayback() {
		engine.armExplicitPlayback();
	}

	YoutubeMediaEngine getEngine() {
		return engine;
	}

	protected void handleEvent(int event, String data) {
		switch (event) {
			case JS_VIDEO_FOUND:
				Log.d("Video found");
				break;
			case JS_VIDEO_PLAYING:
				Log.d("Video playing");
				engine.playing(data);
				break;
			case JS_VIDEO_READY:
				Log.d("Video ready");
				engine.ready(data);
				break;
			case JS_VIDEO_TOUCHED:
				engine.touched();
				break;
			case JS_VIDEO_FULLSCREEN_TAP:
				engine.fullscreenTapped(data);
				break;
			case JS_PLAYBACK_INTENT:
				engine.armExplicitPlayback();
				break;
			case JS_VIDEO_PAUSED:
				Log.d("Video paused");
				engine.paused(data);
				break;
			case JS_VIDEO_ENDED:
				Log.d("Video ended");
				engine.ended(data);
				break;
			case JS_AD_SIGNAL:
				engine.adSignal(data);
				break;
			case JS_VIDEO_QUALITIES:
				Log.d("Video qualities: ", data);
				setResult(data);
				break;
			default:
				super.handleEvent(event, data);
		}
	}

	private void setResult(String data) {
		Completable<String> r = result;
		result = null;
		if (r != null) r.complete(data);
		else Log.e("Unknown result recipient: ", data);
	}
}
