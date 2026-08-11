package me.aap.fermata.ui.voice;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.policy.RuntimeHostMode;

/** Keeps a single global voice entry in the shared navigation rail. */
public final class VoiceUiPolicy {
	private VoiceUiPolicy() {
	}

	public static boolean showToolbarButton(MainActivityDelegate activity) {
		return showToolbarButton(activity.getRuntimeHostMode(),
				activity.getPrefs().getVoiceControlEnabledPref());
	}

	public static boolean showNavBarButton(MainActivityDelegate activity) {
		return showNavBarButton(activity.getRuntimeHostMode(),
				activity.getPrefs().getVoiceControlEnabledPref());
	}

	static boolean showToolbarButton(RuntimeHostMode hostMode, boolean voiceEnabled) {
		return false;
	}

	static boolean showNavBarButton(RuntimeHostMode hostMode, boolean voiceEnabled) {
		return voiceEnabled;
	}
}
