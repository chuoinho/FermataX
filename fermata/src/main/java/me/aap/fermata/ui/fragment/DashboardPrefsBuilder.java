package me.aap.fermata.ui.fragment;

import android.content.Context;

import java.util.function.Consumer;

import me.aap.fermata.FermataApplication;
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
		PreferenceStore addonStore = FermataApplication.get().getPreferenceStore();
		PreferenceSet dashboard = parent.subSet(o -> {
			o.title = R.string.dashboard;
			o.subtitle = R.string.dashboard_order_sub;
			o.icon = R.drawable.launcher;
		});

		dashboard.addButton(o -> {
			o.title = R.string.open_dashboard;
			o.icon = R.drawable.launcher;
			o.onClick = activity::showDashboard;
		});
		dashboard.addButton(o -> {
			o.title = R.string.reset_dashboard_order;
			o.icon = R.drawable.refresh;
			o.onClick = () -> {
				DashboardItems.reset(dashboardStore);
				refresh.accept(dashboard);
			};
		});

		Context itemContext = activity.getLocalizedContext(activity.getContext());
		for (DashboardItems.Item item : DashboardItems.getConfigItems(itemContext, dashboardStore)) {
			if (item.addonInfo == null) continue;
			PreferenceSet itemSet = dashboard.subSet(o -> {
				o.ctitle = item.title;
				o.icon = item.icon;
			});

			itemSet.addBooleanPref(o -> {
				o.title = R.string.enable;
				o.pref = item.addonInfo.enabledPref;
				o.store = addonStore;
			});

		}
	}
}
