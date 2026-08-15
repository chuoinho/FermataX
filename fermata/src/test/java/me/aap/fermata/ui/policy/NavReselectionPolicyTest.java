package me.aap.fermata.ui.policy;

import static me.aap.fermata.ui.policy.BackNavigationPolicy.NavReselectionAction.LEAVE_VIDEO_MODE;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.NavReselectionAction.SHOW_DASHBOARD;
import static me.aap.fermata.ui.policy.BackNavigationPolicy.NavReselectionAction.UNHANDLED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavReselectionPolicyTest {
	@Test
	public void activeVideoWinsOverDestinationReselection() {
		assertEquals(LEAVE_VIDEO_MODE,
				BackNavigationPolicy.resolveNavReselection(true, false));
		assertEquals(LEAVE_VIDEO_MODE,
				BackNavigationPolicy.resolveNavReselection(true, true));
	}

	@Test
	public void dashboardReselectionReturnsDashboardWhenNotInVideoMode() {
		assertEquals(SHOW_DASHBOARD,
				BackNavigationPolicy.resolveNavReselection(false, true));
	}

	@Test
	public void otherDestinationReselectionFallsBackToMediatorBehavior() {
		assertEquals(UNHANDLED,
				BackNavigationPolicy.resolveNavReselection(false, false));
	}
}
