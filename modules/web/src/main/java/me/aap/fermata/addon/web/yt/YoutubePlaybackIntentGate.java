package me.aap.fermata.addon.web.yt;

import java.util.Objects;

/** Requires a real YouTube video page and an explicit first-play intent. */
final class YoutubePlaybackIntentGate {
	static final long USER_GESTURE_WINDOW_MS = 2500L;
	private long userGestureDeadline;
	private boolean explicitPlayback;
	private String activeVideoKey;

	void armUserGesture(long eventTime) {
		userGestureDeadline = eventTime + USER_GESTURE_WINDOW_MS;
	}

	void armExplicitPlayback() {
		explicitPlayback = true;
	}

	boolean accepts(String pageUrl, long eventTime, boolean ownsPlayback) {
		String key = YoutubeFullscreenGate.playbackKey(pageUrl, null);
		if (!YoutubeFullscreenGate.isYoutubeVideoKey(key)) return false;

		if (ownsPlayback || Objects.equals(activeVideoKey, key)) {
			activeVideoKey = key;
			return true;
		}

		if (!explicitPlayback && ((userGestureDeadline == 0L) ||
				(eventTime > userGestureDeadline))) return false;

		activeVideoKey = key;
		explicitPlayback = false;
		userGestureDeadline = 0L;
		return true;
	}

	void reset() {
		activeVideoKey = null;
		explicitPlayback = false;
		userGestureDeadline = 0L;
	}
}
