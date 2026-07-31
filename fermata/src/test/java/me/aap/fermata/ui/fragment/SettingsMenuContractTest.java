package me.aap.fermata.ui.fragment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SettingsMenuContractTest {
	@Test
	public void interfaceDoesNotExposeLegacyBarOptions() throws Exception {
		String source = source("InterfacePrefsBuilder.java");

		assertFalse(source.contains("R.string.hide_bars"));
		assertFalse(source.contains("R.string.fullscreen_mode"));
	}

	@Test
	public void playbackSettingsDoesNotExposeSubtitleSubmenu() throws Exception {
		String source = source("SettingsFragment.java");
		int start = source.indexOf("PreferenceSet playback =");
		int end = source.indexOf("VoicePrefsBuilder.add", start);

		assertTrue(start >= 0);
		assertTrue(end > start);
		String playback = source.substring(start, end);
		assertFalse(playback.contains("R.string.subtitles"));
		assertFalse(playback.contains("addSubtitlePrefs"));
	}

	@Test
	public void diagnosticsReplacesLegacyRawLogAction() throws Exception {
		String settings = source("SettingsFragment.java");
		String backup = source("SettingsBackupManager.java");

		assertTrue(settings.contains("R.string.detailed_diagnostics"));
		assertTrue(settings.contains("R.string.export_diagnostic_report"));
		assertTrue(settings.contains("R.string.clear_diagnostic_data"));
		assertFalse(settings.contains("R.string.open_log"));
		assertFalse(backup.contains("static void openLog"));
		assertTrue(backup.contains("!\"diagnostics.xml\".equals(name)"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		}
		return new String(Files.readAllBytes(file), UTF_8);
	}
}
