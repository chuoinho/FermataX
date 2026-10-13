package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class GlassPhoneControllersTest {

	@Test
	public void testPhonePlaybackCarHeaderControllerSupportsGlassTheme() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/view/PhonePlaybackCarHeaderController.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("0xFF00E5FF"));
	}

	@Test
	public void testPhoneSupportBannerControllerSupportsGlassTheme() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/view/PhoneSupportBannerController.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("createGlassPillDrawable"));
	}

	@Test
	public void testPhoneBottomMenuControllerSupportsGlassThemeCyan() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/view/PhoneBottomMenuController.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("0xFF00E5FF"));
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
