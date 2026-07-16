package me.aap.fermata.ui.voice;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/** Keeps toolbar voice visibility consistent across projected and handheld UIs. */
public final class VoiceUiPolicy {
	private VoiceUiPolicy() {
	}

	public static boolean showToolbarButton(MainActivityDelegate activity) {
		return showToolbarButton(activity.isCarActivityNotMirror(),
				activity.getPrefs().getVoiceControlEnabledPref());
	}

	static boolean showToolbarButton(boolean projectedCarActivity, boolean voiceEnabled) {
		return voiceEnabled;
	}
}
