package me.aap.fermata.ui.glass;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class GlassAudioEffectsTest {

	@Test
	public void testEqualizerCurveViewContainsGlassThemeSupport() throws Exception {
		String source = source("EqualizerCurveView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testAudioEffectsBandViewContainsGlassThemeSupport() throws Exception {
		String source = source("AudioEffectsBandView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testAudioEffectsScreenViewContainsGlassThemeSupport() throws Exception {
		String source = source("AudioEffectsScreenView.java");
		assertTrue(source.contains("isGlassTheme"));
	}

	@Test
	public void testAudioEffectsScreenViewIntegratesEqualizerCurveView() throws Exception {
		String source = source("AudioEffectsScreenView.java");
		assertTrue(source.contains("EqualizerCurveView curveView"));
		assertTrue(source.contains("getCurveView()"));
	}

	private static String source(String name) throws Exception {
		Path root = Path.of(System.getProperty("user.dir"));
		Path file = root.resolve("src/main/java/me/aap/fermata/ui/view").resolve(name);
		if (!Files.isRegularFile(file)) {
			file = root.resolve("fermata/src/main/java/me/aap/fermata/ui/view").resolve(name);
		}
		if (!Files.isRegularFile(file)) {
			file = Path.of("e:/antigravity/fermata/fermata/src/main/java/me/aap/fermata/ui/view").resolve(name);
		}
		return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
	}
}
