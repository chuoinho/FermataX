package me.aap.fermata.addon.web.yt;

/** Decides whether a suspended projected fullscreen transaction is still safe to restore. */
final class YoutubeHostInterruptionPolicy {
	private YoutubeHostInterruptionPolicy() {
	}

	static Decision resolve(long suspendedRelaunchGeneration, long currentRelaunchGeneration,
			boolean hostResumed, boolean viewAttached, boolean youtubeActive,
			boolean youtubeOwnsPlayback) {
		if ((suspendedRelaunchGeneration != currentRelaunchGeneration) ||
				!youtubeActive || !youtubeOwnsPlayback) return Decision.DISCARD;
		if (!hostResumed || !viewAttached) return Decision.RETRY;
		return Decision.RESTORE;
	}

	/** Diagnostic name of the condition that made {@link #resolve} discard. */
	static String discardReason(long suspendedRelaunchGeneration, long currentRelaunchGeneration,
			boolean youtubeActive, boolean youtubeOwnsPlayback) {
		if (suspendedRelaunchGeneration != currentRelaunchGeneration) return "relaunch";
		return youtubeActive ? "owner" : "inactive";
	}

	enum Decision {
		RESTORE,
		RETRY,
		DISCARD
	}
}
