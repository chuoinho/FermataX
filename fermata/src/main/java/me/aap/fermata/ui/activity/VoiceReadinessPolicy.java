package me.aap.fermata.ui.activity;

/** Bounded delayed retry policy for dynamic addon/fragment voice routing. */
final class VoiceReadinessPolicy {
	static final long RETRY_DELAY_MS = 100L;
	static final long TIMEOUT_MS = 30_000L;

	private VoiceReadinessPolicy() {
	}

	static long deadline(long now) {
		return now + TIMEOUT_MS;
	}

	static boolean shouldRetry(long now, long deadline, boolean activityAlive) {
		return activityAlive && (now < deadline);
	}
}
