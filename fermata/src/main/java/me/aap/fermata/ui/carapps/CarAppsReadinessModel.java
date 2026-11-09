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

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes) {
		this(rowVisible, statusStringRes, actionStringRes, warningStringRes, Collections.emptyList());
	}

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes,
			List<CarAppsChecks.Check> checks) {
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
		boolean needsRootForKeyboard = (sdk == 30) || (sdk == 31);
		if (rootState == null || CarAppsFeature.isBlockedSdk(sdk)
				|| (rootState == RootState.ABSENT && !needsRootForKeyboard)) {
			return new CarAppsReadinessModel(false, 0, 0, 0, Collections.emptyList());
		}

		if (rootState != RootState.GRANTED) {
			return new CarAppsReadinessModel(
					true,
					R.string.car_apps_readiness_status_need_root,
					(rootState == RootState.AVAILABLE) ? R.string.car_apps_readiness_action_grant : 0,
					needsRootForKeyboard ? R.string.car_apps_readiness_warn_untrusted_ime : 0,
					(rootState == RootState.ABSENT) ? Collections.emptyList()
							: CarAppsChecks.from(rootState, capabilities, sdk)
			);
		}

		int warning = 0;
		if ((capabilities & CAP_INJECT) == 0) {
			warning = R.string.car_apps_readiness_warn_xiaomi_inject;
		}

		// Every real server reports CAP_VD: without it no server was seen yet, and nothing is known to be missing
		List<CarAppsChecks.Check> checks = ((capabilities & CarAppsChecks.CAP_VD) == 0)
				? Collections.emptyList() : CarAppsChecks.from(rootState, capabilities, sdk);

		return new CarAppsReadinessModel(
				true,
				R.string.car_apps_readiness_status_ready,
				0,
				warning,
				checks
		);
	}
}
