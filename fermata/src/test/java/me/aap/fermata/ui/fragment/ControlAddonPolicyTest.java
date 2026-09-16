package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonState;

public class ControlAddonPolicyTest {
	@Test
	public void controlListsEveryEnabledAddonThatHasACarNavigationScreen() {
		AddonInfo navigation = addon("dashboard,navigation");
		AddonInfo dashboardOnly = addon("dashboard");

		assertTrue(DashboardItems.isControlAddon(navigation, AddonState.LOADED));
		assertTrue(DashboardItems.isControlAddon(navigation, AddonState.ENABLED_PENDING));
		assertFalse(DashboardItems.isControlAddon(navigation, AddonState.DISABLED));
		assertFalse(DashboardItems.isControlAddon(dashboardOnly, AddonState.LOADED));
	}

	@Test
	public void enabledPendingRemainsAnOpenActionUntilLoadingStarts() {
		assertEquals(0, DashboardItems.getControlAddonStatusRes(AddonState.ENABLED_PENDING));
		assertTrue(DashboardItems.isControlAddonOpenEnabled(AddonState.ENABLED_PENDING));
		assertEquals(me.aap.fermata.R.string.control_addon_loading,
				DashboardItems.getControlAddonStatusRes(AddonState.LOADING));
		assertFalse(DashboardItems.isControlAddonOpenEnabled(AddonState.LOADING));
		assertEquals(me.aap.fermata.R.string.control_addon_failed,
				DashboardItems.getControlAddonStatusRes(AddonState.FAILED));
		assertFalse(DashboardItems.isControlAddonOpenEnabled(AddonState.FAILED));
	}

	private static AddonInfo addon(String capabilities) {
		return new AddonInfo("module", "test.Addon", 1, 1, 1, 1,
				true, true, true, true, "", capabilities);
	}
}
