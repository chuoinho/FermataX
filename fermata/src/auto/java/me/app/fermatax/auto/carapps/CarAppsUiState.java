package me.app.fermatax.auto.carapps;

import android.os.Build;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;

/**
 * UI presentation state for the Car Apps screen based on root status and display server connection.
 */
public final class CarAppsUiState {

	public enum State {
		LOADING,
		NEEDS_ROOT,
		NEEDS_WIFI,
		SERVER_ERROR,
		READY
	}

	private CarAppsUiState() {
	}

	/** Without root the server is started over the phone's wireless debugging, which exists only on Wi-Fi. */
	public static State from(@Nullable RootState root, @Nullable CarDisplayClient.State clientState, boolean wifi) {
		if (!CarAppsFeature.canRun(root, CarAppsFeature.isAdbPaired(), Build.VERSION.SDK_INT)) {
			return State.NEEDS_ROOT;
		}
		if (clientState == CarDisplayClient.State.READY) {
			return State.READY;
		}
		if (!wifi && !CarAppsFeature.usable(root)) {
			return State.NEEDS_WIFI;
		}
		if (clientState == CarDisplayClient.State.FAILED) {
			return State.SERVER_ERROR;
		}
		return State.LOADING;
	}
}
