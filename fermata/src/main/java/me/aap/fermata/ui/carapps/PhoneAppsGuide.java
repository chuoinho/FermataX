package me.aap.fermata.ui.carapps;

import android.content.Context;

import androidx.annotation.Nullable;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;

/** Which Phone Apps guide to show and when; the guide screens themselves are in the Auto flavor (backend). */
public final class PhoneAppsGuide {
	/** Set when the guide was shown the first time the phone opened Phone Apps. */
	public static final Pref<BooleanSupplier> PREF_SEEN = Pref.b("CAR_APPS_GUIDE_SEEN", false);

	public enum Page {SETUP, SETUP_THEN_USAGE, SETUP_AGAIN, USAGE}

	/** How the server is started: root, the phone's own wireless debugging, or not at all here. */
	public enum Channel {ROOT, ADB, NONE}

	public enum StepState {DONE, CURRENT, TODO}

	private PhoneAppsGuide() {
	}

	public static Channel channel(@Nullable RootState root, int sdk) {
		if ((root == null) || (sdk < 30) || CarAppsFeature.isBlockedSdk(sdk)) return Channel.NONE;
		if ((root == RootState.GRANTED) || (root == RootState.AVAILABLE)) return Channel.ROOT;
		return CarAppsFeature.adbSupportedSdk(sdk) ? Channel.ADB : Channel.NONE;
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

	/** Once, on the phone, never while the car runs a session, and only where Phone Apps exists. */
	public static boolean shouldShowFirstOpen(boolean seen, boolean supported, boolean phoneHost, boolean carActive) {
		return !seen && supported && phoneHost && !carActive;
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
