package me.aap.fermata.ui.fragment;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.view.NavBarView;

final class InterfacePrefsBuilder {
	private InterfacePrefsBuilder() {
	}

	static void addAndroidAuto(MainActivityDelegate activity, PreferenceSet set) {
		add(activity, set);
	}

	static void add(MainActivityDelegate activity, PreferenceSet set) {
		PreferenceStore store = activity.getPrefs();

		normalizeTheme(store);
		set.addListPref(o -> {
			o.store = store;
			o.pref = MainActivityPrefs.THEME_MAIN;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_classic, R.string.theme_material_you};
			o.valuesMap = new int[]{MainActivityPrefs.THEME_CLASSIC, MainActivityPrefs.THEME_MATERIAL_YOU};
		});

		Pref<IntSupplier> navBarPosition = BuildConfig.AUTO ?
				MainActivityPrefs.NAV_BAR_POS_AA : MainActivityPrefs.NAV_BAR_POS;
		normalizeNavBarPosition(store, navBarPosition);
		set.addListPref(o -> {
			o.store = store;
			o.pref = navBarPosition;
			o.title = R.string.nav_bar_pos;
			o.subtitle = R.string.nav_bar_pos_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.nav_bar_pos_left, R.string.nav_bar_pos_right};
			o.valuesMap = new int[]{NavBarView.POSITION_LEFT, NavBarView.POSITION_RIGHT};
		});

		set.addListPref(o -> {
			o.store = store;
			o.pref = MainActivityPrefs.LOCALE;
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.removeDefault = false;
			o.cmp = String::compareTo;
			o.stringValues = CollectionUtils.mapToArray(MainActivityPrefs.Lang.getValues(),
					v -> v.locale.getDisplayName(), String[]::new);
		});
	}

	private static void normalizeTheme(PreferenceStore store) {
		int theme = store.getIntPref(MainActivityPrefs.THEME_MAIN);
		if (theme != MainActivityPrefs.THEME_CLASSIC && theme != MainActivityPrefs.THEME_MATERIAL_YOU) {
			store.applyIntPref(MainActivityPrefs.THEME_MAIN, MainActivityPrefs.THEME_CLASSIC);
		}
	}

	private static void normalizeNavBarPosition(PreferenceStore store, Pref<IntSupplier> pref) {
		int position = store.getIntPref(pref);
		int normalized = MainActivityPrefs.normalizeNavBarPosition(position);
		if (normalized != position) store.applyIntPref(pref, normalized);
	}
}
