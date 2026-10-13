package me.aap.fermata.ui.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class ControlPanelViewGestureTest {

	@Test
	public void gestureHandlingPreservesZeroLatencyWakeAndDebounce() throws Exception {
		String source = source("ControlPanelView.java");

		// Verify zero-latency waking on single tap up in Glass mode
		assertTrue(source.contains("onSingleTapUp(MotionEvent e)"));
		assertTrue(source.contains("if (!a.getPrefs().isGlassTheme(a.isCarActivity()))"));
		assertTrue(source.contains("if (!isVideoControlsVisible())"));

		// Verify debounce on double tap in Glass mode
		assertTrue(source.contains("onDoubleTap"));
		assertTrue(source.contains("lastControlsShowTimestamp < 400"));

		// Verify 500ms grace period on single tap confirmed
		assertTrue(source.contains("lastControlsShowTimestamp < 500"));
	}

	@Test
	public void controlPanelAppliesGlassThemeStylingWithManualNightMode() throws Exception {
		String source = source("ControlPanelView.java");

		assertTrue(source.contains("isGlassTheme"));
		assertTrue(source.contains("isGlassNightMode()"));
		assertFalse(source.contains("Configuration.UI_MODE_NIGHT_MASK"));
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
