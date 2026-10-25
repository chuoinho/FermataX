package me.aap.fermata.ui.policy;

import android.app.ActivityManager;
import android.content.Context;
import android.os.PowerManager;

/** Reads Android's background run restrictions and stores the related one-time choices. */
public final class BackgroundRunState {
	private static final String PREFS = "background_run";
	private static final String INITIAL_WARNING_ASKED = "initial_warning_asked";
	private static final String VENDOR_GUIDE_DONE = "vendor_guide_done";

	private BackgroundRunState() {
	}

	public static boolean isUnrestricted(Context context) {
		boolean ignoringBatteryOptimizations = false;
		boolean backgroundRestricted = false;
		try {
			PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			ignoringBatteryOptimizations = (power != null)
					&& power.isIgnoringBatteryOptimizations(context.getPackageName());
		} catch (RuntimeException ignored) {
			// Unknown state is treated as restricted so the user can review the setting.
		}
		try {
			ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			backgroundRestricted = (manager == null) || manager.isBackgroundRestricted();
		} catch (RuntimeException ignored) {
			backgroundRestricted = true;
		}
		return BackgroundRunPolicy.isUnrestricted(ignoringBatteryOptimizations, backgroundRestricted);
	}

	public static boolean wasInitialWarningAsked(Context context) {
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.getBoolean(INITIAL_WARNING_ASKED, false);
	}

	public static void markInitialWarningAsked(Context context) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
				.putBoolean(INITIAL_WARNING_ASKED, true).apply();
	}

	public static boolean isVendorGuideDone(Context context) {
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
				.getBoolean(VENDOR_GUIDE_DONE, false);
	}

	public static void setVendorGuideDone(Context context, boolean done) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
				.putBoolean(VENDOR_GUIDE_DONE, done).apply();
	}
}
