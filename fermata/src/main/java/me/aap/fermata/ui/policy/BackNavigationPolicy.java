package me.aap.fermata.ui.policy;

import static me.aap.utils.ui.UiUtils.ID_NULL;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.ToolBarView;

public final class BackNavigationPolicy {
	private BackNavigationPolicy() {
	}

	/**
	 * Automotive/player-bar Back is a secondary rendering of the same Back intent used by the
	 * toolbar, hardware/system Back and every other host. It must not own a second navigation state
	 * machine.
	 */
	public static void handlePlayerBack(MainActivityDelegate a) {
		handleActivityBack(a);
	}

	public static boolean leaveVideoMode(MainActivityDelegate a) {
		BodyLayout b = a.getBody();
		if (!b.isVideoMode()) return false;

		ActivityFragment fragment = a.getActiveFragment();
		boolean splitViewSupported = !(fragment instanceof MainActivityFragment main) ||
				main.isSplitViewSupported();
		b.setMode(resolveVideoExitMode(splitViewSupported));
		if (a.getRuntimeHostMode().usesAutomotivePresentation()) a.setBarsHidden(false);
		if (a.isCarActivity()) {
			a.post(() -> {
				MediaItemListView.focusActive(a.getContext(), null);
				ChromePolicy.refreshTopBackButton(a);
			});
		} else {
			ChromePolicy.refreshTopBackButton(a);
		}
		return true;
	}

	static BodyLayout.Mode resolveVideoExitMode(boolean splitViewSupported) {
		return splitViewSupported ? BodyLayout.Mode.BOTH : BodyLayout.Mode.FRAME;
	}

	/**
	 * Resolves the navigation meaning of selecting the already-active top-level destination.
	 * The nav bar only emits the intent; video-exit/navigation semantics stay centralized here.
	 */
	public static boolean handleNavReselection(MainActivityDelegate a, int navId) {
		NavReselectionAction action = resolveNavReselection(a.getBody().isVideoMode(),
				navId == R.id.dashboard_fragment);
		return switch (action) {
			case LEAVE_VIDEO_MODE -> leaveVideoMode(a);
			case SHOW_DASHBOARD -> {
				a.showDashboard();
				yield true;
			}
			case UNHANDLED -> false;
		};
	}

	static NavReselectionAction resolveNavReselection(boolean videoMode,
			boolean dashboardDestination) {
		if (videoMode) return NavReselectionAction.LEAVE_VIDEO_MODE;
		if (dashboardDestination) return NavReselectionAction.SHOW_DASHBOARD;
		return NavReselectionAction.UNHANDLED;
	}

	public static void handleActivityBack(MainActivityDelegate a) {
		OverlayMenu menu = a.getActiveMenu();
		if (menu != null) {
			if (menu.back()) return;
			else if (a.hideActiveMenu()) return;
		}

		ToolBarView tb = a.getToolBar();
		if ((tb != null) && tb.onBackPressed()) return;

		ActivityFragment f = a.getActiveFragment();
		if ((f != null) && f.onBackPressed()) return;
		int navId = (f == null) ? ID_NULL : a.getActiveNavItemId();
		boolean fragmentMatchesNav = (f != null) && (f.getFragmentId() == navId);
		boolean dashboardRoot = fragmentMatchesNav && (f instanceof DashboardFragment) &&
				(navId == R.id.dashboard_fragment) && f.isRootPage();

		switch (resolveActivityBack(f != null, false, navId != ID_NULL,
				fragmentMatchesNav, dashboardRoot)) {
			case SHOW_NAV_FRAGMENT -> a.showFragment(navId);
			case SHOW_DASHBOARD -> a.showDashboard();
			case FINISH -> a.finish();
			case HANDLED -> {
			}
		}
	}

	static ActivityBackAction resolveActivityBack(boolean hasFragment, boolean fragmentHandled,
			boolean hasNavFragment, boolean fragmentMatchesNav, boolean dashboardRoot) {
		if (fragmentHandled) return ActivityBackAction.HANDLED;
		if (hasFragment && hasNavFragment && !fragmentMatchesNav)
			return ActivityBackAction.SHOW_NAV_FRAGMENT;
		if (hasFragment && !dashboardRoot) return ActivityBackAction.SHOW_DASHBOARD;
		return ActivityBackAction.FINISH;
	}

	enum NavReselectionAction {
		LEAVE_VIDEO_MODE, SHOW_DASHBOARD, UNHANDLED
	}

	enum ActivityBackAction {
		SHOW_NAV_FRAGMENT, SHOW_DASHBOARD, FINISH, HANDLED
	}
}
