package me.app.fermatax.auto.carapps.adb;

import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action.CONNECT;
import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action.ENABLE_ADB;
import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action.NOTHING;
import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action.RESTORE_ADB_SETTING;
import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.Action.WAIT_WIFI;
import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.adbRunning;
import static me.app.fermatax.auto.carapps.adb.AdbRecoveryPolicy.next;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdbRecoveryPolicyTest {

	// next(paired, serverRunning, wifi, adbWifiOn, canWriteSecure, restorePending)

	@Test public void nothingWhenNotPaired() {
		assertEquals(NOTHING, next(false, false, true, false, true, false));
	}

	@Test public void restoresTheSettingWhateverElseIsTrue() {
		assertEquals(RESTORE_ADB_SETTING, next(true, true, true, true, true, true));
		assertEquals(RESTORE_ADB_SETTING, next(true, false, false, true, true, true));
	}

	@Test public void aRevokedPairingStillGivesTheSettingBack() {
		assertEquals(RESTORE_ADB_SETTING, next(false, false, true, true, true, true));
	}

	@Test public void withoutThePermissionTheSettingCannotBeRestored() {
		assertEquals(NOTHING, next(true, true, true, true, false, true));
	}

	@Test public void nothingWhenTheServerRuns() {
		assertEquals(NOTHING, next(true, true, true, true, true, false));
	}

	@Test public void waitsForWifi() {
		assertEquals(WAIT_WIFI, next(true, false, false, false, true, false));
		assertEquals(WAIT_WIFI, next(true, false, false, true, true, false));
	}

	@Test public void waitsWhenWirelessDebuggingIsOffAndCannotBeEnabled() {
		assertEquals(WAIT_WIFI, next(true, false, true, false, false, false));
	}

	@Test public void enablesWirelessDebuggingItself() {
		assertEquals(ENABLE_ADB, next(true, false, true, false, true, false));
	}

	@Test public void connectsWhenWirelessDebuggingIsOn() {
		assertEquals(CONNECT, next(true, false, true, true, true, false));
		assertEquals(CONNECT, next(true, false, true, true, false, false));
	}

	@Test public void adbRunningReadsAdbEnabledBelowAndroid17() {
		assertTrue(adbRunning(36, "1", null));
		assertTrue(adbRunning(30, "1", "stopped"));
		assertFalse(adbRunning(36, "0", "running"));
		assertFalse(adbRunning(36, null, "running"));
	}

	@Test public void adbRunningReadsTheServiceOnAndroid17() {
		assertTrue(adbRunning(37, "0", "running"));
		assertTrue(adbRunning(37, null, "running"));
		assertFalse(adbRunning(37, "1", "stopped"));
		assertFalse(adbRunning(37, "1", null));
	}
}
