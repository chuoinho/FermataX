package me.aap.fermata.ui.fragment;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.view.NavBarView;

final class InterfacePrefsBuilder {
	private InterfacePrefsBuilder() {
	}

	static void addAndroidAuto(MainActivityDelegate activity, PreferenceSet set) {
		if (!BuildConfig.AUTO) return;
		add(activity, set, MainActivityPrefs.THEME_AA, MainActivityPrefs.HIDE_BARS_AA,
				MainActivityPrefs.FULLSCREEN_AA, MainActivityPrefs.SHOW_PG_UP_DOWN_AA,
				MainActivityPrefs.USE_DPAD_CURSOR, MainActivityPrefs.NAV_BAR_POS_AA,
				MainActivityPrefs.NAV_BAR_SIZE_AA, MainActivityPrefs.TOOL_BAR_SIZE_AA,
				MainActivityPrefs.CONTROL_PANEL_SIZE_AA, MainActivityPrefs.TEXT_ICON_SIZE_AA);
	}

	static void add(MainActivityDelegate activity, PreferenceSet set, Pref<IntSupplier> theme,
						Pref<BooleanSupplier> hideBars, Pref<BooleanSupplier> fullScreen,
						Pref<BooleanSupplier> pgUpDown, Pref<BooleanSupplier> dpadCursor,
						Pref<IntSupplier> navBarPosition, Pref<DoubleSupplier> navBarSize,
						Pref<DoubleSupplier> toolBarSize, Pref<DoubleSupplier> controlPanelSize,
						Pref<DoubleSupplier> textIconSize) {
		PreferenceStore store = activity.getPrefs();
		normalizeNavBarPosition(store, navBarPosition);
		set.addListPref(o -> {
			o.store = store;
			o.pref = theme;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_dark, R.string.theme_light, R.string.theme_system,
					R.string.theme_black, R.string.theme_star_wars, R.string.theme_purple,
					R.string.theme_classic};
		});
		set.addBooleanPref(o -> {
			o.store = store;
			o.pref = hideBars;
			o.title = R.string.hide_bars;
			o.subtitle = R.string.hide_bars_sub;
		});
		set.addBooleanPref(o -> {
			o.store = store;
			o.pref = fullScreen;
			o.title = R.string.fullscreen_mode;
		});
		set.addBooleanPref(o -> {
			o.store = store;
			o.pref = pgUpDown;
			o.title = R.string.show_pg_up_down;
		});
		if (dpadCursor != null) {
			set.addBooleanPref(o -> {
				o.store = store;
				o.pref = dpadCursor;
				o.title = R.string.use_dpad_cursor;
			});
		}
		set.addListPref(o -> {
			o.store = store;
			o.pref = navBarPosition;
			o.title = R.string.nav_bar_pos;
			o.subtitle = R.string.nav_bar_pos_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.nav_bar_pos_left, R.string.nav_bar_pos_right};
			o.valuesMap = new int[]{NavBarView.POSITION_LEFT, NavBarView.POSITION_RIGHT};
		});
		addSize(set, store, navBarSize, R.string.nav_bar_size);
		addSize(set, store, toolBarSize, R.string.tool_bar_size);
		addSize(set, store, controlPanelSize, R.string.control_panel_size);
		addSize(set, store, textIconSize, R.string.text_icon_size);
	}

	private static void addSize(PreferenceSet set, PreferenceStore store,
										Pref<DoubleSupplier> pref, int title) {
		set.addFloatPref(o -> {
			o.store = store;
			o.pref = pref;
			o.title = title;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
		});
	}

	private static void normalizeNavBarPosition(PreferenceStore store, Pref<IntSupplier> pref) {
		int position = store.getIntPref(pref);
		if ((position == NavBarView.POSITION_LEFT) || (position == NavBarView.POSITION_RIGHT)) return;
		store.applyIntPref(pref, NavBarView.POSITION_LEFT);
	}
}
