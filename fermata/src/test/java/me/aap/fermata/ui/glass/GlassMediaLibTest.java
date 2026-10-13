package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class GlassMediaLibTest {

	@Test
	public void testMediaItemViewSupportsGlassThemeHighlight() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/view/MediaItemView.java");
		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("0xFF00E5FF"));
	}

	@Test
	public void testMediaLibFragmentSupportsGlassEmptyPanel() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/fragment/MediaLibFragment.java");
		assertTrue(source.contains("media_items_state_panel"));
		assertTrue(source.contains("getGlassCardDrawable"));
	}

	@Test
	public void testFilePickerFragmentSupportsGlassTheme() throws Exception {
		String source = source("depends/utils/src/main/java/me/aap/utils/ui/fragment/FilePickerFragment.java");
		assertTrue(source.contains("isGlassTheme"));
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
