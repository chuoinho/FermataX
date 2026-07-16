package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;

import org.junit.Test;

import me.aap.utils.pref.BasicPreferenceStore;

public class DashboardItemsMigrationTest {
	@Test
	public void migrationPreservesExistingUserOrderAndOnlyAppendsMissingItems() {
		String[] persisted = {
				DashboardItems.RECENT,
				"example.CustomAddon",
				DashboardItems.FOLDERS,
				DashboardItems.FAVORITES
		};
		List<String> defaults = List.of(
				DashboardItems.FOLDERS,
				DashboardItems.FAVORITES,
				DashboardItems.PLAYLISTS,
				DashboardItems.RECENT,
				"example.NewAddon");

		assertArrayEquals(new String[]{
				DashboardItems.RECENT,
				"example.CustomAddon",
				DashboardItems.FOLDERS,
				DashboardItems.FAVORITES,
				DashboardItems.PLAYLISTS,
				"example.NewAddon"
		}, DashboardItems.mergeUiRefreshOrder(persisted, defaults));
	}

	@Test
	public void freshLayoutUsesCanonicalDefaultsWithoutDuplicates() {
		assertArrayEquals(new String[]{
				DashboardItems.FOLDERS,
				DashboardItems.FAVORITES,
				DashboardItems.PLAYLISTS,
				DashboardItems.RECENT
		}, DashboardItems.mergeUiRefreshOrder(null, List.of(
				DashboardItems.FOLDERS,
				DashboardItems.FAVORITES,
				DashboardItems.PLAYLISTS,
				DashboardItems.RECENT,
				DashboardItems.FAVORITES)));
	}

	@Test
	public void resetClearsPersistedOrderAndMigrationMarker() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyStringArrayPref(DashboardItems.PREF,
				new String[]{DashboardItems.RECENT, DashboardItems.FOLDERS});
		store.applyIntPref(DashboardItems.LAYOUT_VERSION, 2);

		DashboardItems.reset(store);

		assertFalse(store.hasPref(DashboardItems.PREF, false));
		assertFalse(store.hasPref(DashboardItems.LAYOUT_VERSION, false));
	}
}
