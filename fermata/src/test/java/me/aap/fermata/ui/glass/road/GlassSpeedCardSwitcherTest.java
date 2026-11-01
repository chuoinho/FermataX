package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.glass.speedo.GlassSpeedoTripView;

@RunWith(RobolectricTestRunner.class)
public class GlassSpeedCardSwitcherTest {

    private Context context;
    private MainActivityPrefs prefs;
    private GlassSpeedoTripView speedView;
    private GlassLimitsSignalsView limitsView;
    private AtomicBoolean notifiedLimitsVisible;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = MainActivityPrefs.get();
        prefs.applyBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA, true);
        prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 0);
        speedView = new GlassSpeedoTripView(context);
        limitsView = new GlassLimitsSignalsView(context);
        notifiedLimitsVisible = new AtomicBoolean(false);
    }

    @Test
    public void restoresSavedChoice() {
        prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 1);
        GlassSpeedCardSwitcher switcher1 = new GlassSpeedCardSwitcher(context, speedView, limitsView, prefs,
                notifiedLimitsVisible::set);
        assertTrue(switcher1.isLimitsShown());
        assertEquals(View.VISIBLE, limitsView.getVisibility());
        assertEquals(View.GONE, speedView.getVisibility());

        prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 0);
        GlassSpeedoTripView speed2 = new GlassSpeedoTripView(context);
        GlassLimitsSignalsView limits2 = new GlassLimitsSignalsView(context);
        GlassSpeedCardSwitcher switcher2 = new GlassSpeedCardSwitcher(context, speed2, limits2, prefs,
                notifiedLimitsVisible::set);
        assertFalse(switcher2.isLimitsShown());
        assertEquals(View.VISIBLE, speed2.getVisibility());
        assertEquals(View.GONE, limits2.getVisibility());
    }

    @Test
    public void swapPersistsAndNotifies() {
        GlassSpeedCardSwitcher switcher = new GlassSpeedCardSwitcher(context, speedView, limitsView, prefs,
                notifiedLimitsVisible::set);
        assertFalse(switcher.isLimitsShown());

        // Swap to Limits card
        speedView.getSwapButton().performClick();
        assertTrue(switcher.isLimitsShown());
        assertEquals(1, prefs.getIntPref(MainActivityPrefs.GLASS_SPEED_CARD));
        assertTrue(notifiedLimitsVisible.get());

        // Swap back to Speed card
        limitsView.getSwapButton().performClick();
        assertFalse(switcher.isLimitsShown());
        assertEquals(0, prefs.getIntPref(MainActivityPrefs.GLASS_SPEED_CARD));
        assertFalse(notifiedLimitsVisible.get());
    }

    @Test
    public void roadDataOffForcesSpeedCard() {
        prefs.applyIntPref(MainActivityPrefs.GLASS_SPEED_CARD, 1);
        prefs.applyBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA, false);

        GlassSpeedCardSwitcher switcher = new GlassSpeedCardSwitcher(context, speedView, limitsView, prefs,
                notifiedLimitsVisible::set);
        assertFalse(switcher.isLimitsShown());
        assertEquals(View.VISIBLE, speedView.getVisibility());
        assertEquals(View.GONE, limitsView.getVisibility());
        assertEquals(View.GONE, speedView.getSwapButton().getVisibility());
    }
}
