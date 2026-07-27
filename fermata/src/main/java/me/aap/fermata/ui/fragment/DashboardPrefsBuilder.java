package me.aap.fermata.ui.fragment;

import java.util.function.Consumer;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;

final class DashboardPrefsBuilder {
	private DashboardPrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet parent,
						Consumer<PreferenceSet> refresh) {
		PreferenceStore dashboardStore = activity.getPrefs();
		PreferenceSet dashboard = parent.subSet(o -> {
			o.title = R.string.dashboard;
			o.subtitle = R.string.dashboard_order_sub;
			o.icon = R.drawable.launcher;
		});

		dashboard.addButton(o -> {
			o.title = R.string.edit_dashboard;
			o.icon = R.drawable.edit;
			o.onClick = () -> {
				activity.showDashboard();
				activity.post(() -> {
					if (activity.getActiveFragment() instanceof DashboardFragment d) d.enterEditMode();
				});
		};
		});
		dashboard.addButton(o -> {
			o.title = R.string.reset_dashboard_order;
			o.icon = R.drawable.refresh;
			o.onClick = () -> {
				DashboardItems.reset(dashboardStore);
				refresh.accept(dashboard);
			};
		});

	}
}
