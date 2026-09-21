package me.aap.fermata.addon.web.stremio;

/** One-shot AA Play admission for a newly transferred document. */
final class StremioPlayerTransferGate {
	private long documentGeneration = Long.MIN_VALUE;
	private boolean armed;

	void arm(long generation) {
		documentGeneration = generation;
		armed = true;
	}

	void cancel() {
		armed = false;
		documentGeneration = Long.MIN_VALUE;
	}

	void onPlaying(long generation) {
		if (armed && (documentGeneration == generation)) cancel();
	}

	boolean shouldDispatchPlay(long generation, boolean paused, boolean hasPlayHandler) {
		if (!armed || (documentGeneration != generation) || !paused || !hasPlayHandler) return false;
		armed = false;
		return true;
	}
}
