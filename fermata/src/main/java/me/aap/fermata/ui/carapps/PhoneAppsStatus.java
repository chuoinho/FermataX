package me.aap.fermata.ui.carapps;

import android.os.Build;

import androidx.annotation.Nullable;

import me.aap.fermata.R;

/** The one-line Phone Apps status of the Settings page and the Readiness row. */
public enum PhoneAppsStatus {
	NOT_SUPPORTED(R.string.car_apps_status_unsupported, false),
	OFF(R.string.car_apps_status_off, false),
	READY_ROOT(R.string.car_apps_status_ready_root, false),
	READY_ADB(R.string.car_apps_status_ready_adb, false),
	NEEDS_ROOT_GRANT(R.string.car_apps_readiness_status_need_root, true),
	NEEDS_SETUP(R.string.car_apps_readiness_status_need_setup, true),
	/** The paired channel could not start the server: its authorization may be revoked. */
	FAILED(R.string.car_apps_server_error, true);

	public final int textRes;
	public final boolean needsAction;

	PhoneAppsStatus(int textRes, boolean needsAction) {
		this.textRes = textRes;
		this.needsAction = needsAction;
	}

	public static PhoneAppsStatus of(boolean supported, boolean enabled, @Nullable RootState root, boolean adbPaired,
			boolean serverFailed, int sdk) {
		if (!supported) return NOT_SUPPORTED;
		if (!enabled) return OFF;
		if (CarAppsFeature.usable(root)) return READY_ROOT;
		if (CarAppsFeature.canRun(root, adbPaired, sdk)) return serverFailed ? FAILED : READY_ADB;
		// 13+: the setup offers both ways, so "root required" would be wrong there
		return ((root == RootState.AVAILABLE) && !CarAppsFeature.adbSupportedSdk(sdk)) ? NEEDS_ROOT_GRANT : NEEDS_SETUP;
	}

	public static PhoneAppsStatus current() {
		return of(CarAppsFeature.isSupported(), CarAppsFeature.isEnabled(), CarAppsFeature.state(),
				CarAppsFeature.isAdbPaired(), CarAppsFeature.serverFailed(), Build.VERSION.SDK_INT);
	}
}
