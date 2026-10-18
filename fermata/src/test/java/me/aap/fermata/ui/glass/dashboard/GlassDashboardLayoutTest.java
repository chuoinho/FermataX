package me.aap.fermata.ui.glass.dashboard;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.fermata.ui.glass.quickbar.GlassQuickBarManager;
import me.aap.fermata.ui.glass.quickbar.GlassQuickBarView;

@RunWith(RobolectricTestRunner.class)
public class GlassDashboardLayoutTest {

    @Test
    public void testSplitModeRestoreSpeedoVisibility() {
        Context ctx = RuntimeEnvironment.getApplication();
        GlassDashboardLayout layout = new GlassDashboardLayout(ctx);

        View speedo = new View(ctx);
        View navWeather = new View(ctx);
        View media = new View(ctx);
        GlassQuickBarView quickBar = new GlassQuickBarView(ctx);

        layout.setViews(null, speedo, navWeather, media, quickBar);

        // 1. Enter SPLIT mode
        layout.setSplit(true);
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 800, 300);

        assertEquals(GlassDashboardLayout.Mode.SPLIT, layout.getMode());
        assertEquals(View.GONE, speedo.getVisibility());

        // 2. Exit SPLIT mode to WIDE mode (width=1000, height=600)
        layout.setSplit(false);
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 1000, 600);

        assertEquals(GlassDashboardLayout.Mode.WIDE, layout.getMode());
        assertEquals(View.VISIBLE, speedo.getVisibility());
    }

    @Test
    public void testQuickBarAdaptiveSlotBudget() {
        Context ctx = RuntimeEnvironment.getApplication();
        GlassQuickBarView quickBar = new GlassQuickBarView(ctx);
        quickBar.init(new GlassQuickBarManager(new me.aap.utils.pref.BasicPreferenceStore()), null);

        // Compact measure: width < 550dp (e.g. 400px with density=1.0)
        quickBar.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));

        assertEquals(4, quickBar.getActiveSlotCount());

        // Wide measure: width >= 550dp (e.g. 800px with density=1.0)
        quickBar.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY));

        assertEquals(6, quickBar.getActiveSlotCount());
    }

    @Test
    public void testRefreshAppearanceDayNightPropagation() {
        Context ctx = RuntimeEnvironment.getApplication();
        GlassDashboardLayout layout = new GlassDashboardLayout(ctx);

        me.aap.fermata.ui.glass.speedo.GlassSpeedoTripView speedo = new me.aap.fermata.ui.glass.speedo.GlassSpeedoTripView(ctx);
        GlassNavWeatherView navWeather = new GlassNavWeatherView(ctx);
        me.aap.fermata.ui.glass.media.GlassMediaPlayerView media = new me.aap.fermata.ui.glass.media.GlassMediaPlayerView(ctx);
        GlassQuickBarView quickBar = new GlassQuickBarView(ctx);
        quickBar.init(new GlassQuickBarManager(new me.aap.utils.pref.BasicPreferenceStore()), null);

        layout.setViews(null, speedo, navWeather, media, quickBar);

        layout.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 800, 300);

        // Call refreshAppearance directly
        layout.refreshAppearance();

        assertNotNull(layout.getBackground());
    }
}
