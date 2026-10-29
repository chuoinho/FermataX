package me.app.fermatax.auto.carapps;

import androidx.annotation.Nullable;

import me.aap.fermata.ui.carapps.RootState;

/**
 * UI presentation state for the Car Apps screen based on root status and display server connection.
 */
public final class CarAppsUiState {

	public enum State {
		LOADING,
		NEEDS_ROOT,
		SERVER_ERROR,
		READY
	}

	private CarAppsUiState() {
	}

	public static State from(@Nullable RootState root, @Nullable CarDisplayClient.State clientState) {
		if (root != RootState.GRANTED) {
			return State.NEEDS_ROOT;
		}
		if (clientState == CarDisplayClient.State.READY) {
			return State.READY;
		}
		if (clientState == CarDisplayClient.State.FAILED) {
			return State.SERVER_ERROR;
		}
		return State.LOADING;
	}
}
