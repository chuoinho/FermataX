package me.aap.fermata.ui.policy;

import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.FINISH;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.ActivityBackAction.SHOW_NAV_FRAGMENT;
import static me.aap.fermata.ui.view.BodyLayout.Mode.BOTH;
import static me.aap.fermata.ui.view.BodyLayout.Mode.FRAME;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackNavigationPolicyTest {
	@Test
	public void fullscreenExitHonorsFragmentSplitCapability() {
		assertEquals(BOTH, BackNavigationPolicy.resolveVideoExitMode(true));
		assertEquals(FRAME, BackNavigationPolicy.resolveVideoExitMode(false));
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

	@Test
	public void addonRootReturnsToDashboardAndDashboardRootExitsOnEveryHost() {
		assertEquals(BackNavigationPolicy.ActivityBackAction.SHOW_DASHBOARD,
				BackNavigationPolicy.resolveActivityBack(true, false, true, true, false));
		assertEquals(FINISH,
				BackNavigationPolicy.resolveActivityBack(true, false, true, true, true));
	}
}
