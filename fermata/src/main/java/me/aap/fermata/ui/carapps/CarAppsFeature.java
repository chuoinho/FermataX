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
	public static final String PREF_PANEL_OFF = "CAR_APPS_PANEL_OFF";
	public static final Pref<BooleanSupplier> PANEL_OFF = Pref.b(PREF_PANEL_OFF, true);

	private static volatile RootState state = RootState.ABSENT;
	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static volatile Backend backend;
	private static volatile boolean fullscreen;

	private CarAppsFeature() {
	}

	public static boolean visible(boolean autoBuild, int sdk, @Nullable RootState s) {
		return autoBuild && (sdk >= 30) && !isBlockedSdk(sdk) && (s != null) && (s != RootState.ABSENT);
	}

	/** 12L: as uid 1000, which the server needs below 33, am start exits 255, so no app can open. */
	public static boolean isBlockedSdk(int sdk) {
		return sdk == 32;
	}

	public static boolean usable(@Nullable RootState s) {
		return s == RootState.GRANTED;
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
