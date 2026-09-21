package me.aap.fermata.ui.policy;

import me.aap.fermata.R;

/** Defines the primary navigation root for each runtime presentation host. */
public final class PhoneRootPolicy {
	private PhoneRootPolicy() {
	}

	public static boolean usesPhoneRoots(RuntimeHostMode mode) {
		return mode == RuntimeHostMode.PHONE;
	}

	public static int initialDestination(RuntimeHostMode mode) {
		return R.id.dashboard_fragment;
	}

	public static boolean isPrimaryRoot(RuntimeHostMode mode, int fragmentId) {
		return fragmentId == initialDestination(mode);
	}

	public static boolean isPrimaryRoot(RuntimeHostMode mode, int selectedPhoneRootId,
			int fragmentId) {
		return usesPhoneRoots(mode) ?
				(fragmentId == normalizePhoneRoot(selectedPhoneRootId)) :
				isPrimaryRoot(mode, fragmentId);
	}

	public static boolean isPhoneRoot(int fragmentId) {
		return (fragmentId == R.id.dashboard_fragment) ||
				(fragmentId == R.id.settings_fragment);
	}

	public static int normalizePhoneRoot(int fragmentId) {
		return isPhoneRoot(fragmentId) ? fragmentId : R.id.dashboard_fragment;
	}

	public static int resolvePhoneRoot(int currentRootId, int routeId) {
		return isPhoneRoot(routeId) ? routeId : normalizePhoneRoot(currentRootId);
	}

	public static boolean showAddonNavBar(RuntimeHostMode mode, int phoneRootId,
			boolean barsHidden) {
		return !barsHidden && (!usesPhoneRoots(mode) ||
				(normalizePhoneRoot(phoneRootId) == R.id.dashboard_fragment));
	}

	public static boolean showPhoneBottomMenu(RuntimeHostMode mode, boolean barsHidden) {
		return !barsHidden && usesPhoneRoots(mode);
	}
}
