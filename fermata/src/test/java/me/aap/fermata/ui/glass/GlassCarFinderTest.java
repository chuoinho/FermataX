package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class GlassCarFinderTest {

	@Test
	public void testGlassRadarViewExists() throws Exception {
		String source = source("ui/glass/carfinder", "GlassRadarView.java");
		assertTrue(source.contains("updateRelativeBearing"));
		assertTrue(source.contains("0xFF00E5FF"));
	}

	@Test
	public void testCarFinderFragmentSupportsGlassTheme() throws Exception {
		String source = source("carfinder", "CarFinderFragment.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testCarFinderSettingsDialogSupportsGlassTheme() throws Exception {
		String source = source("carfinder", "CarFinderSettingsDialog.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	private static String source(String subpath, String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata").resolve(subpath).resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata").resolve(subpath).resolve(name);
		}
		if (!Files.isRegularFile(file)) {
			file = Path.of("e:/antigravity/fermata/fermata/src/main/java/me/aap/fermata").resolve(subpath).resolve(name);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
