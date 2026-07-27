package me.aap.fermata.addon.web.yt;

import java.util.Objects;

/** Owns the generation used to reject callbacks from superseded YouTube playback. */
final class YoutubePlaybackSession {
	private long generation;
	private Snapshot current;

	synchronized Snapshot begin(YoutubeItem item, long startedAtMillis) {
		Objects.requireNonNull(item, "item");
		if (startedAtMillis < 0L) throw new IllegalArgumentException("Start time cannot be negative");
		return current = new Snapshot(++generation, item, startedAtMillis);
	}

	synchronized Snapshot current() {
		return current;
	}

	synchronized Snapshot currentFor(String videoId) {
		if ((current == null) || (videoId == null) ||
				!videoId.equals(current.item().videoId())) return null;
		return current;
	}

	synchronized boolean isCurrent(Snapshot session) {
		return (session != null) && (current != null) &&
				(session.generation() == generation) &&
				session.item().videoId().equals(current.item().videoId());
	}

	synchronized boolean update(Snapshot session, YoutubeItem item) {
		Objects.requireNonNull(item, "item");
		if (!isCurrent(session) || !session.item().videoId().equals(item.videoId())) return false;
		current = new Snapshot(generation, item, session.startedAtMillis());
		return true;
	}

	synchronized boolean finish(Snapshot session) {
		if (!isCurrent(session)) return false;
		current = null;
		generation++;
		return true;
	}

	synchronized void invalidate() {
		current = null;
		generation++;
	}

	record Snapshot(long generation, YoutubeItem item, long startedAtMillis) {
		Snapshot {
			Objects.requireNonNull(item, "item");
		}
	}
}
