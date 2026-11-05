package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.ui.carapps.CarAppsChecks;
import me.app.fermatax.auto.carapps.ICarDisplayServer;
import me.app.fermatax.auto.carapps.PanelPowerPolicy.PanelMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PanelPowerPolicyTest {

	@Test public void buttonFollowsTheDesignTable() {
		int own = ICarDisplayServer.CAP_OWN_POWER_GROUP, panel = ICarDisplayServer.CAP_PANEL_POWER;
		assertEquals(PanelMode.NONE, PanelPowerPolicy.forButton(own | panel, 35, true, false));
		assertEquals(PanelMode.NONE, PanelPowerPolicy.forButton(own, 36, true, true));
		assertEquals(PanelMode.SLEEP, PanelPowerPolicy.forButton(own, 36, false, false));
		assertEquals(PanelMode.SLEEP, PanelPowerPolicy.forButton(own | panel, 35, false, true));
		assertEquals(PanelMode.SLEEP, PanelPowerPolicy.forButton(0, 31, false, true));
		assertEquals(PanelMode.PANEL_OFF, PanelPowerPolicy.forButton(own | panel, 35, false, false));
		assertEquals(PanelMode.PANEL_OFF, PanelPowerPolicy.forButton(panel, 31, false, false));
		assertEquals(PanelMode.DIM, PanelPowerPolicy.forButton(own, 35, false, false));
		assertEquals(PanelMode.DIM, PanelPowerPolicy.forButton(0, 36, false, false));
		assertEquals(PanelMode.DIM, PanelPowerPolicy.forButton(0, 31, false, false));
	}

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
	public void sdkSupportedRangeIs30To35() {
		org.junit.Assert.assertFalse(PanelPowerPolicy.sdkSupported(29));
		org.junit.Assert.assertTrue(PanelPowerPolicy.sdkSupported(30));
		org.junit.Assert.assertTrue(PanelPowerPolicy.sdkSupported(35));
		org.junit.Assert.assertFalse(PanelPowerPolicy.sdkSupported(36));
	}
}
