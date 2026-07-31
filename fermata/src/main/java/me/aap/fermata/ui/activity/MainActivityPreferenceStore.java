package me.aap.fermata.ui.activity;

import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CLOCK_POS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CLOCK_POS_RIGHT;
import static me.aap.fermata.ui.activity.MainActivityPrefs.TEXT_ICON_SIZE;
import static me.aap.fermata.ui.activity.MainActivityPrefs.TEXT_ICON_SIZE_AA;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_AA;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_DARK;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_MAIN;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.action.Action;
import me.aap.fermata.action.Key;
import me.aap.utils.app.App;
import me.aap.utils.pref.PreferenceStore;

final class MainActivityPreferenceStore implements MainActivityPrefs {
	static final MainActivityPreferenceStore INSTANCE = new MainActivityPreferenceStore();
	private final List<ListenerRef<PreferenceStore.Listener>> listeners = new LinkedList<>();
	private final SharedPreferences prefs =
			FermataApplication.get().getDefaultSharedPreferences();

	private MainActivityPreferenceStore() {
		App.get().getHandler().post(this::migratePrefs);
	}

	private void migratePrefs() {
		var oldTheme = Pref.i("THEME", THEME_DARK);
		var oldScale = Pref.f("MEDIA_ITEM_SCALE", 1f);
		var fbLongPress = Pref.i("FB_LONG_PRESS", 0);
		var fbLongPressAA = Pref.i("FB_LONG_PRESS_AA", 0);
		var showClock = Pref.b("SHOW_CLOCK", false);
		var voiceCtrlM = Pref.b("VOICE_CONTROl_M", false);
		var theme = getIntPref(oldTheme);
		var scale = getFloatPref(oldScale);

		if ((theme != THEME_DARK) || (scale != 1f)) {
			try (PreferenceStore.Edit e = editPreferenceStore()) {
				if (theme != THEME_DARK) {
					e.setIntPref(THEME_MAIN, theme);
					if (AUTO) e.setIntPref(THEME_AA, theme);
					e.removePref(oldTheme);
				}
				if (scale != 1f) {
					e.setFloatPref(TEXT_ICON_SIZE, scale);
					if (AUTO) e.setFloatPref(TEXT_ICON_SIZE_AA, scale);
					e.removePref(oldScale);
				}
			}
		}

		if ((getIntPref(fbLongPress) == 1) || (getIntPref(fbLongPressAA) == 1)) {
			try (PreferenceStore.Edit e = editPreferenceStore()) {
				e.setBooleanPref(VOICE_CONTROl_ENABLED, true);
				e.setBooleanPref(VOICE_CONTROl_FB, true);
			}
		}

		if (getBooleanPref(showClock)) {
			try (PreferenceStore.Edit e = editPreferenceStore()) {
				e.removePref(showClock);
				e.setIntPref(CLOCK_POS, CLOCK_POS_RIGHT);
			}
		}

		if (getBooleanPref(voiceCtrlM)) {
			removePref(voiceCtrlM);
			var keyPrefs = Key.getPrefs();
			var action = Action.ACTIVATE_VOICE_CTRL.ordinal();
			keyPrefs.applyIntPref(Key.M.getLongActionPref(), action);
			keyPrefs.applyIntPref(Key.MENU.getLongActionPref(), action);
		}
	}

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return prefs;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners;
	}
}
