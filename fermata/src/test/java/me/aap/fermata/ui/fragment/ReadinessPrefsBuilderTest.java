package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import me.aap.fermata.ui.policy.RuntimeHostMode;

public class ReadinessPrefsBuilderTest {
	@Test
	public void readinessIsAvailableOnlyFromThePhoneSettingsHost() {
		assertTrue(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.PHONE));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.AA_PROJECTION));
		assertFalse(ReadinessPrefsBuilder.shouldAdd(RuntimeHostMode.MIRROR));
	}

	@Test
	public void closedOrInactiveViewsRejectRefreshCallbacks() {
		assertTrue(ReadinessPrefsBuilder.isRefreshAllowed(() -> true, false));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> false, false));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> true, true));
		assertFalse(ReadinessPrefsBuilder.isRefreshAllowed(() -> {
			throw new IllegalStateException("stale view");
		}, false));
	}

	@Test
	public void sectionHeadersUseTheActivityThemeForTheirPrimaryTextColor() throws Exception {
		String source = source("ReadinessPrefsBuilder.java");
		assertTrue(source.contains("Context themeContext = activity.getContext();"));
		assertTrue(source.contains("new TextView(themeContext)"));
		assertTrue(source.contains("themeContext.getTheme().resolveAttribute"));
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
