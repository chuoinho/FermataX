package me.aap.fermata.ui.view;

import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Coordinates shell invalidation without allowing one surface to mutate another surface directly.
 * Surface controllers remain the only renderers of their own views.
 */
public final class UiShellController {
	private UiShellController() {
	}

	/** Playback metadata/presentation can affect top-bar title, but not nav selection. */
	public static void onPlaybackPresentationChanged(MainActivityDelegate activity) {
		TopBarController.refresh(activity);
	}

	/** Route changes affect both route chrome and the selected top-level destination. */
	public static void onRouteChanged(MainActivityDelegate activity) {
		TopBarController.refresh(activity);
		NavBarController.refresh(activity);
	}
}
