package me.aap.fermata.ui.carapps;

import androidx.annotation.Nullable;

import me.aap.fermata.R;

/**
 * Presentation model for the Car Apps entry in the readiness checklist.
 */
public final class CarAppsReadinessModel {

	public static final int CAP_INJECT = 4;
	public static final int CAP_PANEL_POWER = 8;

	public final boolean rowVisible;
	public final int statusStringRes;
	public final int actionStringRes;
	public final int warningStringRes;

	public CarAppsReadinessModel(boolean rowVisible, int statusStringRes, int actionStringRes, int warningStringRes) {
		this.rowVisible = rowVisible;
		this.statusStringRes = statusStringRes;
		this.actionStringRes = actionStringRes;
		this.warningStringRes = warningStringRes;
	}

	public static CarAppsReadinessModel from(@Nullable RootState rootState, int capabilities) {
		if (rootState == null || rootState == RootState.ABSENT) {
			return new CarAppsReadinessModel(false, 0, 0, 0);
		}

		if (rootState == RootState.AVAILABLE) {
			return new CarAppsReadinessModel(
					true,
					R.string.car_apps_readiness_status_need_root,
					R.string.car_apps_readiness_action_grant,
					0
			);
		}

		int warning = 0;
		if ((capabilities & CAP_INJECT) == 0) {
			warning = R.string.car_apps_readiness_warn_xiaomi_inject;
		} else if ((capabilities & CAP_PANEL_POWER) == 0) {
			warning = R.string.car_apps_readiness_warn_panel_power;
		}

		return new CarAppsReadinessModel(
				true,
				R.string.car_apps_readiness_status_ready,
				0,
				warning
		);
	}
}
