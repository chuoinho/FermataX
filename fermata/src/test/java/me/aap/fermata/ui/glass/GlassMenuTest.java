package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.fermata.ui.glass.theme.GlassDrawables;

public class GlassMenuTest {

	@Test
	public void testActivityDelegateHasGlassThemeHooks() throws Exception {
		Method isGlass = ActivityDelegate.class.getMethod("isGlassTheme");
		assertNotNull(isGlass);
		Method getDrawable = ActivityDelegate.class.getMethod("getGlassCardDrawable", android.content.Context.class, float.class);
		assertNotNull(getDrawable);
	}

	@Test
	public void testOverlayMenuViewHasGlassThemeSupport() throws Exception {
		String source = source("depends/utils/src/main/java/me/aap/utils/ui/menu/OverlayMenuView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testOverlayMenuItemViewHasGlassThemeSupport() throws Exception {
		String source = source("depends/utils/src/main/java/me/aap/utils/ui/menu/OverlayMenuItemView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testGlassDrawablesProvidesItemHighlight() throws Exception {
		Method highlightMethod = GlassDrawables.class.getMethod("createGlassItemHighlightDrawable", android.content.Context.class, boolean.class);
		assertNotNull(highlightMethod);
	}

	private static String source(String relativePath) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve(relativePath);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata").resolve(relativePath);
		}
		if (!Files.isRegularFile(file)) {
			file = Path.of("e:/antigravity/fermata").resolve(relativePath);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
