package me.aap.fermata.ui.activity;

import me.aap.fermata.R;
import me.aap.fermata.ui.policy.BackNavigationPolicy;

/** Common semantic entry point for top-level navigation intents and route synchronization. */
public final class NavigationCoordinator {
	private NavigationCoordinator() {
	}

	/**
	 * Selects a top-level destination. The active destination is updated before navigation so every
	 * renderer observes the same authoritative state during synchronous fragment callbacks.
	 */
	public static boolean select(MainActivityDelegate activity, int destinationId) {
		if (destinationId == R.id.dashboard_fragment) {
			activity.showDashboard();
			return true;
		}

		int previous = activity.getActiveNavItemId();
		activity.setActiveNavItemId(destinationId);
		if (activity.showFragmentWhenReady(destinationId)) return true;
		activity.setActiveNavItemId(previous);
		return false;
	}

	/**
	 * Synchronizes authoritative destination state when a route was opened programmatically rather
	 * than through a nav click. Non-nav pages intentionally preserve the previously selected
	 * top-level destination so Back can return to it.
	 */
	public static void routeChanged(MainActivityDelegate activity, int routeId,
			boolean topLevelDestination) {
		int current = activity.getActiveNavItemId();
		int selected = resolveRouteSelection(current, routeId, topLevelDestination);
		if (selected != current) activity.setActiveNavItemId(selected);
	}

	static int resolveRouteSelection(int currentSelection, int routeId,
			boolean topLevelDestination) {
		return topLevelDestination ? routeId : currentSelection;
	}

	/** Returns true when the common navigation policy consumed a destination reselection. */
	public static boolean reselect(MainActivityDelegate activity, int destinationId) {
		return BackNavigationPolicy.handleNavReselection(activity, destinationId);
	}
}
