package me.aap.fermata.addon.web.yt;

/** Consumes the first ordinary YouTube entry once per confirmed or provisional AA session. */
final class YoutubeAutoSessionTracker {
	private Object provisionalHost;
	private long consumedGeneration;
	private boolean provisionalConsumed;

	Decision consume(Object host, long generation, boolean explicitTarget,
			boolean playbackActive) {
		if (generation > 0L) {
			if ((provisionalHost == host) && provisionalConsumed) {
				consumedGeneration = generation;
				provisionalHost = null;
				provisionalConsumed = false;
			}
			if (consumedGeneration == generation) return Decision.KEEP;
			consumedGeneration = generation;
			return decision(explicitTarget, playbackActive);
		}

		if (provisionalHost != host) {
			provisionalHost = host;
			provisionalConsumed = false;
		}
		if (provisionalConsumed) return Decision.KEEP;
		provisionalConsumed = true;
		return decision(explicitTarget, playbackActive);
	}

	private static Decision decision(boolean explicitTarget, boolean playbackActive) {
		return (explicitTarget || playbackActive) ? Decision.KEEP : Decision.RESET_HOME;
	}

	enum Decision { KEEP, RESET_HOME }
}
