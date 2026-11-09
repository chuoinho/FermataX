package me.aap.fermata.ui.carapps;

import android.os.Build;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.aap.fermata.BuildConfig;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Feature gate and state coordinator for Car Apps on Android Auto.
 */
public final class CarAppsFeature {

	public interface Backend {
		FutureSupplier<Boolean> requestRoot();

		ActivityFragment createFragment();

		default int capabilities() {
			return CarAppsReadinessModel.CAP_INJECT;
		}
	}

	/** Turn the phone panel off while the virtual display is in use (user option, default on). */

	private static volatile RootState state = RootState.ABSENT;
	private static volatile boolean adbPaired;
	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static volatile Backend backend;
	private static volatile boolean fullscreen;

	private CarAppsFeature() {
	}

	public static boolean visible(boolean autoBuild, int sdk, @Nullable RootState s) {
		return autoBuild && (sdk >= 30) && !isBlockedSdk(sdk) && (s != null)
				&& ((s != RootState.ABSENT) || adbSupportedSdk(sdk));
	}

	/** 12L: as uid 1000, which the server needs below 33, am start exits 255, so no app can open. */
	public static boolean isBlockedSdk(int sdk) {
		return sdk == 32;
	}

	/** Android 11–12 need root: the system blocks local IME on untrusted virtual displays. */
	public static boolean adbSupportedSdk(int sdk) {
		return sdk >= 33;
	}

	public static boolean usable(@Nullable RootState s) {
		return s == RootState.GRANTED;
	}

	/** True when the server can be started: by root, or by the paired ADB channel on a supported Android. */
	public static boolean canRun() {
		return canRun(state, adbPaired, Build.VERSION.SDK_INT);
	}

	public static boolean canRun(@Nullable RootState root, boolean adbPaired, int sdk) {
		return usable(root) || (adbPaired && adbSupportedSdk(sdk));
	}

	public static boolean isAdbPaired() {
		return adbPaired;
	}

	public static void setAdbPaired(boolean paired) {
		if (adbPaired == paired) {
			return;
		}
		adbPaired = paired;
		notifyListeners();
	}

	/** True while the virtual display owns the screen; the top bar is hidden meanwhile. */
	public static boolean isFullscreen() {
		return fullscreen;
	}

	public static void setFullscreen(boolean value) {
		fullscreen = value;
	}

	public static boolean isVisible() {
		return visible(BuildConfig.AUTO, Build.VERSION.SDK_INT, state());
	}

	public static RootState state() {
		return state;
	}

	public static void setState(@Nullable RootState newState) {
		if (newState == null) {
			newState = RootState.ABSENT;
		}
		if (state == newState) {
			return;
		}
		state = newState;
		notifyListeners();
	}

	private static void notifyListeners() {
		for (Runnable listener : listeners) {
			listener.run();
		}
	}

	public static void addListener(Runnable listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public static void removeListener(Runnable listener) {
		if (listener != null) {
			listeners.remove(listener);
		}
	}

	public static void setBackend(@Nullable Backend b) {
		backend = b;
	}

	@Nullable
	public static Backend backend() {
		return backend;
	}

	public static int capabilities() {
		Backend b = backend;
		if (b != null) {
			return b.capabilities();
		}
		return CarAppsReadinessModel.CAP_INJECT;
	}
}
