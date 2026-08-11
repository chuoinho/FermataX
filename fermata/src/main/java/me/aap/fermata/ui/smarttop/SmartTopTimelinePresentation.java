package me.aap.fermata.ui.smarttop;

import me.aap.fermata.ui.policy.PlaybackTimelinePolicy;

/** Values that can visibly change during a timeline-only SmartTop update. */
record SmartTopTimelinePresentation(
		PlaybackTimelinePolicy.Mode mode,
		int progress,
		long currentSeconds,
		long remainingSeconds,
		boolean playing) {
	static SmartTopTimelinePresentation of(SmartTopTimeline timeline) {
		PlaybackTimelinePolicy.Mode mode = timeline.mode();
		if (mode != PlaybackTimelinePolicy.Mode.SEEKABLE) {
			return new SmartTopTimelinePresentation(mode, 0, 0L, 0L, timeline.playing());
		}
		long position = Math.max(0L, timeline.positionMillis());
		long duration = Math.max(0L, timeline.durationMillis());
		return new SmartTopTimelinePresentation(mode,
				SmartTopBinder.progress(position, duration), position / 1000L,
				Math.max(0L, duration - position) / 1000L, timeline.playing());
	}
}
