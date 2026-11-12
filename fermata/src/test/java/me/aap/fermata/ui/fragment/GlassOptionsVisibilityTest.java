package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.BasicPreferenceStore;

/** The Glass appearance option follows the single shared theme only, on the Auto and the Mobile build alike. */
public class GlassOptionsVisibilityTest {

	@Test
	public void glassOptionsShowOnlyForGlassTheme() {
		assertTrue(InterfacePrefsBuilder.showsGlassOptions(store(MainActivityPrefs.THEME_GLASS)));
		assertFalse(InterfacePrefsBuilder.showsGlassOptions(store(MainActivityPrefs.THEME_CLASSIC)));
		assertFalse(InterfacePrefsBuilder.showsGlassOptions(store(MainActivityPrefs.THEME_MATERIAL_YOU)));
		// Fresh install: no stored theme means Glass.
		assertTrue(InterfacePrefsBuilder.showsGlassOptions(new BasicPreferenceStore()));
	}

	@Test
	public void staleCarThemeKeyDoesNotShowGlassOptions() {
		BasicPreferenceStore s = store(MainActivityPrefs.THEME_CLASSIC);
		s.applyIntPref(MainActivityPrefs.LEGACY_THEME_AA, MainActivityPrefs.THEME_GLASS);
		assertFalse(InterfacePrefsBuilder.showsGlassOptions(s));
	}

	private static BasicPreferenceStore store(int theme) {
		BasicPreferenceStore s = new BasicPreferenceStore();
		s.applyIntPref(MainActivityPrefs.THEME_MAIN, theme);
		return s;
	}
}
