package me.aap.fermata.ui.policy;

/** Separates an AA host return from an explicit navigation relaunch. */
public final class HostRelaunchPolicy {
	private static final String ACTION_MAIN = "android.intent.action.MAIN";

	private HostRelaunchPolicy() {
	}

	public static boolean startsNewNavigation(String action, boolean hasData) {
		return hasData || ((action != null) && !ACTION_MAIN.equals(action));
	}
}
