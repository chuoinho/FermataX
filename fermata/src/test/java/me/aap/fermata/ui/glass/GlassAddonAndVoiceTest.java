package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class GlassAddonAndVoiceTest {

	@Test
	public void testVoiceRecognitionSessionSupportsGlassTheme() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/activity/VoiceRecognitionSession.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("0xFF00E5FF"));
	}

	@Test
	public void testInitialSetupFragmentSupportsGlassTheme() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/fragment/InitialSetupFragment.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("createGlassCardDrawable") || source.contains("getGlassCardDrawable"));
	}

	@Test
	public void testWebToolBarMediatorSupportsGlassTheme() throws Exception {
		String source = source("modules/web/src/main/java/me/aap/fermata/addon/web/WebToolBarMediator.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("getGlassCardDrawable") || source.contains("createGlassCardDrawable"));
	}

	@Test
	public void testTikTokFloatingNavViewSupportsGlassTheme() throws Exception {
		String source = source("modules/web/src/main/java/me/aap/fermata/addon/web/tiktok/TikTokFloatingNavView.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("createGlassPillDrawable") || source.contains("getGlassCardDrawable"));
	}

	private static String source(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve(relativePath);
		if (!Files.isRegularFile(file)) {
			file = Path.of("e:/antigravity/fermata").resolve(relativePath);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
