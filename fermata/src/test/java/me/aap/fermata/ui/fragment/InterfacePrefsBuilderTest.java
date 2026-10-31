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

	@Test
	public void glassUnitsCanBeChosenAndApplyLive() throws Exception {
		String builder = source("InterfacePrefsBuilder.java");
		assertTrue(builder.contains("o.pref = MainActivityPrefs.GLASS_SPEED_UNIT;"));
		assertTrue(builder.contains("R.string.glass_units_metric, R.string.glass_units_imperial"));
		String dashboard = source("DashboardFragment.java");
		assertTrue(dashboard.contains("prefs.contains(MainActivityPrefs.GLASS_SPEED_UNIT)"));
		assertTrue(dashboard.contains("glassDashboardController.onUnitsChanged()"));
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
