package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.carapps.CarAppsFeature;

public class AdbSupportTest {

	@Test
	public void adbSupportsAndroid13Through17() {
		assertTrue(CarAppsFeature.adbSupportedSdk(33));
		assertTrue(CarAppsFeature.adbSupportedSdk(34));
		assertTrue(CarAppsFeature.adbSupportedSdk(35));
		assertTrue(CarAppsFeature.adbSupportedSdk(36));
		assertTrue(CarAppsFeature.adbSupportedSdk(37));
	}

	@Test
	public void adbRejectsAndroid10And12L() {
		assertFalse(CarAppsFeature.adbSupportedSdk(29));
		assertFalse(CarAppsFeature.adbSupportedSdk(32));
	}

	@Test
	public void android11And12RequireRootAfterShellFlowFails() {
		assertFalse(CarAppsFeature.adbSupportedSdk(30));
		assertFalse(CarAppsFeature.adbSupportedSdk(31));
	}
}
