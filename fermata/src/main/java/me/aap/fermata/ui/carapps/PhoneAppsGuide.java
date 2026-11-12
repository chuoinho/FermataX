package me.aap.fermata.ui.carapps;

import android.content.Context;

import androidx.annotation.Nullable;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

/** Which Phone Apps guide to show and when; the guide screens themselves are in the Auto flavor (backend). */
public final class PhoneAppsGuide {
	/** Set when the guide was shown the first time the phone opened Phone Apps. */
	public static final Pref<BooleanSupplier> PREF_SEEN = Pref.b("CAR_APPS_GUIDE_SEEN", false);

	public enum Page {SETUP, SETUP_THEN_USAGE, SETUP_AGAIN, USAGE}

	/** How the server is started: root, the phone's own wireless debugging, or not at all here. */
	public enum Channel {ROOT, ADB, NONE}

	public enum StepState {DONE, CURRENT, TODO}

	/** The way the user picks at the start of the setup: root or without root. */
	public enum Mode {ROOT, NO_ROOT}

	/** The picked Mode's ordinal; absent until the user picks one. */
	public static final Pref<IntSupplier> PREF_MODE = Pref.i("CAR_APPS_MODE", -1);

	/** What the setup page shows. */
	public enum SetupStep {CHOOSE, ROOT, ADB, NO_ROOT_UNSUPPORTED, NONE}

	private PhoneAppsGuide() {
	}

	@Nullable
	public static Mode mode(PreferenceStore store) {
		int m = store.getIntPref(PREF_MODE);
		return ((m >= 0) && (m < Mode.values().length)) ? Mode.values()[m] : null;
	}

	/** A way already set up wins; otherwise the user's pick, and the pick itself while there is none. */
	public static SetupStep setupStep(@Nullable RootState root, boolean adbPaired, int sdk, @Nullable Mode mode) {
		if ((root == null) || (sdk < 30) || CarAppsFeature.isBlockedSdk(sdk)) return SetupStep.NONE;
		if (root == RootState.GRANTED) return SetupStep.ROOT;
		if (adbPaired && CarAppsFeature.adbSupportedSdk(sdk)) return SetupStep.ADB;
		if (mode == null) return SetupStep.CHOOSE;
		if (mode == Mode.ROOT) return SetupStep.ROOT;
		return CarAppsFeature.adbSupportedSdk(sdk) ? SetupStep.ADB : SetupStep.NO_ROOT_UNSUPPORTED;
	}

	/**
	 * Stores the pick. Without root on Android 11-12 there is no way to run, so the addon is turned off: it stays in
	 * Settings > Addons to be turned on again once the phone is rooted. Any other pick turns it on.
	 */
	public static void choose(PreferenceStore store, Mode mode, int sdk) {
		store.applyIntPref(PREF_MODE, mode.ordinal());
		CarAppsFeature.setEnabled(store, (mode == Mode.ROOT) || CarAppsFeature.adbSupportedSdk(sdk));
	}

	/** Root once granted; root merely offered leads to it only where there is no setup without root (11-12). */
	public static Channel channel(@Nullable RootState root, int sdk) {
		if ((root == null) || (sdk < 30) || CarAppsFeature.isBlockedSdk(sdk)) return Channel.NONE;
		if (root == RootState.GRANTED) return Channel.ROOT;
		if (CarAppsFeature.adbSupportedSdk(sdk)) return Channel.ADB;
		return (root == RootState.AVAILABLE) ? Channel.ROOT : Channel.NONE;
	}

	/** The last step done means all are; otherwise the first open step is the current one. */
	public static StepState[] progress(boolean... done) {
		StepState[] s = new StepState[done.length];
		boolean all = (done.length > 0) && done[done.length - 1];
		int current = -1;
		for (int i = 0; i < done.length; i++) {
			if (all || ((current < 0) && done[i])) {
				s[i] = StepState.DONE;
			} else if (current < 0) {
				current = i;
				s[i] = StepState.CURRENT;
			} else {
				s[i] = StepState.TODO;
			}
		}
		return s;
	}

	/** Once, on the phone, never while the car runs a session, and only where Phone Apps exists and is turned on. */
	public static boolean shouldShowFirstOpen(boolean seen, boolean supported, boolean enabled, boolean phoneHost,
			boolean carActive) {
		return !seen && supported && enabled && phoneHost && !carActive;
	}

	public static Page firstOpenPage(boolean canRun) {
		return canRun ? Page.USAGE : Page.SETUP_THEN_USAGE;
	}

	/** False where there is no Phone Apps backend (Mobile flavor). */
	public static boolean open(@Nullable Context ctx, Page page) {
		CarAppsFeature.Backend b = CarAppsFeature.backend();
		if (b == null) return false;
		b.openGuide(ctx, page);
		return true;
	}
}
