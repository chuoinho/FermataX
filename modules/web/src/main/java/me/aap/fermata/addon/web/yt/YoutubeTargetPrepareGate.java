package me.aap.fermata.addon.web.yt;

/** Accepts a WebView prepare acknowledgement only for the latest requested video generation. */
final class YoutubeTargetPrepareGate {
	private long requestRevision;
	private String videoId = "";
	private long playbackGeneration;

	long begin(String videoId, long playbackGeneration) {
		this.videoId = (videoId == null) ? "" : videoId;
		this.playbackGeneration = Math.max(0L, playbackGeneration);
		return ++requestRevision;
	}

	boolean complete(String videoId, long playbackGeneration) {
		if (!matches(videoId, playbackGeneration)) return false;
		clear();
		return true;
	}

	boolean accepts(String videoId, long playbackGeneration) {
		return !isPending() || matches(videoId, playbackGeneration);
	}

	boolean cancel(long expectedRevision) {
		if ((expectedRevision != requestRevision) || videoId.isEmpty()) return false;
		clear();
		return true;
	}

	boolean isPending() {
		return !videoId.isEmpty();
	}

	void cancel() {
		if (!videoId.isEmpty()) clear();
	}

	private boolean matches(String videoId, long playbackGeneration) {
		return !this.videoId.isEmpty() && this.videoId.equals(videoId) &&
				(this.playbackGeneration == playbackGeneration);
	}

	private void clear() {
		requestRevision++;
		videoId = "";
		playbackGeneration = 0L;
	}
}
