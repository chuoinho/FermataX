package me.aap.fermata.ui.policy;

public final class PlaybackTimelinePolicy {
	private PlaybackTimelinePolicy() {
	}

	public static Mode resolve(boolean liveStream, boolean itemSeekable, boolean engineSeekable,
			long durationMillis) {
		if (itemSeekable && engineSeekable && (durationMillis > 0L)) return Mode.SEEKABLE;
		return liveStream ? Mode.LIVE : Mode.HIDDEN;
	}

	public enum Mode {
		HIDDEN,
		LIVE,
		SEEKABLE
	}
}
