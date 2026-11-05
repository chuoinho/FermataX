package me.app.fermatax.auto.carapps;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;

/**
 * Evaluates whether the CarDisplayServer should auto-start on Android Auto session connection.
 */
public final class AutoStartPolicy {

	public static final String PREF_AUTO_START = "CAR_APPS_AUTOSTART";

	private AutoStartPolicy() {
	}

	public static boolean shouldStart(boolean carServiceActive, RootState s, boolean autoStartPref, int sdk) {
		return carServiceActive && (s == RootState.GRANTED) && autoStartPref && !CarAppsFeature.isBlockedSdk(sdk);
	}
}
