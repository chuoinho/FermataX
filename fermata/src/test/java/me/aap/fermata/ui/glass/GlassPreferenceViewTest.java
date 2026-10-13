package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityDelegate;

public class GlassPreferenceViewTest {

	@Test
	public void testActivityDelegateHasWidgetFactoryMethods() throws Exception {
		Method switchMethod = ActivityDelegate.class.getMethod("createSwitch", android.content.Context.class);
		assertNotNull(switchMethod);
		Method sliderMethod = ActivityDelegate.class.getMethod("createSlider", android.content.Context.class);
		assertNotNull(sliderMethod);
	}

	@Test
	public void testPreferenceViewContainsGlassThemeSupport() throws Exception {
		String source = source("depends/utils/src/main/java/me/aap/utils/pref/PreferenceView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testMainActivityDelegateProvidesGlassWidgets() throws Exception {
		String source = source("fermata/src/main/java/me/aap/fermata/ui/activity/MainActivityDelegate.java");
		assertTrue(source.contains("createSwitch"));
		assertTrue(source.contains("createSlider"));
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
