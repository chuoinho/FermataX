package me.aap.fermata.ui.carapps;

import android.os.Build;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;

/**
 * Presentation model for the Car Apps entry in the readiness checklist.
 */
public final class CarAppsReadinessModel {

	public static final int CAP_INJECT = CarAppsChecks.CAP_INJECT;

	public final boolean rowVisible;
	public final int statusStringRes;
	public final int actionStringRes;
	public final int warningStringRes;
	public final List<CarAppsChecks.Check> checks;
	/** Offer "Set up without root": Android 13+, no usable root, not paired yet. */
	public final boolean adbSetup;
	/** Offer "Set up again": the paired ADB channel failed to start the server (its authorization may be revoked). */
	public final boolean adbSetupAgain;

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes) {
		this(rowVisible, statusStringRes, actionStringRes, warningStringRes, Collections.emptyList());
	}

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes,
			List<CarAppsChecks.Check> checks) {
		this(rowVisible, statusStringRes, actionStringRes, warningStringRes, checks, false);
	}

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes,
			List<CarAppsChecks.Check> checks, boolean adbSetup) {
		this(rowVisible, statusStringRes, actionStringRes, warningStringRes, checks, adbSetup, false);
	}

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes,
			List<CarAppsChecks.Check> checks, boolean adbSetup, boolean adbSetupAgain) {
		this.adbSetup = adbSetup;
		this.adbSetupAgain = adbSetupAgain;
		this.rowVisible = rowVisible;
		this.statusStringRes = statusStringRes;
		this.actionStringRes = actionStringRes;
		this.warningStringRes = warningStringRes;
		this.checks = (checks != null) ? checks : Collections.emptyList();
	}

	public static CarAppsReadinessModel from(@Nullable RootState rootState, int capabilities) {
		return from(rootState, capabilities, Build.VERSION.SDK_INT);
	}

	public static CarAppsReadinessModel from(@Nullable RootState rootState, int capabilities, int sdk) {
		boolean canRun = CarAppsFeature.canRun(rootState, CarAppsFeature.isAdbPaired(), sdk);
		boolean adbSetup = (rootState != null) && !CarAppsFeature.isBlockedSdk(sdk)
				&& CarAppsFeature.adbSupportedSdk(sdk) && !canRun;
		if (rootState == null || CarAppsFeature.isBlockedSdk(sdk) || (rootState == RootState.ABSENT && !canRun && !adbSetup)) {
			return new CarAppsReadinessModel(false, 0, 0, 0, Collections.emptyList());
		}

		if (rootState == RootState.ABSENT && !canRun) {
			return new CarAppsReadinessModel(true, R.string.car_apps_readiness_status_need_setup, 0, 0,
					Collections.emptyList(), true);
		}

		if (!canRun) {
			return new CarAppsReadinessModel(
					true,
					R.string.car_apps_readiness_status_need_root,
					R.string.car_apps_readiness_action_grant,
					0,
					CarAppsChecks.from(rootState, capabilities, sdk),
					adbSetup
			);
		}

		int warning = 0;
		if ((capabilities & CAP_INJECT) == 0) {
			warning = R.string.car_apps_readiness_warn_xiaomi_inject;
		}

		// Every real server reports CAP_VD: without it no server was seen yet, and nothing is known to be missing
		List<CarAppsChecks.Check> checks = ((capabilities & CarAppsChecks.CAP_VD) == 0)
				? Collections.emptyList() : CarAppsChecks.from(rootState, capabilities, sdk);

		if (!CarAppsFeature.usable(rootState) && CarAppsFeature.serverFailed()) {
			return new CarAppsReadinessModel(true, R.string.car_apps_server_error, 0, warning, checks, false, true);
		}

		return new CarAppsReadinessModel(
				true,
				R.string.car_apps_readiness_status_ready,
				0,
				warning,
				checks
		);
	}
}
