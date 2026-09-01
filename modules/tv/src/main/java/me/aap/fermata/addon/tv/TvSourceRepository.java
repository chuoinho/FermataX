package me.aap.fermata.addon.tv;

import static me.aap.utils.collection.CollectionUtils.contains;

import me.aap.fermata.addon.tv.xtream.XtreamAccount;
import me.aap.fermata.addon.tv.stalker.StalkerAccount;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

final class TvSourceRepository {
	private static final Pref<IntSupplier> SOURCE_COUNTER =
			Pref.i("SOURCE_COUNTER", 0).withInheritance(false);
	private static final Pref<Supplier<int[]>> SOURCE_IDS =
			Pref.ia("SOURCE_IDS", () -> new int[0]).withInheritance(false);
	private static final String SOURCE_TYPE_PREFIX = "SOURCE_TYPE#";
	private static final String M3U_ID_PREFIX = "M3UID#";
	private final PreferenceStore store;

	TvSourceRepository(PreferenceStore store) {
		this.store = store;
	}

	int[] getSourceIds() {
		return store.getIntArrayPref(SOURCE_IDS);
	}

	boolean hasSource(int sourceId) {
		return contains(getSourceIds(), sourceId);
	}

	void setSourceIds(int[] sourceIds) {
		store.applyIntArrayPref(SOURCE_IDS, sourceIds);
	}

	int nextSourceId() {
		return store.getIntPref(SOURCE_COUNTER) + 1;
	}

	int getSourceCounter() {
		return store.getIntPref(SOURCE_COUNTER);
	}

	PreferenceStore getStore() {
		return store;
	}

	void saveM3uSource(PreferenceStore.Edit e, int sourceId, String m3uId) {
		e.setIntPref(SOURCE_COUNTER, sourceId);
		e.setStringPref(sourceTypePref(sourceId), TvSourceItem.TYPE_M3U);
		e.setStringPref(m3uIdPref(sourceId), m3uId);
	}

	void saveXtreamSource(PreferenceStore.Edit e, int sourceId, XtreamAccount account) {
		e.setIntPref(SOURCE_COUNTER, sourceId);
		e.setStringPref(sourceTypePref(sourceId), TvSourceItem.TYPE_XTREAM);
		XtreamAccount.save(e, sourceId, account);
	}

	void updateXtreamSource(PreferenceStore.Edit e, int sourceId, XtreamAccount account) {
		e.setStringPref(sourceTypePref(sourceId), TvSourceItem.TYPE_XTREAM);
		XtreamAccount.save(e, sourceId, account);
	}

	void saveStalkerSource(PreferenceStore.Edit e, int sourceId, StalkerAccount account) {
		e.setIntPref(SOURCE_COUNTER, sourceId);
		e.setStringPref(sourceTypePref(sourceId), TvSourceItem.TYPE_STALKER);
		StalkerAccount.save(e, sourceId, account);
	}

	void updateStalkerSource(PreferenceStore.Edit e, int sourceId, StalkerAccount account) {
		e.setStringPref(sourceTypePref(sourceId), TvSourceItem.TYPE_STALKER);
		StalkerAccount.save(e, sourceId, account);
	}

	void removeM3uSourcePrefs(PreferenceStore.Edit e, int sourceId) {
		e.removePref(sourceTypePref(sourceId));
		e.removePref(m3uIdPref(sourceId));
	}

	void removeXtreamSourcePrefs(PreferenceStore.Edit e, int sourceId) {
		e.removePref(sourceTypePref(sourceId));
		XtreamAccount.remove(e, sourceId);
	}

	void removeStalkerSourcePrefs(PreferenceStore.Edit e, int sourceId) {
		e.removePref(sourceTypePref(sourceId));
		StalkerAccount.remove(e, sourceId);
	}

	void removeSourcePrefs(PreferenceStore.Edit e, int sourceId) {
		removeM3uSourcePrefs(e, sourceId);
		XtreamAccount.remove(e, sourceId);
		StalkerAccount.remove(e, sourceId);
	}

	String getM3uId(int sourceId) {
		return store.getStringPref(m3uIdPref(sourceId));
	}

	String getSourceType(int sourceId) {
		return getSourceType(store, sourceId);
	}

	void restoreSources(int counter, java.util.List<TvAddon.SourceBackup> sources) {
		int[] ids = new int[sources.size()];
		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			for (int i = 0; i < sources.size(); i++) {
				TvAddon.SourceBackup source = sources.get(i);
				ids[i] = source.id;
				if (source.account != null) saveXtreamSource(edit, source.id, source.account);
				else if (source.stalkerAccount != null) {
					saveStalkerSource(edit, source.id, source.stalkerAccount);
				} else saveM3uSource(edit, source.id, source.m3uId);
			}
			edit.setIntPref(SOURCE_COUNTER, counter);
			edit.setIntArrayPref(SOURCE_IDS, ids);
		}
	}

	void removeSource(int sourceId) {
		int[] ids = getSourceIds();
		if (ids.length == 0) return;

		int[] newIds = new int[ids.length - 1];
		boolean removed = false;

		for (int i = 0, j = 0; i < ids.length; i++) {
			if (ids[i] == sourceId) removed = true;
			else if (j < newIds.length) newIds[j++] = ids[i];
			else return;
		}

		if (removed) {
			Log.i("Removing source: ", sourceId);
			try (PreferenceStore.Edit e = store.editPreferenceStore()) {
				e.setIntArrayPref(SOURCE_IDS, newIds);
				removeSourcePrefs(e, sourceId);
			}
		}
	}

	static String getSourceType(PreferenceStore store, int sourceId) {
		String type = store.getStringPref(sourceTypePref(sourceId));
		if (TvSourceItem.TYPE_XTREAM.equals(type)) return TvSourceItem.TYPE_XTREAM;
		if (TvSourceItem.TYPE_STALKER.equals(type)) return TvSourceItem.TYPE_STALKER;
		return TvSourceItem.TYPE_M3U;
	}

	static Pref<Supplier<String>> sourceTypePref(int sourceId) {
		return Pref.s(SOURCE_TYPE_PREFIX + sourceId);
	}

	private static Pref<Supplier<String>> m3uIdPref(int sourceId) {
		return Pref.s(M3U_ID_PREFIX + sourceId);
	}
}
