package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeHostInterruptionPolicy.Decision.DISCARD;
import static me.aap.fermata.addon.web.yt.YoutubeHostInterruptionPolicy.Decision.RESTORE;
import static me.aap.fermata.addon.web.yt.YoutubeHostInterruptionPolicy.Decision.RETRY;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YoutubeHostInterruptionPolicyTest {
	@Test
	public void sameResumedHostAndPlaybackOwnerRestores() {
		assertEquals(RESTORE, YoutubeHostInterruptionPolicy.resolve(
				4L, 4L, true, true, true, true));
	}

	@Test
	public void hostOrViewReadinessRetriesWithoutLosingIntent() {
		assertEquals(RETRY, YoutubeHostInterruptionPolicy.resolve(
				4L, 4L, false, true, true, true));
		assertEquals(RETRY, YoutubeHostInterruptionPolicy.resolve(
				4L, 4L, true, false, true, true));
	}

	@Test
	public void explicitRelaunchNavigationOrOwnerChangeDiscards() {
		assertEquals(DISCARD, YoutubeHostInterruptionPolicy.resolve(
				4L, 5L, true, true, true, true));
		assertEquals(DISCARD, YoutubeHostInterruptionPolicy.resolve(
				4L, 4L, true, true, false, true));
		assertEquals(DISCARD, YoutubeHostInterruptionPolicy.resolve(
				4L, 4L, true, true, true, false));
	}
}
