package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import me.aap.utils.ui.view.DialogBuilder;

public class GlassDialogBuilderTest {

	@Test
	public void testGlassDialogBuilderClassExists() throws Exception {
		Class<?> clazz = Class.forName("me.aap.fermata.ui.glass.dialog.GlassDialogBuilder");
		assertNotNull(clazz);
		assertTrue(DialogBuilder.class.isAssignableFrom(clazz));
	}

	@Test
	public void testMainActivityDelegateUsesGlassDialogBuilder() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java");
		assertTrue(source.contains("GlassDialogBuilder"));
	}

	@Test
	public void testGenericDialogFragmentSupportsGlassTheme() throws Exception {
		String source = source("depends/utils/src/main/java/me/aap/utils/ui/fragment/GenericFragment.java");
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
