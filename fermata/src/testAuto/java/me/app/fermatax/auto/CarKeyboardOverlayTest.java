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
}
