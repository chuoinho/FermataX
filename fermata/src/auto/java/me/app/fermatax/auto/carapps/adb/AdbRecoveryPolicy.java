package me.app.fermatax.auto.carapps.adb;

import androidx.annotation.Nullable;

/** What the recovery has to do next, from facts only; the job and the start in CarDisplayClient both ask it. */
public final class AdbRecoveryPolicy {

	public enum Action {NOTHING, WAIT_WIFI, RESTORE_ADB_SETTING, ENABLE_ADB, CONNECT}

	private AdbRecoveryPolicy() {
	}

	/**
	 * A setting we changed is given back first, even after the pairing was revoked: it is the user's.
	 * Without the permission it cannot be, so it is not asked for. Wireless debugging that cannot be switched on
	 * (no permission) is the user's to enable, the same as no Wi-Fi.
	 */
	public static Action next(boolean paired, boolean serverRunning, boolean wifi, boolean adbWifiOn,
	                          boolean canWriteSecure, boolean restorePending) {
		if (restorePending && canWriteSecure) return Action.RESTORE_ADB_SETTING;
		if (!paired || serverRunning) return Action.NOTHING;
		if (!wifi) return Action.WAIT_WIFI;
		if (adbWifiOn) return Action.CONNECT;
		return canWriteSecure ? Action.ENABLE_ADB : Action.WAIT_WIFI;
	}

	/** Android 17 hides adb_enabled from apps, so the state of the adbd service is read instead. */
	public static boolean adbRunning(int sdk, @Nullable String adbEnabled, @Nullable String initSvcAdbd) {
		return (sdk >= 37) ? "running".equals(initSvcAdbd) : "1".equals(adbEnabled);
	}
}
