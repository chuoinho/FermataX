package me.aap.fermata.ui.glass.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GlassHardwareUtilsTest {

	@Test
	public void roundCreatesValidGradientDrawable() {
		GradientDrawable d = GlassHardwareUtils.round(0x1A181C1E, 24.0f);
		assertNotNull(d);
	}

	@Test
	public void roundStrokeCreatesDrawableWithBorders() {
		GradientDrawable d = GlassHardwareUtils.roundStroke(0x1A181C1E, 28.0f, 2, 0x18FFFFFF);
		assertNotNull(d);
	}

	@Test
	public void clipRoundSetsHardwareOutlineProvider() {
		Context context = RuntimeEnvironment.getApplication();
		View view = new View(context);
		view.layout(0, 0, 200, 100);

		GlassHardwareUtils.clipRound(view, 16.0f);

		assertTrue(view.getClipToOutline());
		ViewOutlineProvider provider = view.getOutlineProvider();
		assertNotNull(provider);

		Outline outline = new Outline();
		provider.getOutline(view, outline);
		assertFalse(outline.isEmpty());
	}
}
