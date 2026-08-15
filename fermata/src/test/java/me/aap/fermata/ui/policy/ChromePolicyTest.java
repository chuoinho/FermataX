package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChromePolicyTest {
	@Test
	public void phoneTopBackFollowsRouteInsteadOfPlaybackOwnership() {
		assertFalse(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.PHONE, true, false, true, false, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.PHONE, true, false, false, false, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.PHONE, false, false, false, false, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.PHONE, true, false, false, true, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.PHONE, false, true, false, false, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.PHONE, false, true, false, false, true));
	}

	@Test
	public void automotiveTopBackKeepsSinglePlaybackOwner() {
		assertFalse(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, true, false, true, false, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, true, false, false, false, false));
		assertTrue(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.MIRROR, false, true, false, false, false));
		assertFalse(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, false, true, false, false, true));
		assertFalse(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, true, false, false, true, false));
		assertFalse(ChromePolicy.isTopBackVisible(
				RuntimeHostMode.AA_PROJECTION, false, false, false, false, false));
	}
}
