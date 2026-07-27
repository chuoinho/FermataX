package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubePlaybackSessionTest {
	@Test
	public void metadataCanUpdateWithinOneGeneration() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubeItem item = item("first", "", 10L);
		YoutubePlaybackSession.Snapshot token = sessions.begin(item, 5L);

		assertTrue(sessions.update(token, item.withTitle("Resolved title")));
		assertTrue(sessions.isCurrent(token));
		assertEquals(token.generation(), sessions.current().generation());
		assertEquals("Resolved title", sessions.current().item().title());
	}

	@Test
	public void nextPlaybackRejectsCallbacksFromOldGeneration() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubePlaybackSession.Snapshot old = sessions.begin(item("first", "First", 10L), 1L);
		YoutubePlaybackSession.Snapshot current = sessions.begin(item("second", "Second", 20L), 2L);

		assertFalse(sessions.isCurrent(old));
		assertFalse(sessions.update(old, old.item().withTitle("Stale")));
		assertFalse(sessions.finish(old));
		assertTrue(sessions.isCurrent(current));
	}

	@Test
	public void finishAndInvalidateAdvanceGeneration() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubePlaybackSession.Snapshot first = sessions.begin(item("first", "First", 10L), 1L);

		assertTrue(sessions.finish(first));
		assertNull(sessions.current());
		assertFalse(sessions.isCurrent(first));

		YoutubePlaybackSession.Snapshot second = sessions.begin(item("second", "Second", 20L), 2L);
		assertTrue(second.generation() > first.generation());
		sessions.invalidate();
		assertFalse(sessions.isCurrent(second));
	}

	@Test
	public void reusedVideoNodeGetsANewGenerationAfterAtoBtoA() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubePlaybackSession.Snapshot firstA = sessions.begin(item("a", "A", 1L), 1L);
		YoutubePlaybackSession.Snapshot b = sessions.begin(item("b", "B", 2L), 2L);
		YoutubePlaybackSession.Snapshot secondA = sessions.begin(item("a", "A again", 3L), 3L);

		assertFalse(sessions.isCurrent(firstA));
		assertFalse(sessions.isCurrent(b));
		assertTrue(sessions.isCurrent(secondA));
		assertTrue(secondA.generation() > b.generation());
	}

	@Test
	public void replayAfterEndedVideoCannotReuseTheEndedGeneration() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubePlaybackSession.Snapshot first = sessions.begin(item("replay", "Replay", 1L), 1L);

		assertTrue(sessions.finish(first));
		YoutubePlaybackSession.Snapshot replay = sessions.begin(item("replay", "Replay", 2L), 2L);

		assertTrue(replay.generation() > first.generation());
		assertTrue(sessions.isCurrent(replay));
		assertFalse(sessions.isCurrent(first));
	}

	@Test
	public void updateCannotChangePlaybackIdentity() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubePlaybackSession.Snapshot current = sessions.begin(item("first", "First", 10L), 1L);

		assertFalse(sessions.update(current, item("second", "Second", 20L)));
		assertEquals("first", sessions.current().item().videoId());
	}

	@Test
	public void currentForReturnsTheTypedGenerationOnlyForTheOwnedVideo() {
		YoutubePlaybackSession sessions = new YoutubePlaybackSession();
		YoutubePlaybackSession.Snapshot current =
				sessions.begin(item("owned", "Owned", 10L), 1L);

		assertEquals(current, sessions.currentFor("owned"));
		assertNull(sessions.currentFor("other"));
		sessions.invalidate();
		assertNull(sessions.currentFor("owned"));
	}

	private static YoutubeItem item(String id, String title, long playedAt) {
		return YoutubeItem.fromPageUrl("https://m.youtube.com/watch?v=" + id, title, playedAt);
	}
}
