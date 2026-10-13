package me.aap.fermata.ui.view;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SubtitlesAndVideoInfoGlassTest {

	@Test
	public void subtitlesViewContainsGlassThemeSupport() throws Exception {
		String source = source("SubtitlesView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void videoInfoViewContainsGlassThemeSupport() throws Exception {
		String source = source("VideoInfoView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/view").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/view").resolve(name);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
