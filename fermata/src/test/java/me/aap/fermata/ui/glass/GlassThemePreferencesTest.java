package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.BasicPreferenceStore;

public class GlassThemePreferencesTest {

	@Test
	public void testGlassThemeConstantExists() {
		assertEquals(9, MainActivityPrefs.THEME_GLASS);
	}

	@Test
	public void testPhoneUiThemeDefaultsAndGlassOption() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		TestMainActivityPrefs prefs = new TestMainActivityPrefs(store);

		// Default phone theme is Classic
		assertEquals(MainActivityPrefs.THEME_CLASSIC, prefs.getThemePref(false));
		assertFalse(prefs.isGlassTheme(false));

		// When phone selects Glass theme, returns Glass
		store.applyIntPref(MainActivityPrefs.THEME_MAIN, MainActivityPrefs.THEME_GLASS);
		assertEquals(MainActivityPrefs.THEME_GLASS, prefs.getThemePref(false));
		assertTrue(prefs.isGlassTheme(false));

		// Invalid theme falls back to Classic
		store.applyIntPref(MainActivityPrefs.THEME_MAIN, 999);
		assertEquals(MainActivityPrefs.THEME_CLASSIC, prefs.getThemePref(false));
		assertFalse(prefs.isGlassTheme(false));
	}

	@Test
	public void testAutomotiveDefaultsToClassicAndSupportsGlassOptIn() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		TestMainActivityPrefs prefs = new TestMainActivityPrefs(store);

		// Fresh install AA theme defaults to Classic (no silent Glass force)
		assertEquals(MainActivityPrefs.THEME_CLASSIC, prefs.getThemePref(true));
		assertFalse(prefs.isGlassTheme(true));

		// Can opt in to Glass on AA
		store.applyIntPref(MainActivityPrefs.THEME_AA, MainActivityPrefs.THEME_GLASS);
		assertEquals(MainActivityPrefs.THEME_GLASS, prefs.getThemePref(true));
		assertTrue(prefs.isGlassTheme(true));

		// Invalid AA theme falls back to Classic
		store.applyIntPref(MainActivityPrefs.THEME_AA, 999);
		assertEquals(MainActivityPrefs.THEME_CLASSIC, prefs.getThemePref(true));
		assertFalse(prefs.isGlassTheme(true));
	}

	@Test
	public void testGlassAppearancePreference() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		TestMainActivityPrefs prefs = new TestMainActivityPrefs(store);

		// Default appearance is NIGHT
		assertEquals(MainActivityPrefs.GLASS_APPEARANCE_NIGHT, prefs.getGlassAppearancePref());
		assertTrue(prefs.isGlassNightMode());

		// Can switch to DAY
		prefs.setGlassAppearancePref(MainActivityPrefs.GLASS_APPEARANCE_DAY);
		assertEquals(MainActivityPrefs.GLASS_APPEARANCE_DAY, prefs.getGlassAppearancePref());
		assertFalse(prefs.isGlassNightMode());

		// Can switch back to NIGHT
		prefs.setGlassAppearancePref(MainActivityPrefs.GLASS_APPEARANCE_NIGHT);
		assertEquals(MainActivityPrefs.GLASS_APPEARANCE_NIGHT, prefs.getGlassAppearancePref());
		assertTrue(prefs.isGlassNightMode());

		// Invalid value normalizes to NIGHT
		store.applyIntPref(MainActivityPrefs.GLASS_APPEARANCE, 42);
		assertEquals(MainActivityPrefs.GLASS_APPEARANCE_NIGHT, prefs.getGlassAppearancePref());
		assertTrue(prefs.isGlassNightMode());
	}

	private static final class TestMainActivityPrefs extends BasicPreferenceStore implements MainActivityPrefs {
		private final BasicPreferenceStore delegate;

		TestMainActivityPrefs(BasicPreferenceStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public int getIntPref(Pref<? extends IntSupplier> pref) {
			return delegate.getIntPref(pref);
		}

		@Override
		public void applyIntPref(Pref<? extends IntSupplier> pref, int value) {
			delegate.applyIntPref(pref, value);
		}

		@Override
		public android.content.SharedPreferences getSharedPreferences() {
			return null;
		}
	}
}
