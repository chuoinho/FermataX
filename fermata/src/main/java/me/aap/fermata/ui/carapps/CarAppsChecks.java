package me.aap.fermata.ui.carapps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.R;

/**
 * Diagnostics checks for Car Apps capabilities.
 * Pure Java implementation with no Android framework dependencies.
 */
public final class CarAppsChecks {

	// Mirrored from me.app.fermatax.auto.carapps.ICarDisplayServer.aidl
	public static final int CAP_VD = 1;
	public static final int CAP_TRUSTED = 2;
	public static final int CAP_INJECT = 4;
	public static final int CAP_IME = 16;
	public static final int CAP_OWN_POWER_GROUP = 32;
	public static final int CAP_TASKS = 64;
	public static final int CAP_FOCUS = 128;

	public enum Status {
		OK,
		WARN,
		FAIL,
		NA
	}

	public static final class Check {
		public final String id;
		public final Status status;
		public final int summaryRes;
		public final int fixRes;

		public Check(String id, Status status, int summaryRes, int fixRes) {
			this.id = id;
			this.status = status;
			this.summaryRes = summaryRes;
			this.fixRes = fixRes;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Check)) return false;
			Check check = (Check) o;
			return summaryRes == check.summaryRes && fixRes == check.fixRes && id.equals(check.id) && status == check.status;
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, status, summaryRes, fixRes);
		}

		@Override
		public String toString() {
			return "Check{" + id + ", " + status + ", summaryRes=" + summaryRes + ", fixRes=" + fixRes + "}";
		}
	}

	private CarAppsChecks() {
	}

	public static List<Check> from(RootState root, int capabilities, int sdk) {
		List<Check> list = new ArrayList<>(7);

		// 1. Channel (root/shell)
		if (root == RootState.GRANTED) {
			list.add(new Check("channel", Status.OK, R.string.car_apps_check_channel_root, 0));
		} else if (root == RootState.AVAILABLE) {
			list.add(new Check("channel", Status.WARN, R.string.car_apps_check_channel_available,
					R.string.car_apps_check_channel_grant_fix));
		} else {
			list.add(new Check("channel", Status.FAIL, R.string.car_apps_check_channel_absent,
					R.string.car_apps_check_channel_root_fix));
		}

		// 2. Trusted display
		if (sdk < 30) { // DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED first appears in android11-release
			list.add(new Check("trusted", Status.NA, R.string.car_apps_check_trusted_na, 0));
		} else if ((capabilities & CAP_TRUSTED) != 0) {
			list.add(new Check("trusted", Status.OK, R.string.car_apps_check_trusted_ok, 0));
		} else {
			list.add(new Check("trusted", Status.WARN, R.string.car_apps_check_trusted_missing,
					R.string.car_apps_check_trusted_fix));
		}

		// 3. Inject touch
		if ((capabilities & CAP_INJECT) != 0) {
			list.add(new Check("inject", Status.OK, R.string.car_apps_check_inject_ok, 0));
		} else {
			list.add(new Check("inject", Status.FAIL, R.string.car_apps_check_inject_missing,
					R.string.car_apps_check_inject_fix));
		}

		// 4. IME keyboard policy
		if ((capabilities & CAP_IME) != 0) {
			list.add(new Check("ime", Status.OK, R.string.car_apps_check_ime_ok, 0));
		} else {
			list.add(new Check("ime", Status.WARN, R.string.car_apps_check_ime_missing,
					R.string.car_apps_check_ime_fix));
		}

		// 5. Tasks
		if ((capabilities & CAP_TASKS) != 0) {
			list.add(new Check("tasks", Status.OK, R.string.car_apps_check_tasks_ok, 0));
		} else {
			list.add(new Check("tasks", Status.WARN, R.string.car_apps_check_tasks_missing,
					R.string.car_apps_check_tasks_fix));
		}

		// 6. Focus
		if (sdk >= 34) {
			if ((capabilities & CAP_FOCUS) != 0) {
				list.add(new Check("focus", Status.OK, R.string.car_apps_check_focus_ok, 0));
			} else {
				list.add(new Check("focus", Status.WARN, R.string.car_apps_check_focus_missing,
						R.string.car_apps_check_focus_fix));
			}
		} else {
			list.add(new Check("focus", Status.NA, R.string.car_apps_check_focus_na, 0));
		}

		// 7. Own power group (informational)
		if ((capabilities & CAP_OWN_POWER_GROUP) != 0) {
			list.add(new Check("own_power_group", Status.OK, R.string.car_apps_check_power_group_own, 0));
		} else {
			list.add(new Check("own_power_group", Status.OK, R.string.car_apps_check_power_group_shared, 0));
		}

		return Collections.unmodifiableList(list);
	}
}
