package me.app.fermatax.auto;

/** Filters the zero-time duplicate Back event emitted by some projected head units. */
final class ProjectedBackEventFilter {
	static final long DUPLICATE_WINDOW_MILLIS = 250L;
	private long lastRealBackUptime = Long.MIN_VALUE;

	boolean shouldSuppress(boolean back, long eventTime, int deviceId, long nowUptime) {
		if (!back) return false;

		boolean synthetic = (eventTime == 0L) && (deviceId == -1);
		if (!synthetic) {
			lastRealBackUptime = nowUptime;
			return false;
		}

		long elapsed = nowUptime - lastRealBackUptime;
		return (elapsed >= 0L) && (elapsed <= DUPLICATE_WINDOW_MILLIS);
	}
}
