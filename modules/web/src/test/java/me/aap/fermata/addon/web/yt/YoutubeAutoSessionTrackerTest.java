package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeAutoSessionTracker.Decision.KEEP;
import static me.aap.fermata.addon.web.yt.YoutubeAutoSessionTracker.Decision.RESET_HOME;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YoutubeAutoSessionTrackerTest {
	@Test
	public void firstOrdinaryEntryResetsOnlyOncePerGeneration() {
		YoutubeAutoSessionTracker tracker = new YoutubeAutoSessionTracker();
		Object host = new Object();
		assertEquals(RESET_HOME, tracker.consume(host, 1L, false, false));
		assertEquals(KEEP, tracker.consume(host, 1L, false, false));
		assertEquals(RESET_HOME, tracker.consume(new Object(), 2L, false, false));
	}

	@Test
	public void explicitAndActivePlaybackWinFirstEntry() {
		YoutubeAutoSessionTracker explicit = new YoutubeAutoSessionTracker();
		assertEquals(KEEP, explicit.consume(new Object(), 1L, true, false));
		assertEquals(KEEP, explicit.consume(new Object(), 1L, false, false));
		YoutubeAutoSessionTracker active = new YoutubeAutoSessionTracker();
		assertEquals(KEEP, active.consume(new Object(), 1L, false, true));
	}

	@Test
	public void provisionalEntryIsNotResetAgainWhenGenerationArrives() {
		YoutubeAutoSessionTracker tracker = new YoutubeAutoSessionTracker();
		Object host = new Object();
		assertEquals(RESET_HOME, tracker.consume(host, 0L, false, false));
		assertEquals(KEEP, tracker.consume(host, 0L, false, false));
		assertEquals(KEEP, tracker.consume(host, 4L, false, false));
		assertEquals(KEEP, tracker.consume(host, 4L, false, false));
	}

	@Test
	public void newProvisionalHostStartsANewSession() {
		YoutubeAutoSessionTracker tracker = new YoutubeAutoSessionTracker();
		assertEquals(RESET_HOME, tracker.consume(new Object(), 0L, false, false));
		assertEquals(RESET_HOME, tracker.consume(new Object(), 0L, false, false));
	}
}
