package me.aap.fermata.addon;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;

final class AddonPreferenceMigrator {
	private static final Pref<BooleanSupplier> ADDONS_ENABLED_BY_DEFAULT =
			Pref.b("ADDONS_ENABLED_BY_DEFAULT", false);
	private static final Pref<BooleanSupplier> ADDONS_ENABLED_BY_DEFAULT_V2 =
			Pref.b("ADDONS_ENABLED_BY_DEFAULT_V2", false);
	private static final Pref<LongSupplier> UPDATE_MARKER = Pref.l("CHECK_UPDATES_STAMP", 0);

	private AddonPreferenceMigrator() {
	}

	static boolean enableDefaults(PreferenceStore store, Iterable<AddonInfo> available) {
		boolean freshInstall = isFreshStore(store, available);
		if (store.getBooleanPref(ADDONS_ENABLED_BY_DEFAULT_V2)) return freshInstall;

		try (PreferenceStore.Edit edit = store.editPreferenceStore(false)) {
			for (AddonInfo info : available) {
				if (!info.enableByDefault || store.hasPref(info.enabledPref, false)) continue;
				edit.setBooleanPref(info.enabledPref, true);
			}

			edit.setBooleanPref(ADDONS_ENABLED_BY_DEFAULT, true);
			edit.setBooleanPref(ADDONS_ENABLED_BY_DEFAULT_V2, true);
		}
		return freshInstall;
	}

	private static boolean isFreshStore(PreferenceStore store, Iterable<AddonInfo> available) {
		PreferenceStore root = store.getRootPreferenceStore();
		if (root instanceof SharedPreferenceStore shared)
			return shared.getSharedPreferences().getAll().isEmpty();
		if (store.hasPref(ADDONS_ENABLED_BY_DEFAULT, false) ||
				store.hasPref(ADDONS_ENABLED_BY_DEFAULT_V2, false) ||
				store.hasPref(UPDATE_MARKER, false)) return false;
		for (AddonInfo info : available) {
			if (store.hasPref(info.enabledPref, false)) return false;
		}
		return true;
	}
}
