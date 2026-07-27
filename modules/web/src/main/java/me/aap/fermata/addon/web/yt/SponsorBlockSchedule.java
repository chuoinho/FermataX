package me.aap.fermata.addon.web.yt;

import java.util.List;

final class SponsorBlockSchedule {
	static final long MIN_DELAY_MS = 250L;
	static final long MAX_DELAY_MS = 1_000L;
	static final long POST_SEGMENT_RESCAN_MS = 1_000L;
	private static final long[] RETRY_DELAYS_MS = {1_500L, 4_000L, 10_000L};

	private SponsorBlockSchedule() {
	}

	static int findSegmentIndex(List<SponsorBlockClient.Segment> segments, long positionMillis) {
		long position = Math.max(0L, positionMillis);
		for (int i = 0; i < segments.size(); i++) {
			long end = millis(segments.get(i).endSeconds());
			if (position < end) return i;
		}
		return segments.size();
	}

	static long delayUntil(long positionMillis, long triggerMillis, float playbackSpeed) {
		double speed = (Float.isFinite(playbackSpeed) && (playbackSpeed > 0f)) ? playbackSpeed : 1d;
		double mediaDistance = Math.max(0L, triggerMillis - Math.max(0L, positionMillis));
		long wallDelay = (long) Math.ceil(mediaDistance / speed);
		return Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, wallDelay));
	}

	static long millis(double seconds) {
		if (!Double.isFinite(seconds) || (seconds <= 0d)) return 0L;
		double millis = seconds * 1000d;
		return (millis >= Long.MAX_VALUE) ? Long.MAX_VALUE : (long) millis;
	}

	static long retryDelayMillis(int attempt) {
		return ((attempt < 0) || (attempt >= RETRY_DELAYS_MS.length)) ? -1L :
				RETRY_DELAYS_MS[attempt];
	}
}
