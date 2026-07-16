package me.aap.fermata.ui.policy;

import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.ToolBarView;

public final class BackNavigationPolicy {
	private BackNavigationPolicy() {
	}

	public static void handlePlayerBack(MainActivityDelegate a) {
		ActivityFragment f = a.getActiveFragment();
		BodyLayout b = a.getBody();
		boolean bothMode = b.isBothMode();
		boolean fragmentEligible = (f != null) && (bothMode || !(f instanceof MediaLibFragment));
		boolean fragmentHandled = fragmentEligible && f.onBackPressed();

		switch (resolvePlayerBack(bothMode, fragmentEligible, fragmentHandled,
				!bothMode && b.isVideoMode())) {
			case REFRESH_CHROME -> {
				ChromePolicy.refreshAutoTopBackButton(a);
			}
			case SHOW_DASHBOARD -> a.showDashboard();
			case LEAVE_VIDEO_MODE -> leaveVideoMode(a);
			case TRY_AUDIO_SOURCE -> {
				if (!PlaybackUiPolicy.goToCurrentAudioSource(a)) a.onBackPressed();
			}
			case HANDLED -> {
			}
		}
	}

	static PlayerBackAction resolvePlayerBack(boolean bothMode, boolean fragmentEligible,
															 boolean fragmentHandled, boolean videoMode) {
		if (bothMode) return fragmentHandled ? PlayerBackAction.REFRESH_CHROME :
				PlayerBackAction.SHOW_DASHBOARD;
		if (fragmentEligible && fragmentHandled) return PlayerBackAction.HANDLED;
		if (videoMode) return PlayerBackAction.LEAVE_VIDEO_MODE;
		return PlayerBackAction.TRY_AUDIO_SOURCE;
	}

	public static boolean leaveVideoMode(MainActivityDelegate a) {
		BodyLayout b = a.getBody();
		if (!b.isVideoMode()) return false;

		b.setMode(BodyLayout.Mode.BOTH);
		if (AUTO) a.setBarsHidden(false);
		if (a.isCarActivity()) {
			a.post(() -> {
				MediaItemListView.focusActive(a.getContext(), null);
				ChromePolicy.refreshAutoTopBackButton(a);
			});
		} else {
			ChromePolicy.refreshAutoTopBackButton(a);
		}
		return true;
	}

	public static void handleAutoActivityBack(MainActivityDelegate a) {
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
																 boolean hasNavFragment, boolean fragmentMatchesNav,
																 boolean dashboardRoot) {
		if (fragmentHandled) return ActivityBackAction.HANDLED;
		if (hasFragment && hasNavFragment && !fragmentMatchesNav)
			return ActivityBackAction.SHOW_NAV_FRAGMENT;
		if (hasFragment && !dashboardRoot) return ActivityBackAction.SHOW_DASHBOARD;
		return ActivityBackAction.FINISH;
	}

	enum PlayerBackAction {
		REFRESH_CHROME, SHOW_DASHBOARD, LEAVE_VIDEO_MODE, TRY_AUDIO_SOURCE, HANDLED
	}

	enum ActivityBackAction {
		SHOW_NAV_FRAGMENT, SHOW_DASHBOARD, FINISH, HANDLED
	}
}
