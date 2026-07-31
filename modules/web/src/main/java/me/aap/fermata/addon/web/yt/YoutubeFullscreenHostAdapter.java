package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.ui.activity.MainActivityDelegate;

final class YoutubeFullscreenHostAdapter implements YoutubeFullscreenCoordinator.Host {
	private final YoutubeMediaEngine engine;
	private final YoutubeWebView web;

	YoutubeFullscreenHostAdapter(YoutubeMediaEngine engine, YoutubeWebView web) {
		this.engine = engine;
		this.web = web;
	}

	@Nullable
	private MainActivityDelegate getLiveActivity() {
		if (!web.isAttachedToWindow()) return null;
		try {
			MainActivityDelegate activity = MainActivityDelegate.get(web.getContext());
			if (activity.getAppActivity().isFinishing() ||
					activity.getAppActivity().isDestroyed()) return null;
			return activity;
		} catch (RuntimeException ignored) {
			// AA removes its context-to-activity resolver before the media service closes engines.
			return null;
		}
	}

	private boolean canEnterFullscreen(MainActivityDelegate activity) {
		return activity.isHostResumed() && engine.isCurrentEngine() &&
				engine.isYoutubeActive(activity);
	}

	@Override
	public boolean canEnterFullscreen() {
		MainActivityDelegate activity = getLiveActivity();
		return (activity != null) && canEnterFullscreen(activity);
	}

	@Override
	public boolean requestBrowserFullscreen(long request) {
		if (getLiveActivity() == null) return false;
		FermataChromeClient chrome = web.getWebChromeClient();
		if (!(chrome instanceof YoutubeChromeClient youtubeChrome) ||
				youtubeChrome.isFullScreen()) return false;
		youtubeChrome.enterAutomaticFullScreen(request);
		return true;
	}

	@Override
	public void cancelPendingBrowserFullscreen() {
		FermataChromeClient chrome = web.getWebChromeClient();
		if (chrome != null) chrome.cancelPendingFullScreenEntry();
	}

	@Override
	public void expirePendingBrowserFullscreenWait() {
		FermataChromeClient chrome = web.getWebChromeClient();
		if (chrome instanceof YoutubeChromeClient youtubeChrome)
			youtubeChrome.expirePendingFullScreenWait();
	}

	@Override
	public boolean isBrowserFullscreen() {
		if (getLiveActivity() == null) return false;
		FermataChromeClient chrome = web.getWebChromeClient();
		return (chrome != null) && chrome.isFullScreen();
	}

	@Override
	public boolean isAppVideoMode() {
		MainActivityDelegate activity = getLiveActivity();
		return (activity != null) && engine.isCurrentEngine() && activity.isVideoMode();
	}

	@Override
	public void exitBrowserFullscreen() {
		if (getLiveActivity() == null) return;
		FermataChromeClient chrome = web.getWebChromeClient();
		if ((chrome != null) && chrome.isFullScreen()) chrome.exitFullScreen();
	}

	@Override
	public boolean ownsPlaybackPresentation() {
		return (getLiveActivity() != null) && engine.isCurrentEngine();
	}

	@Override
	public void enterBrowserVideoMode() {
		MainActivityDelegate activity = getLiveActivity();
		if ((activity == null) || !canEnterFullscreen(activity)) return;
		web.setImmersiveVideoMode(false);
		FermataChromeClient chrome = web.getWebChromeClient();
		if (chrome instanceof YoutubeChromeClient youtubeChrome &&
				youtubeChrome.isFullScreen()) {
			activity.setVideoMode(true, youtubeChrome.getFullScreenView());
		}
	}

	@Override
	public void enterFallbackVideoMode() {
		MainActivityDelegate activity = getLiveActivity();
		if ((activity == null) || !canEnterFullscreen(activity)) return;
		web.setImmersiveVideoMode(true);
		activity.setVideoMode(true, null);
	}

	@Override
	public void leaveAppVideoMode() {
		MainActivityDelegate activity = getLiveActivity();
		if (activity == null) return;
		web.setImmersiveVideoMode(false);
		if (engine.isCurrentEngine()) activity.setVideoMode(false, null);
	}

	@Override
	public void post(Runnable task) {
		web.post(task);
	}

	@Override
	public void postDelayed(Runnable task, long delayMillis) {
		web.postDelayed(task, delayMillis);
	}
}
