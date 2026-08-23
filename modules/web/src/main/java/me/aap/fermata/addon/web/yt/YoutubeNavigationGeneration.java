package me.aap.fermata.addon.web.yt;

/** Rejects callbacks emitted by a WebView navigation that no longer owns the session. */
final class YoutubeNavigationGeneration {
	private long generation = 1L;
	private boolean runtimeOpen = true;

	synchronized long next() {
		return runtimeOpen ? ++generation : 0L;
	}

	synchronized long openRuntime() {
		runtimeOpen = true;
		return ++generation;
	}

	synchronized void closeRuntime() {
		runtimeOpen = false;
		generation++;
	}

	synchronized boolean isCurrent(long candidate) {
		return runtimeOpen && (candidate == generation);
	}
}
