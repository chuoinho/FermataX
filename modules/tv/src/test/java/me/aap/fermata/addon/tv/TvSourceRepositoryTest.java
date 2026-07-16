package me.aap.fermata.addon.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;

public class TvSourceRepositoryTest {
	@Test
	public void defaultsMissingSourceTypeToM3u() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		assertEquals(TvSourceItem.TYPE_M3U, TvSourceRepository.getSourceType(store, 7));
	}

	@Test
	public void savesLegacyM3uSourcePrefs() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		TvSourceRepository repo = new TvSourceRepository(store);

		assertEquals(1, repo.nextSourceId());
		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			repo.saveM3uSource(edit, 1, "m3u-resource-id");
		}
		repo.setSourceIds(new int[]{1});

		assertArrayEquals(new int[]{1}, repo.getSourceIds());
		assertEquals(2, repo.nextSourceId());
		assertEquals(TvSourceItem.TYPE_M3U, repo.getSourceType(1));
		assertEquals("m3u-resource-id", repo.getM3uId(1));
	}

	@Test
	public void readsXtreamSourceTypeMarker() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		TvSourceRepository repo = new TvSourceRepository(store);

		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			edit.setStringPref(TvSourceRepository.sourceTypePref(2), TvSourceItem.TYPE_XTREAM);
		}

		assertEquals(TvSourceItem.TYPE_XTREAM, repo.getSourceType(2));
	}

	@Test
	public void removesSourcePrefs() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		TvSourceRepository repo = new TvSourceRepository(store);

		try (PreferenceStore.Edit edit = store.editPreferenceStore()) {
			repo.saveM3uSource(edit, 1, "m3u-resource-id");
		}
		repo.setSourceIds(new int[]{1});

		repo.removeSource(1);

		assertArrayEquals(new int[0], repo.getSourceIds());
		assertFalse(repo.hasSource(1));
		assertEquals(TvSourceItem.TYPE_M3U, repo.getSourceType(1));
		assertEquals(null, repo.getM3uId(1));
	}
}
