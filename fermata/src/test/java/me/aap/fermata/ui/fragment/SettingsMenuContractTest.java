package me.aap.fermata.ui.fragment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.Test;

public class SettingsMenuContractTest {
	@Test
	public void interfaceDoesNotExposeLegacyBarOptions() throws Exception {
		String source = source("InterfacePrefsBuilder.java");

		assertFalse(source.contains("R.string.hide_bars"));
		assertFalse(source.contains("R.string.fullscreen_mode"));
		assertFalse(source.contains("R.string.nav_bar_size"));
		assertFalse(source.contains("R.string.tool_bar_size"));
		assertFalse(source.contains("R.string.control_panel_size"));
		assertFalse(source.contains("R.string.text_icon_size"));
		assertFalse(source.contains("R.string.show_pg_up_down"));
		assertFalse(source.contains("R.string.use_dpad_cursor"));
		assertFalse(source.contains("R.string.theme_dark"));
		assertFalse(source.contains("R.string.theme_light"));
		assertFalse(source.contains("R.string.theme_system"));
		assertFalse(source.contains("R.string.theme_black"));
		assertFalse(source.contains("R.string.theme_star_wars"));
		assertFalse(source.contains("R.string.theme_purple"));
		assertTrue(source.contains("R.string.theme_classic"));
		assertTrue(source.contains("R.string.theme_material_you"));
	}

	@Test
	public void interfaceMapsLanguageOrdinalsToValuesMap() throws Exception {
		String source = source("InterfacePrefsBuilder.java");
		assertTrue(source.contains("MainActivityPrefs.LOCALE"));
		assertTrue(source.contains("o.valuesMap = langs.stream().mapToInt(Enum::ordinal).toArray();"));
	}

	@Test
	public void supportedLanguagesMapToExpectedLegacyOrdinals() {
		var values = me.aap.fermata.ui.activity.MainActivityPrefs.Lang.getValues();
		org.junit.Assert.assertEquals(9, values.size());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.TR, values.get(6));
		org.junit.Assert.assertEquals(3, values.get(6).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.DE, values.get(7));
		org.junit.Assert.assertEquals(4, values.get(7).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.IT, values.get(8));
		org.junit.Assert.assertEquals(2, values.get(8).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.EN, values.get(0));
		org.junit.Assert.assertEquals(0, values.get(0).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.VI, values.get(1));
		org.junit.Assert.assertEquals(6, values.get(1).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.RU, values.get(2));
		org.junit.Assert.assertEquals(1, values.get(2).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.ES, values.get(3));
		org.junit.Assert.assertEquals(15, values.get(3).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.PT, values.get(4));
		org.junit.Assert.assertEquals(5, values.get(4).ordinal());
		org.junit.Assert.assertEquals(me.aap.fermata.ui.activity.MainActivityPrefs.Lang.ZH_TW, values.get(5));
		org.junit.Assert.assertEquals(10, values.get(5).ordinal());
	}

	@Test
	public void playbackSettingsDoesNotExposeSubtitleSubmenu() throws Exception {
		String source = source("PlaybackPrefsBuilder.java");
		assertFalse(source.contains("R.string.subtitles"));
		assertFalse(source.contains("addSubtitlePrefs"));

		String settings = source("SettingsFragment.java");
		assertTrue(settings.contains("PlaybackPrefsBuilder.add(a, set, mediaPrefs"));
	}

	@Test
	public void diagnosticsReplacesLegacyRawLogAction() throws Exception {
		String settings = source("SettingsFragment.java");
		String backup = source("SettingsBackupManager.java");
		String stateStore = mainSource("me/aap/fermata/backup/AndroidBackupStateStore.java");

		assertTrue(settings.contains("R.string.detailed_diagnostics"));
		assertTrue(settings.contains("R.string.vehicle_key_test"));
		assertTrue(settings.contains("KeyBindingPrefsBuilder.add(set);"));
		assertTrue(settings.contains("R.string.export_diagnostic_report"));
		assertTrue(settings.contains("R.string.clear_diagnostic_data"));
		assertFalse(settings.contains("VoicePrefsBuilder.add"));
		assertFalse(settings.contains("DashboardPrefsBuilder.add"));
		assertFalse(settings.contains("R.string.other"));
		assertFalse(settings.contains("R.string.open_log"));
		assertFalse(backup.contains("static void openLog"));
		assertFalse(backup.contains("PrefUtils.exportSharedPreferences"));
		assertTrue(stateStore.contains("\"diagnostics\""));
	}

	@Test
	public void singleLargePageStructureWithSectionHeaders() throws Exception {
		String settings = source("SettingsFragment.java");
		assertTrue(settings.contains("addSectionHeader(set, ctx, R.string.interface_prefs);"));
		assertTrue(settings.contains("addSectionHeader(set, ctx, R.string.playback_settings);"));
		assertTrue(settings.contains("addSectionHeader(set, ctx, R.string.addons);"));
		assertTrue(settings.contains("addSectionHeader(set, ctx, R.string.readiness_title);"));
		assertTrue(settings.contains("addSectionHeader(set, ctx, R.string.diagnostics);"));
		assertTrue(settings.contains("addSectionHeader(set, ctx, R.string.backup_restore);"));
		assertFalse(settings.contains("set.subSet(o -> o.title = R.string.interface_prefs)"));
		assertFalse(settings.contains("set.subSet(o -> o.title = R.string.playback_settings)"));
	}

	@Test
	public void vehicleKeyTestIsTranslatedInEverySupportedLocale() throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path resources = root.resolve("src/main/res");
		if (!Files.isDirectory(resources)) resources = root.resolve("fermata/src/main/res");
		try (Stream<Path> directories = Files.list(resources)) {
			directories.filter(Files::isDirectory)
					.filter(path -> path.getFileName().toString().startsWith("values"))
					.map(path -> path.resolve("strings.xml"))
					.filter(Files::isRegularFile)
					.forEach(path -> {
						try {
							String strings = new String(Files.readAllBytes(path), UTF_8);
							assertTrue(path.toString(), strings.contains("name=\"vehicle_key_test\""));
						} catch (Exception error) {
							throw new AssertionError(path.toString(), error);
						}
					});
		}
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		}
		return new String(Files.readAllBytes(file), UTF_8);
	}

	private static String mainSource(String path) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java").resolve(path);
		if (!Files.isRegularFile(file)) file = root.resolve("fermata/src/main/java").resolve(path);
		return new String(Files.readAllBytes(file), UTF_8);
	}
}
