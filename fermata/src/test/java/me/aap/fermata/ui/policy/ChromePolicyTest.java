package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChromePolicyTest {
	@Test
	public void topBackFollowsRouteInsteadOfPlaybackOwnershipOnEveryHost() {
		for (RuntimeHostMode host : new RuntimeHostMode[]{
				RuntimeHostMode.PHONE, RuntimeHostMode.AA_PROJECTION, RuntimeHostMode.MIRROR}) {
			assertFalse(ChromePolicy.isTopBackVisible(
					host, true, false, true, false, false));
			assertTrue(ChromePolicy.isTopBackVisible(
					host, true, false, false, false, false));
			assertTrue(ChromePolicy.isTopBackVisible(
					host, false, false, false, false, false));
			assertTrue(ChromePolicy.isTopBackVisible(
					host, true, false, false, true, false));
			assertTrue(ChromePolicy.isTopBackVisible(
					host, false, true, false, false, false));
			assertTrue(ChromePolicy.isTopBackVisible(
					host, false, true, false, false, true));
		}
	}

	@Test
	public void missingHostNeverShowsTopBack() {
		assertFalse(ChromePolicy.isTopBackVisible(
				null, true, true, false, false, false));
	}
}
