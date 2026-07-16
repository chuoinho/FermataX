package me.aap.fermata.media.service;

final class StreamRetryPolicy {
	static final int MAX_ATTEMPTS = 3;
	private static final long STABLE_PLAYBACK_MILLIS = 30_000L;

	private StreamRetryPolicy() {
	}

	static int nextAttempt(int previousAttempt, boolean sameStream, long playedMillis) {
		return (!sameStream || (playedMillis >= STABLE_PLAYBACK_MILLIS)) ? 1 : previousAttempt + 1;
	}

	static boolean canRetry(int attempt) {
		return (attempt > 0) && (attempt <= MAX_ATTEMPTS);
	}

	static long delay(int attempt) {
		return switch (attempt) {
			case 1 -> 1_000L;
			case 2 -> 3_000L;
			default -> 8_000L;
		};
	}
}
