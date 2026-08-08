package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

/** Keeps toolbar state tied to the page that is actually visible in the WebView. */
final class YoutubeToolbarPolicy {
	private YoutubeToolbarPolicy() {
	}

	static boolean isPlaybackPage(@Nullable String url) {
		if ((url == null) || url.isBlank()) return false;
		try {
			YoutubeItem.fromPageUrl(url, "", 0L);
			return true;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	static boolean showBack(boolean externalPlayback, boolean browserFullscreen,
			boolean canGoBack, boolean rootPage, @Nullable String url) {
		return externalPlayback || browserFullscreen || canGoBack || !rootPage ||
				isPlaybackPage(url);
	}

	static boolean usePlaybackTitle(@Nullable String url, boolean youtubeOwner) {
		return youtubeOwner && isPlaybackPage(url);
	}
}
