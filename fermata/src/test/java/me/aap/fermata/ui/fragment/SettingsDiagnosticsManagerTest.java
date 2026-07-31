package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsDiagnosticsManagerTest {
	@Test
	public void callbackRequiresCurrentLiveUiGeneration() {
		assertTrue(SettingsDiagnosticsManager.isUiCallbackAllowed(() -> true, false, false));
		assertFalse(SettingsDiagnosticsManager.isUiCallbackAllowed(() -> false, false, false));
		assertFalse(SettingsDiagnosticsManager.isUiCallbackAllowed(() -> true, true, false));
		assertFalse(SettingsDiagnosticsManager.isUiCallbackAllowed(() -> true, false, true));
		assertFalse(SettingsDiagnosticsManager.isUiCallbackAllowed(null, false, false));
	}

	@Test
	public void callbackGuardFailureIsFailClosed() {
		assertFalse(SettingsDiagnosticsManager.isUiCallbackAllowed(() -> {
			throw new IllegalStateException("stale view");
		}, false, false));
	}
}
