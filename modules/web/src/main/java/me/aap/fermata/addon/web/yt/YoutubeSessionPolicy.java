package me.aap.fermata.addon.web.yt;

/** Decides whether a returning YouTube host should restore or start from Home. */
final class YoutubeSessionPolicy {
	static final long DEFAULT_RETENTION_MILLIS = 5L * 60L * 60L * 1000L;
	private final long retentionMillis;

	YoutubeSessionPolicy(long retentionMillis) {
		this.retentionMillis = Math.max(0L, retentionMillis);
	}

	Action resolve(boolean explicitTarget, boolean playbackActive, boolean coldSession,
			long leftAtMillis, long nowMillis) {
		if (explicitTarget) return Action.OPEN_EXPLICIT;
		if (playbackActive) return Action.KEEP;
		if (coldSession) return Action.RESET_HOME;
		if (leftAtMillis <= 0L) return Action.KEEP;
		long elapsed = nowMillis - leftAtMillis;
		return (elapsed >= retentionMillis) ? Action.RESET_HOME : Action.KEEP;
	}

	enum Action {
		KEEP,
		OPEN_EXPLICIT,
		RESET_HOME
	}
}
