package me.aap.fermata.ui.glass.rail;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GlassNavigationRailViewTest {

    private Context context;
    private GlassNavigationRailView railView;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        railView = new GlassNavigationRailView(context);
    }

    @Test
    public void testInitialStateIsCollapsedWithGrabberVisible() {
        assertFalse(railView.isOpen());

        // Child 0: scrim, Child 1: railContainer, Child 2: grabberHandle
        assertEquals(3, railView.getChildCount());

        View scrim = railView.getChildAt(0);
        View railContainer = railView.getChildAt(1);
        View grabber = railView.getChildAt(2);

        assertEquals(View.GONE, scrim.getVisibility());
        assertEquals(View.GONE, railContainer.getVisibility());
        assertEquals(View.VISIBLE, grabber.getVisibility());
    }

    @Test
    public void testRailContainsExactlyFiveButtons() {
        // Child 1 is the railContainer
        ViewGroup railContainer = (ViewGroup) railView.getChildAt(1);
        assertEquals(5, railContainer.getChildCount());

        // Verify the 5 buttons have accessibility labels
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertNotNull(btn.getContentDescription());
            assertTrue(btn.getContentDescription().length() > 0);
            assertTrue(btn.isFocusable());
        }
    }

    @Test
    public void testExpandAndCollapseImmediate() {
        railView.expand();
        assertTrue(railView.isOpen());

        railView.collapseImmediate();
        assertFalse(railView.isOpen());

        View scrim = railView.getChildAt(0);
        View railContainer = railView.getChildAt(1);
        View grabber = railView.getChildAt(2);

        assertEquals(View.GONE, scrim.getVisibility());
        assertEquals(View.GONE, railContainer.getVisibility());
        assertEquals(View.VISIBLE, grabber.getVisibility());
    }

    @Test
    public void testBackPressedInterceptsWhenOpen() {
        // When collapsed, back press is not intercepted
        assertFalse(railView.onBackPressed());

        // When expanded, back press collapses rail and intercepts event
        railView.expand();
        assertTrue(railView.isOpen());

        boolean intercepted = railView.onBackPressed();
        assertTrue(intercepted);
        assertFalse(railView.isOpen());
    }

    @Test
    public void testFullscreenHidesGrabberAndCollapses() {
        railView.expand();
        assertTrue(railView.isOpen());

        railView.setFullscreenHidden(true);
        assertFalse(railView.isOpen());

        View grabber = railView.getChildAt(2);
        assertEquals(View.GONE, grabber.getVisibility());

        // Exit fullscreen restores grabber
        railView.setFullscreenHidden(false);
        assertEquals(View.VISIBLE, grabber.getVisibility());
    }
}
