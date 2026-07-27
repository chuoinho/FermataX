package me.aap.fermata.addon.stremio.subtitle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.utils.async.Promise;

public class StremioSubtitleSessionTest {
	@Test
	public void replacementCancelsLoadAndRejectsItsLateCompletion() throws Exception {
		StremioSubtitleSession session = new StremioSubtitleSession("video-a");
		session.activate("video-a");
		Promise<byte[]> first = new Promise<>();
		var ownedFirst = session.load(() -> first);
		session.activate("video-a");
		assertTrue(first.isCancelled());
		assertThrows(Exception.class, ownedFirst::get);

		Promise<byte[]> second = new Promise<>();
		var ownedSecond = session.load(() -> second);
		second.complete(new byte[]{2});
		assertArrayEquals(new byte[]{2}, ownedSecond.get());
		assertTrue(session.staleCallbackCount() >= 1L);
	}

	@Test
	public void identityCannotChangeWithinSession() {
		StremioSubtitleSession session = new StremioSubtitleSession("video-a");
		assertThrows(IllegalArgumentException.class, () -> session.activate("video-b"));
	}
}
