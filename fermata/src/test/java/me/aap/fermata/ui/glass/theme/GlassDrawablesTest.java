package me.aap.fermata.ui.glass.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

@RunWith(RobolectricTestRunner.class)
public class GlassDrawablesTest {

	@Test
	public void testThemeColorsNightAndDay() {
		Context context = RuntimeEnvironment.getApplication();
		GlassThemeColors dark = GlassThemeColors.get(context, true);
		GlassThemeColors light = GlassThemeColors.get(context, false);

		assertTrue(dark.isNight());
		assertFalse(light.isNight());
		assertEquals(0xFFFFFFFF, dark.getTextPrimary());
		assertEquals(0xFF15181A, light.getTextPrimary());
	}

	@Test
	public void testCardDrawableCreation() {
		Context context = RuntimeEnvironment.getApplication();
		GradientDrawable card = (GradientDrawable) GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, true);
		assertNotNull(card);
	}

	@Test
	public void testBackgroundGradientCreation() {
		GradientDrawable bg = (GradientDrawable) GlassDrawables.createBackgroundGradient(1920, 720, true);
		assertNotNull(bg);
	}
}
