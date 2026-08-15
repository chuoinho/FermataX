package me.aap.fermata.ui.view;

import android.view.View;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.view.NavBarView;

/** Single renderer for the selected top-level navigation destination. */
public final class NavBarController {
	private NavBarController() {
	}

	public static void refresh(MainActivityDelegate activity) {
		NavBarView navBar = activity.getNavBar();
		if (navBar == null) return;
		applySelection(navBar, activity.getActiveNavItemId());
	}

	public static void applySelection(NavBarView navBar, int selectedId) {
		if (navBar instanceof FermataNavBarView rail) {
			rail.forEachNavigationItem(child -> {
				if (child == null) return;
				child.setSelected((child.getId() != R.id.nav_voice) &&
						(child.getId() == selectedId));
			});
			return;
		}

		for (int i = 0, count = navBar.getChildCount(); i < count; i++) {
			View child = navBar.getChildAt(i);
			child.setSelected((child != null) && (child.getId() == selectedId));
		}
	}
}
