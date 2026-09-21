package me.aap.fermata.addon.web.yt;

import java.util.function.BooleanSupplier;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Keeps a phone WebView from taking playback ownership while direct AA is visible. */
final class YoutubePlaybackHostPolicy {
	private YoutubePlaybackHostPolicy() {
	}

	static boolean prefersHost(boolean carHost, boolean carTarget) {
		return carHost == carTarget;
	}

	static boolean forwardSelection(boolean carTarget, boolean currentEngine,
			boolean sameVideo, boolean explicitIntent) {
		return carTarget && explicitIntent && !(currentEngine && sameVideo);
	}

	static boolean attachHost(boolean carTarget, boolean carHost, boolean admissionCurrent) {
		return admissionCurrent && prefersHost(carHost, carTarget);
	}

	static boolean isPreferredHost(YoutubeWebView web) {
		try {
			MainActivityDelegate activity = MainActivityDelegate.get(web.getContext());
			return activity.isCarActivityNotMirror() || web.getMediaEngine().isLocalUserSelection();
		} catch (RuntimeException ignored) {
			return true;
		}
	}

	static boolean shouldForward(boolean currentEngine, boolean activePlaybackHost,
			boolean localHostPreferred, boolean explicitPlaybackIntent) {
		return !currentEngine && !activePlaybackHost && !localHostPreferred && explicitPlaybackIntent;
	}
}
