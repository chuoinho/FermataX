package me.aap.fermata.ui.policy;

/** Separates an AA host return from an explicit navigation relaunch. */
public final class HostRelaunchPolicy {
	private static final String ACTION_MAIN = "android.intent.action.MAIN";
	/** Sent by the Android Auto media widget: it asks to show what is already playing. */
	private static final String ACTION_SHOW_PLAYBACK = "MEDIA_SHOW_PLAYBACK_VIEW";

	private HostRelaunchPolicy() {
	}

	public static boolean startsNewNavigation(String action, boolean hasData) {
		return hasData || ((action != null) && !ACTION_MAIN.equals(action) &&
				!ACTION_SHOW_PLAYBACK.equals(action));
	}
}
