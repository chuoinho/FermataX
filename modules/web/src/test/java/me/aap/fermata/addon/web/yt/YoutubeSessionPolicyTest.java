package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeSessionPolicy.Action.KEEP;
import static me.aap.fermata.addon.web.yt.YoutubeSessionPolicy.Action.OPEN_EXPLICIT;
import static me.aap.fermata.addon.web.yt.YoutubeSessionPolicy.Action.RESET_HOME;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YoutubeSessionPolicyTest {
	private static final long FIVE_HOURS = YoutubeSessionPolicy.DEFAULT_RETENTION_MILLIS;
	private final YoutubeSessionPolicy policy = new YoutubeSessionPolicy(FIVE_HOURS);

	@Test
	public void explicitTargetWinsColdAndStaleState() {
		assertEquals(OPEN_EXPLICIT,
				policy.resolve(true, false, true, 1L, FIVE_HOURS + 1L));
	}

	@Test
	public void activePlaybackWinsColdAndStaleState() {
		assertEquals(KEEP,
				policy.resolve(false, true, true, 1L, FIVE_HOURS + 1L));
	}

	@Test
	public void coldInactiveSessionResetsImmediately() {
		assertEquals(RESET_HOME, policy.resolve(false, false, true, 0L, 1L));
	}

	@Test
	public void softSessionIsKeptBeforeFiveHours() {
		assertEquals(KEEP, policy.resolve(false, false, false, 100L,
				100L + FIVE_HOURS - 1L));
	}

	@Test
	public void softSessionResetsAtFiveHours() {
		assertEquals(RESET_HOME, policy.resolve(false, false, false, 100L,
				100L + FIVE_HOURS));
	}

	@Test
	public void clockRollbackDoesNotResetSession() {
		assertEquals(KEEP, policy.resolve(false, false, false, 200L, 100L));
	}
}
