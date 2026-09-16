package me.aap.fermata.addon.web.yt;

import me.aap.fermata.addon.web.R;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;

final class YoutubePlayerPreferences {
	static final PreferenceStore.Pref<BooleanSupplier> AUTO_FULLSCREEN =
			PreferenceStore.Pref.b("YT_AUTO_FULLSCREEN", true);

	private YoutubePlayerPreferences() {
	}

	static boolean getAutoFullscreen(PreferenceStore store) {
		return store.getBooleanPref(AUTO_FULLSCREEN);
	}

	static void setAutoFullscreen(PreferenceStore store, boolean enabled) {
		store.applyBooleanPref(AUTO_FULLSCREEN, enabled);
	}

	static void contributeAutoFullscreenSetting(PreferenceStore store, PreferenceSet settings,
			ChangeableCondition visibility) {
		settings.addBooleanPref(options -> {
			options.store = store;
			options.pref = AUTO_FULLSCREEN;
			options.title = R.string.yt_auto_fullscreen;
			options.visibility = visibility;
		});
	}
}
