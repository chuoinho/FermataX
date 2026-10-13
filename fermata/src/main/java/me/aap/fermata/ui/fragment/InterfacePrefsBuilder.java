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

		if (activity.isCarActivity()) {
			normalizeThemeAA(store);
			set.addListPref(o -> {
				o.store = store;
				o.pref = MainActivityPrefs.THEME_AA;
				o.title = R.string.theme;
				o.subtitle = R.string.theme_sub;
				o.formatSubtitle = true;
				o.values = new int[]{R.string.theme_classic, R.string.theme_material_you, R.string.theme_glass};
				o.valuesMap = new int[]{MainActivityPrefs.THEME_CLASSIC, MainActivityPrefs.THEME_MATERIAL_YOU, MainActivityPrefs.THEME_GLASS};
			});
		} else {
			normalizeTheme(store);
			set.addListPref(o -> {
				o.store = store;
				o.pref = MainActivityPrefs.THEME_MAIN;
				o.title = R.string.theme;
				o.subtitle = R.string.theme_sub;
				o.formatSubtitle = true;
				o.values = new int[]{R.string.theme_classic, R.string.theme_material_you, R.string.theme_glass};
				o.valuesMap = new int[]{MainActivityPrefs.THEME_CLASSIC, MainActivityPrefs.THEME_MATERIAL_YOU, MainActivityPrefs.THEME_GLASS};
			});

			if (BuildConfig.AUTO) {
				normalizeThemeAA(store);
				set.addListPref(o -> {
					o.store = store;
					o.pref = MainActivityPrefs.THEME_AA;
					o.title = R.string.theme_aa;
					o.subtitle = R.string.theme_aa_sub;
					o.formatSubtitle = true;
					o.values = new int[]{R.string.theme_classic, R.string.theme_material_you, R.string.theme_glass};
					o.valuesMap = new int[]{MainActivityPrefs.THEME_CLASSIC, MainActivityPrefs.THEME_MATERIAL_YOU, MainActivityPrefs.THEME_GLASS};
				});
			}
		}

		normalizeGlassAppearance(store);
		set.addListPref(o -> {
			o.store = store;
			o.pref = MainActivityPrefs.GLASS_APPEARANCE;
			o.title = R.string.glass_appearance;
			o.subtitle = R.string.glass_appearance_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.glass_appearance_day, R.string.glass_appearance_night};
			o.valuesMap = new int[]{MainActivityPrefs.GLASS_APPEARANCE_DAY, MainActivityPrefs.GLASS_APPEARANCE_NIGHT};
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

		normalizeLocale(store);
		set.addListPref(o -> {
			o.store = store;
			o.pref = MainActivityPrefs.LOCALE;
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.removeDefault = false;
			o.cmp = String::compareTo;
			var langs = MainActivityPrefs.Lang.getValues();
			o.stringValues = CollectionUtils.mapToArray(langs,
					v -> v.locale.getDisplayName(), String[]::new);
			o.valuesMap = langs.stream().mapToInt(Enum::ordinal).toArray();
		});

		if (!activity.isCarActivityNotMirror()) {
			set.addButton(o -> {
				o.title = R.string.initial_setup_reopen;
				o.subtitle = R.string.initial_setup_reopen_sub;
				o.onClick = () -> activity.showFragment(R.id.initial_setup_fragment);
			});
		}
	}

	private static void normalizeTheme(PreferenceStore store) {
		int theme = store.getIntPref(MainActivityPrefs.THEME_MAIN);
		if (theme != MainActivityPrefs.THEME_CLASSIC &&
				theme != MainActivityPrefs.THEME_MATERIAL_YOU &&
				theme != MainActivityPrefs.THEME_GLASS) {
			store.applyIntPref(MainActivityPrefs.THEME_MAIN, MainActivityPrefs.THEME_CLASSIC);
		}
	}

	private static void normalizeThemeAA(PreferenceStore store) {
		int theme = store.getIntPref(MainActivityPrefs.THEME_AA);
		if (theme != MainActivityPrefs.THEME_CLASSIC &&
				theme != MainActivityPrefs.THEME_MATERIAL_YOU &&
				theme != MainActivityPrefs.THEME_GLASS) {
			store.applyIntPref(MainActivityPrefs.THEME_AA, MainActivityPrefs.THEME_CLASSIC);
		}
	}

	private static void normalizeGlassAppearance(PreferenceStore store) {
		int app = store.getIntPref(MainActivityPrefs.GLASS_APPEARANCE);
		if (app != MainActivityPrefs.GLASS_APPEARANCE_DAY && app != MainActivityPrefs.GLASS_APPEARANCE_NIGHT) {
			store.applyIntPref(MainActivityPrefs.GLASS_APPEARANCE, MainActivityPrefs.GLASS_APPEARANCE_NIGHT);
		}
	}

	private static void normalizeNavBarPosition(PreferenceStore store, Pref<IntSupplier> pref) {
		int position = store.getIntPref(pref);
		int normalized = MainActivityPrefs.normalizeNavBarPosition(position);
		if (normalized != position) store.applyIntPref(pref, normalized);
	}

	private static void normalizeLocale(PreferenceStore store) {
		int pref = store.getIntPref(MainActivityPrefs.LOCALE);
		MainActivityPrefs.Lang lang = MainActivityPrefs.Lang.get(pref);
		if (lang.ordinal() != pref) store.applyIntPref(MainActivityPrefs.LOCALE, lang.ordinal());
	}
}
