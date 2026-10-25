package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class InterfacePrefsBuilderTest {
	@Test
	public void vendorGuideCheckboxUsesTheActiveThemeTextColor() throws Exception {
		String source = source("InterfacePrefsBuilder.java");
		assertTrue(source.contains("done.setTextColor("));
		assertTrue(source.contains("activity.getGlassTextPrimary()"));
		assertTrue(source.contains("android.R.attr.textColorPrimary"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/fragment").resolve(name);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
