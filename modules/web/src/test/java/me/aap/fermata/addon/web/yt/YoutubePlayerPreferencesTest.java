package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceView;

public class YoutubePlayerPreferencesTest {
	@Test
	public void settingsRowAndPlayerMenuShareOneStoredAutomaticFullscreenValue() throws Exception {
		BasicPreferenceStore store = new BasicPreferenceStore();
		YoutubePlayerPreferences.setAutoFullscreen(store, false);
		assertFalse(YoutubePlayerPreferences.getAutoFullscreen(store));

		PreferenceSet settings = new PreferenceSet();
		YoutubePlayerPreferences.contributeAutoFullscreenSetting(store, settings, alwaysVisible());
		PreferenceView.BooleanOpts row = autoFullscreenRow(settings);
		assertSame(store, row.store);
		assertEquals("YT_AUTO_FULLSCREEN", row.pref.getName());

		row.store.applyBooleanPref(row.pref, true);
		assertTrue(YoutubePlayerPreferences.getAutoFullscreen(store));

		YoutubePlayerPreferences.setAutoFullscreen(store, false);
		assertFalse(YoutubePlayerPreferences.getAutoFullscreen(store));
	}

	private static PreferenceView.BooleanOpts autoFullscreenRow(PreferenceSet settings)
			throws Exception {
		Field field = PreferenceSet.class.getDeclaredField("preferences");
		field.setAccessible(true);
		List<?> preferences = (List<?>) field.get(settings);
		for (Object preference : preferences) {
			PreferenceView.Opts options = ((me.aap.utils.function.Supplier<PreferenceView.Opts>) preference).get();
			if ((options instanceof PreferenceView.BooleanOpts row) &&
					"YT_AUTO_FULLSCREEN".equals(row.pref.getName())) return row;
		}
		throw new AssertionError("Automatic fullscreen setting is missing");
	}

	private static ChangeableCondition alwaysVisible() {
		return new ChangeableCondition() {
			@Override
			public boolean get() {
				return true;
			}

			@Override
			public void setListener(Listener listener) {
			}

			@Override
			public ChangeableCondition copy() {
				return this;
			}
		};
	}
}
