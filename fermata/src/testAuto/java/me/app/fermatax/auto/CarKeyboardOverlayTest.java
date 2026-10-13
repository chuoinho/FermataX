package me.app.fermatax.auto;

import static android.text.InputType.TYPE_CLASS_NUMBER;
import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
import static android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
import static android.text.InputType.TYPE_TEXT_VARIATION_URI;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarKeyboardOverlayTest {
	@Test
	public void passwordDetectionRequiresMatchingInputClass() {
		assertFalse(CarKeyboardOverlay.isPasswordInputType(
				TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_URI));
		assertTrue(CarKeyboardOverlay.isPasswordInputType(
				TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD));
		assertTrue(CarKeyboardOverlay.isPasswordInputType(
				TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD));
	}

	@Test
	public void keyboardSupportsGlassThemeDayNightTokensWithoutUiMode() throws Exception {
		java.nio.file.Path file = java.nio.file.Path.of("src/auto/java/me/app/fermatax/auto/CarKeyboardOverlay.java");
		if (!java.nio.file.Files.isRegularFile(file)) {
			file = java.nio.file.Path.of("fermata/src/auto/java/me/app/fermatax/auto/CarKeyboardOverlay.java");
		}
		String source = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(source.contains("isGlassTheme()"));
		assertTrue(source.contains("isGlassNightMode()"));
		assertTrue(source.contains("applyTheme()"));
		assertFalse(source.contains("UI_MODE_NIGHT"));
	}
}
