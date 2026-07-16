package me.aap.fermata.addon;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

final class AddonPreferenceMigrator {
	private static final Pref<BooleanSupplier> ADDONS_ENABLED_BY_DEFAULT =
			Pref.b("ADDONS_ENABLED_BY_DEFAULT", false);
	private static final Pref<BooleanSupplier> ADDONS_ENABLED_BY_DEFAULT_V2 =
			Pref.b("ADDONS_ENABLED_BY_DEFAULT_V2", false);

	private AddonPreferenceMigrator() {
	}

	static void enableDefaults(PreferenceStore store, Iterable<AddonInfo> available) {
		if (store.getBooleanPref(ADDONS_ENABLED_BY_DEFAULT_V2)) return;

		try (PreferenceStore.Edit edit = store.editPreferenceStore(false)) {
			for (AddonInfo info : available) {
				if (!info.enableByDefault || store.hasPref(info.enabledPref, false)) continue;
				edit.setBooleanPref(info.enabledPref, true);
			}

			edit.setBooleanPref(ADDONS_ENABLED_BY_DEFAULT, true);
			edit.setBooleanPref(ADDONS_ENABLED_BY_DEFAULT_V2, true);
		}
	}
}
