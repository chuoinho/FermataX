package me.aap.fermata.media.pref;

import androidx.annotation.NonNull;

import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;

public interface RecentPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String[]>> RECENT = Pref.sa("RECENT", new String[0]);

	@NonNull
	PreferenceStore getRecentPreferenceStore();

	default String[] getRecentPref() {
		return getRecentPreferenceStore().getStringArrayPref(RECENT);
	}

	default void setRecentPref(String[] recent) {
		getRecentPreferenceStore().applyStringArrayPref(RECENT, recent);
	}
}
