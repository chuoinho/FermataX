package me.aap.fermata.ui.smarttop;

/** Conservative finite-media policy used before labeling an item as Resume. */
public final class SmartTopResumePolicy {
	static final long MIN_POSITION_MILLIS = 30_000L;
	static final long MIN_REMAINING_MILLIS = 60_000L;
	static final double MAX_COMPLETION_RATIO = 0.95D;

	private SmartTopResumePolicy() {
	}

	public static boolean isMeaningful(boolean live, boolean seekable,
			long positionMillis, long durationMillis) {
		if (live || !seekable || (positionMillis < MIN_POSITION_MILLIS) ||
				(durationMillis <= 0L) || (positionMillis >= durationMillis)) return false;
		long remaining = durationMillis - positionMillis;
		return (remaining >= MIN_REMAINING_MILLIS) &&
				(((double) positionMillis / (double) durationMillis) < MAX_COMPLETION_RATIO);
	}
}
