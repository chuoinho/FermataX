package me.aap.fermata.ui.smarttop;

import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

/** Read-only timeline presentation. It never owns durable playback progress. */
public record SmartTopTimeline(
		PlaybackTimelinePolicy.Mode mode,
		long positionMillis,
		long durationMillis,
		boolean playing) {
	public static final SmartTopTimeline HIDDEN = new SmartTopTimeline(
			PlaybackTimelinePolicy.Mode.HIDDEN, 0L, 0L, false);

	public SmartTopTimeline {
		if (positionMillis < 0L) positionMillis = 0L;
		if (durationMillis < 0L) durationMillis = 0L;
		if ((mode == PlaybackTimelinePolicy.Mode.SEEKABLE) && (durationMillis <= 0L)) {
			throw new IllegalArgumentException("Seekable timeline requires positive duration");
		}
	}
}
