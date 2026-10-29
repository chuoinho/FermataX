package me.aap.fermata.ui.carapps;

import android.os.Build;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.aap.fermata.BuildConfig;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * Feature gate and state coordinator for Car Apps on Android Auto.
 */
public final class CarAppsFeature {

	public interface Backend {
		FutureSupplier<Boolean> requestRoot();

		ActivityFragment createFragment();
	}

	private static volatile RootState state = RootState.ABSENT;
	private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private static volatile Backend backend;

	private CarAppsFeature() {
	}

	public static boolean visible(boolean autoBuild, int sdk, @Nullable RootState s) {
		return autoBuild && (sdk >= 33) && (s != null) && (s != RootState.ABSENT);
	}

	public static boolean usable(@Nullable RootState s) {
		return s == RootState.GRANTED;
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
}
