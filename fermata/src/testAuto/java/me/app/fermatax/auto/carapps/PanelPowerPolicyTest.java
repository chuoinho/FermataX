package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.ui.carapps.CarAppsChecks;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PanelPowerPolicyTest {

	@Test
	public void prefOffProducesNone() {
		int allCaps = CarAppsChecks.CAP_PANEL_POWER | CarAppsChecks.CAP_OWN_POWER_GROUP;
		assertEquals(PanelPowerPolicy.PanelMode.NONE,
				PanelPowerPolicy.decide(false, allCaps, true, false));
	}

	@Test
	public void uninteractiveOrLockedProducesNone() {
		int allCaps = CarAppsChecks.CAP_PANEL_POWER;
		assertEquals(PanelPowerPolicy.PanelMode.NONE,
				PanelPowerPolicy.decide(true, allCaps, false, false));
		assertEquals(PanelPowerPolicy.PanelMode.NONE,
				PanelPowerPolicy.decide(true, allCaps, true, true));
		assertEquals(PanelPowerPolicy.PanelMode.NONE,
				PanelPowerPolicy.decide(true, allCaps, false, true));
	}

	@Test
	public void panelPowerCapabilityProducesPanelOff() {
		int caps = CarAppsChecks.CAP_PANEL_POWER | CarAppsChecks.CAP_INJECT;
		assertEquals(PanelPowerPolicy.PanelMode.PANEL_OFF,
				PanelPowerPolicy.decide(true, caps, true, false));
	}

	@Test
	public void panelPowerCapabilityWithOwnPowerGroupStillProducesPanelOff() {
		// SDK 13-15 has wake lock causing CAP_OWN_POWER_GROUP, but also has CAP_PANEL_POWER
		int caps = CarAppsChecks.CAP_PANEL_POWER | CarAppsChecks.CAP_OWN_POWER_GROUP | CarAppsChecks.CAP_INJECT;
		assertEquals(PanelPowerPolicy.PanelMode.PANEL_OFF,
				PanelPowerPolicy.decide(true, caps, true, false));
	}

	@Test
	public void ownPowerGroupWithoutPanelPowerProducesNone() {
		// SDK 36 real independent power group: genuine sleep, no panel off
		int caps = CarAppsChecks.CAP_OWN_POWER_GROUP | CarAppsChecks.CAP_INJECT;
		assertEquals(PanelPowerPolicy.PanelMode.NONE,
				PanelPowerPolicy.decide(true, caps, true, false));
	}

	@Test
	public void neitherCapabilityProducesDim() {
		// Phase 2 fallback: no panel power, no own power group -> black screen
		int caps = CarAppsChecks.CAP_INJECT | CarAppsChecks.CAP_TRUSTED;
		assertEquals(PanelPowerPolicy.PanelMode.DIM,
				PanelPowerPolicy.decide(true, caps, true, false));
	}

	@Test
	public void sdkSupportedRangeIs29To35() {
		org.junit.Assert.assertFalse(PanelPowerPolicy.sdkSupported(28));
		org.junit.Assert.assertTrue(PanelPowerPolicy.sdkSupported(29));
		org.junit.Assert.assertTrue(PanelPowerPolicy.sdkSupported(33));
		org.junit.Assert.assertTrue(PanelPowerPolicy.sdkSupported(35));
		org.junit.Assert.assertFalse(PanelPowerPolicy.sdkSupported(36));
	}
}
