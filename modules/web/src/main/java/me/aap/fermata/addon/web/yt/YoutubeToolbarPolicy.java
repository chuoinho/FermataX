package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

/** Keeps playback-title context tied to the page that is actually visible in the WebView. */
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

	static boolean usePlaybackTitle(@Nullable String url, boolean youtubeOwner) {
		return youtubeOwner && isPlaybackPage(url);
	}
}
