package me.aap.fermata.addon.web.yt;

import java.util.function.BooleanSupplier;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Keeps a phone WebView from taking playback ownership while direct AA is visible. */
final class YoutubePlaybackHostPolicy {
	private YoutubePlaybackHostPolicy() {
	}

	static boolean isPreferredHost(YoutubeWebView web) {
		try {
			return web.getAddon().isPreferredPlaybackActivity(
					MainActivityDelegate.get(web.getContext()));
		} catch (RuntimeException ignored) {
			return true;
		}
	}

	static boolean forward(YoutubeWebView web, boolean currentEngine, boolean activePlaybackHost,
			YoutubePlaybackMetadata.Signal signal, BooleanSupplier explicitPlaybackIntent) {
		boolean preferred = isPreferredHost(web);
		if (currentEngine || activePlaybackHost || preferred) return false;
		return shouldForward(false, false, false, explicitPlaybackIntent.getAsBoolean()) &&
				web.getAddon().forwardPlaybackToPreferredHost(web, signal);
	}

	static boolean shouldForward(boolean currentEngine, boolean activePlaybackHost,
			boolean localHostPreferred, boolean explicitPlaybackIntent) {
		return !currentEngine && !activePlaybackHost && !localHostPreferred && explicitPlaybackIntent;
	}
}
