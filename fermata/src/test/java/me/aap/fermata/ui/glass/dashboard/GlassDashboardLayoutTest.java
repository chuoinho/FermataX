package me.aap.fermata.ui.glass.dashboard;

import static org.junit.Assert.*;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GlassDashboardLayoutTest {

    @Test
    public void testResponsiveLayoutModeResolution() {
        Context ctx = RuntimeEnvironment.getApplication();
        GlassDashboardLayout layout = new GlassDashboardLayout(ctx);
        assertEquals(GlassDashboardLayout.Mode.WIDE, layout.computeMode(1920, 720)); // Ultrawide (~2.67)
        assertEquals(GlassDashboardLayout.Mode.WIDE, layout.computeMode(1280, 720)); // Standard 16:9 (~1.78)
        assertEquals(GlassDashboardLayout.Mode.SQUARE, layout.computeMode(800, 800)); // 1:1 (1.0)
        assertEquals(GlassDashboardLayout.Mode.PORTRAIT, layout.computeMode(1080, 1920)); // Tall display (~0.56)
    }

    @Test
    public void testSplitModeToggle() {
        Context ctx = RuntimeEnvironment.getApplication();
        GlassDashboardLayout layout = new GlassDashboardLayout(ctx);
        assertFalse(layout.isSplit());

        layout.setSplit(true);
        assertTrue(layout.isSplit());
        assertEquals(GlassDashboardLayout.Mode.SPLIT, layout.computeMode(1920, 720));
    }
}
