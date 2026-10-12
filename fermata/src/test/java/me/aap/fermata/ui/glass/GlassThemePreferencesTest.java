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
	public void testPhoneUiNeverReturnsGlassTheme() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyIntPref(MainActivityPrefs.THEME_MAIN, MainActivityPrefs.THEME_GLASS);

		TestMainActivityPrefs prefs = new TestMainActivityPrefs(store);
		assertEquals(MainActivityPrefs.THEME_CLASSIC, prefs.getThemePref(false));
		assertFalse(prefs.isGlassTheme(false));
	}

	@Test
	public void testAutomotiveReturnsGlassThemeWhenConfigured() {
		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyIntPref(MainActivityPrefs.THEME_MAIN, MainActivityPrefs.THEME_CLASSIC);
		store.applyIntPref(MainActivityPrefs.THEME_AA, MainActivityPrefs.THEME_GLASS);

		TestMainActivityPrefs prefs = new TestMainActivityPrefs(store);
		assertEquals(MainActivityPrefs.THEME_GLASS, prefs.getThemePref(true));
		assertTrue(prefs.isGlassTheme(true));
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
		public android.content.SharedPreferences getSharedPreferences() {
			return null;
		}
	}
}
