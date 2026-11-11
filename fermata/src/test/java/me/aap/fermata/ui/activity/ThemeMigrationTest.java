package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LEGACY_THEME;
import static me.aap.fermata.ui.activity.MainActivityPrefs.LEGACY_THEME_AA;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_BLACK;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_CLASSIC;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_DARK;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_GLASS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_LIGHT;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_MAIN;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_MATERIAL_YOU;
import static me.aap.fermata.ui.activity.MainActivityPrefs.THEME_STAR_WARS;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;

/** Phone and car share one theme: THEME_MAIN. THEME_AA and the legacy THEME key are merged once. */
public class ThemeMigrationTest {
	private static final Integer ABSENT = null;

	/** {main, aa, legacy} valid values chosen so every winner is distinguishable from the Glass fallback. */
	private static final int[][] VALID_SETS = {
			{THEME_MATERIAL_YOU, THEME_CLASSIC, THEME_GLASS},
			{THEME_GLASS, THEME_MATERIAL_YOU, THEME_CLASSIC},
	};

	@Test
	public void migrationMatrixResolvesOneValidThemeAndDropsLegacyKeys() {
		for (int[] valid : VALID_SETS) {
			Integer[] mains = {ABSENT, valid[0], THEME_BLACK};
			Integer[] aas = {ABSENT, valid[1], THEME_STAR_WARS};
			Integer[] olds = {ABSENT, valid[2], THEME_LIGHT};
			for (Integer main : mains) {
				for (Integer aa : aas) {
					for (Integer old : olds) {
						int expected = (main != null && main == valid[0]) ? valid[0]
								: (aa != null && aa == valid[1]) ? valid[1]
								: (old != null && old == valid[2]) ? valid[2] : THEME_GLASS;
						String c = "main=" + main + " aa=" + aa + " old=" + old;
						Prefs p = prefs(main, aa, old);

						MainActivityPrefs.migrateTheme(p);
						assertMigrated(c, p, expected);

						AtomicInteger changes = new AtomicInteger();
						PreferenceStore.Listener l = (s, list) -> changes.incrementAndGet();
						p.addBroadcastListener(l);
						MainActivityPrefs.migrateTheme(p);
						assertMigrated(c + " (2nd run)", p, expected);
						assertEquals(c + " 2nd run writes nothing", 0, changes.get());
						p.removeBroadcastListener(l);
					}
				}
			}
		}
	}

	@Test
	public void explicitValidCarChoiceSurvivesJunkPhoneKey() {
		Prefs p = prefs(THEME_BLACK, THEME_CLASSIC, null);
		MainActivityPrefs.migrateTheme(p);
		assertEquals(THEME_CLASSIC, p.getThemePref(false));
		assertEquals(THEME_CLASSIC, p.getThemePref(true));
	}

	@Test
	public void legacyDarkThemeIsNeverWritten() {
		Prefs p = prefs(null, null, THEME_DARK);
		MainActivityPrefs.migrateTheme(p);
		assertFalse(p.hasPref(THEME_MAIN) && p.getIntPref(THEME_MAIN) == THEME_DARK);
		assertEquals(THEME_GLASS, p.getThemePref(true));
		assertFalse(p.hasPref(LEGACY_THEME));
	}

	@Test
	public void carAndPhoneReadTheSameThemeEvenBeforeMigration() {
		// An unmigrated THEME_AA must not give the car a second theme.
		Prefs p = prefs(null, THEME_CLASSIC, null);
		assertEquals(p.getThemePref(false), p.getThemePref(true));
		assertEquals(p.isGlassTheme(false), p.isGlassTheme(true));

		p = prefs(THEME_BLACK, THEME_MATERIAL_YOU, null);
		assertEquals(p.getThemePref(false), p.getThemePref(true));
	}

	private static void assertMigrated(String c, Prefs p, int expected) {
		assertEquals(c, expected, p.getThemePref(false));
		assertEquals(c + " car == phone", p.getThemePref(false), p.getThemePref(true));
		assertFalse(c + " THEME_AA removed", p.hasPref(LEGACY_THEME_AA));
		assertFalse(c + " legacy THEME removed", p.hasPref(LEGACY_THEME));
		if (p.hasPref(THEME_MAIN)) {
			assertTrue(c + " no invalid int stored", MainActivityPrefs.isValidTheme(p.getIntPref(THEME_MAIN)));
		}
		assertEquals(c + " stored value is the effective value", expected, p.getIntPref(THEME_MAIN));
	}

	private static Prefs prefs(Integer main, Integer aa, Integer old) {
		Prefs p = new Prefs();
		if (main != null) p.applyIntPref(THEME_MAIN, main);
		if (aa != null) p.applyIntPref(LEGACY_THEME_AA, aa);
		if (old != null) p.applyIntPref(LEGACY_THEME, old);
		return p;
	}

	private static final class Prefs extends BasicPreferenceStore implements MainActivityPrefs {
		@Override
		public android.content.SharedPreferences getSharedPreferences() {
			return null;
		}
	}
}
