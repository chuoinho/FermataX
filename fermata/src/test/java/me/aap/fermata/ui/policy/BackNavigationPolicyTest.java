package me.aap.fermata.ui.policy;

import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.FINISH;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.SHOW_NAV_FRAGMENT;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.PlayerBackAction.LEAVE_VIDEO_MODE;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.PlayerBackAction.REFRESH_CHROME;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.PlayerBackAction.TRY_AUDIO_SOURCE;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackNavigationPolicyTest {
	@Test
	public void splitBackUsesChildHistoryThenDashboard() {
		assertEquals(REFRESH_CHROME,
				BackNavigationPolicy.resolvePlayerBack(true, true, true, false));
		assertEquals(BackNavigationPolicy.PlayerBackAction.SHOW_DASHBOARD,
				BackNavigationPolicy.resolvePlayerBack(true, true, false, false));
		assertEquals(BackNavigationPolicy.PlayerBackAction.SHOW_DASHBOARD,
				BackNavigationPolicy.resolvePlayerBack(true, false, false, false));
	}

	@Test
	public void fullscreenBackLeavesVideoBeforeAudioOrActivityFallback() {
		assertEquals(BackNavigationPolicy.PlayerBackAction.HANDLED,
				BackNavigationPolicy.resolvePlayerBack(false, true, true, true));
		assertEquals(LEAVE_VIDEO_MODE,
				BackNavigationPolicy.resolvePlayerBack(false, false, false, true));
		assertEquals(TRY_AUDIO_SOURCE,
				BackNavigationPolicy.resolvePlayerBack(false, false, false, false));
	}

	@Test
	public void activityBackReturnsThroughNavDashboardThenExit() {
		assertEquals(BackNavigationPolicy.ActivityBackAction.HANDLED,
				BackNavigationPolicy.resolveActivityBack(true, true, true, false, false));
		assertEquals(SHOW_NAV_FRAGMENT,
				BackNavigationPolicy.resolveActivityBack(true, false, true, false, false));
		assertEquals(BackNavigationPolicy.ActivityBackAction.SHOW_DASHBOARD,
				BackNavigationPolicy.resolveActivityBack(true, false, true, true, false));
		assertEquals(FINISH,
				BackNavigationPolicy.resolveActivityBack(true, false, true, true, true));
		assertEquals(FINISH,
				BackNavigationPolicy.resolveActivityBack(false, false, false, false, false));
	}
}
