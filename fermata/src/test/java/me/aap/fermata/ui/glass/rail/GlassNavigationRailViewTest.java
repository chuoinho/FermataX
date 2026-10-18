package me.aap.fermata.ui.glass.rail;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import me.aap.fermata.ui.glass.theme.GlassDimensions;

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

        // Verify the 5 buttons have accessibility labels, are focusable, and have minHeight >= 48dp
        int minHeightExpected = GlassDimensions.dpToPx(context, 48);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertNotNull(btn.getContentDescription());
            assertTrue(btn.getContentDescription().length() > 0);
            assertTrue(btn.isFocusable());
            assertTrue("Button minimum height must be at least 48dp", btn.getMinimumHeight() >= minHeightExpected);
        }
    }

    @Test
    public void testGrabberHandleHitTargetEnvelopeAndVisualPillBounds() {
        // Grabber handle is Child 2
        FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
        ViewGroup.LayoutParams lp = grabber.getLayoutParams();

        // Hit envelope must be at least 48dp width and 84dp height
        int minHitWidth = GlassDimensions.dpToPx(context, 48);
        int expectedHitHeight = GlassDimensions.dpToPx(context, 84);
        assertTrue("Grabber hit width must be at least 48dp", lp.width >= minHitWidth);
        assertEquals(expectedHitHeight, lp.height);

        // Inner visual pill must be 26dp width x 72dp height
        assertEquals(1, grabber.getChildCount());
        View innerPill = grabber.getChildAt(0);
        ViewGroup.LayoutParams pillLp = innerPill.getLayoutParams();
        assertEquals(GlassDimensions.dpToPx(context, 26), pillLp.width);
        assertEquals(GlassDimensions.dpToPx(context, 72), pillLp.height);
    }

    @Test
    public void testMeasurementBudgetLowHeightViewport() {
        railView.expand();
        // Simulate low viewport: width 800px (800dp), height 360px (360dp)
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY);
        railView.measure(widthSpec, heightSpec);
        railView.layout(0, 0, 800, 360);

        ViewGroup railContainer = (ViewGroup) railView.getChildAt(1);
        ViewGroup.MarginLayoutParams containerLp = (ViewGroup.MarginLayoutParams) railContainer.getLayoutParams();

        // On 360dp height, margins must be tightened to 8dp
        int expectedVPad = GlassDimensions.dpToPx(context, 8);
        assertEquals(expectedVPad, containerLp.topMargin);
        assertEquals(expectedVPad, containerLp.bottomMargin);

        // Verify that all 5 buttons have actual layout height >= 48dp
        int minTouchPx = GlassDimensions.dpToPx(context, 48);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertTrue("Button height on 360dp viewport must be >= 48dp touch target, was: " + btn.getHeight(),
                    btn.getHeight() >= minTouchPx);
        }
    }

    @Test
    public void testMeasurementBudgetAutomotiveHostHighViewport() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.expand();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY);
        carRail.measure(widthSpec, heightSpec);
        carRail.layout(0, 0, 1280, 720);

        ViewGroup railContainer = (ViewGroup) carRail.getChildAt(1);
        int minTargetPx = GlassDimensions.dpToPx(context, 76);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertTrue("Automotive 720p button height must be >= 76dp, was: " + btn.getHeight(),
                    btn.getHeight() >= minTargetPx);
        }
    }

    @Test
    public void testMeasurementBudgetAutomotiveHostStandardViewport() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.expand();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY);
        carRail.measure(widthSpec, heightSpec);
        carRail.layout(0, 0, 800, 480);

        ViewGroup railContainer = (ViewGroup) carRail.getChildAt(1);
        int minTargetPx = GlassDimensions.dpToPx(context, 76);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertTrue("Automotive 480p button height must be >= 76dp, was: " + btn.getHeight(),
                    btn.getHeight() >= minTargetPx);
        }
    }

    @Test
    public void testMeasurementBudgetAutomotiveHostCompactBoundary() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.expand();
        // At 402dp compact boundary: (402 - 22) / 5 = 76dp per button
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(402, View.MeasureSpec.EXACTLY);
        carRail.measure(widthSpec, heightSpec);
        carRail.layout(0, 0, 800, 402);

        ViewGroup railContainer = (ViewGroup) carRail.getChildAt(1);
        int minTargetPx = GlassDimensions.dpToPx(context, 76);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertTrue("Automotive 402dp boundary button height must be >= 76dp, was: " + btn.getHeight(),
                    btn.getHeight() >= minTargetPx);
        }
    }

    @Test
    public void testMeasurementBudgetAutomotiveHostLowViewportUnder402dp() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.expand();
        // At 360dp low viewport
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY);
        carRail.measure(widthSpec, heightSpec);
        carRail.layout(0, 0, 800, 360);

        ViewGroup railContainer = (ViewGroup) carRail.getChildAt(1);
        // Container must not overflow parent
        assertTrue("Rail container must fit within 360dp viewport without clipping",
                railContainer.getBottom() <= 360);

        // Buttons scale to available space (~67.6dp), remaining well above 48dp floor
        int minTouchPx = GlassDimensions.dpToPx(context, 48);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertTrue("Automotive 360dp button height must be >= 48dp accessible floor, was: " + btn.getHeight(),
                    btn.getHeight() >= minTouchPx);
            assertTrue("Automotive 360dp button height should achieve ~67dp, was: " + btn.getHeight(),
                    btn.getHeight() >= 67);
        }

        // Grabber handle hit target for Car must be 76dp
        FrameLayout grabber = (FrameLayout) carRail.getChildAt(2);
        assertEquals(GlassDimensions.dpToPx(context, 76), grabber.getLayoutParams().width);
    }

    @Test
    public void testMeasurementBudgetAutomotiveHostAccessibilityFloorBoundary() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.expand();
        // At 262dp accessibility floor boundary: (262 - 22) / 5 = 48dp per button
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(262, View.MeasureSpec.EXACTLY);
        carRail.measure(widthSpec, heightSpec);
        carRail.layout(0, 0, 800, 262);

        ViewGroup railContainer = (ViewGroup) carRail.getChildAt(1);
        assertTrue("Rail container must fit within 262dp viewport without clipping",
                railContainer.getBottom() <= 262);

        int minFloorPx = GlassDimensions.dpToPx(context, 48);
        for (int i = 0; i < 5; i++) {
            View btn = railContainer.getChildAt(i);
            assertTrue("Automotive 262dp boundary button height must be >= 48dp accessibility floor, was: " + btn.getHeight(),
                    btn.getHeight() >= minFloorPx);
        }
    }

    @Test
    public void testRailBackgroundHighOpacityAndScrim() {
        // Scrim background must be gentle dimming (alpha = 90)
        View scrim = railView.getChildAt(0);
        android.graphics.drawable.ColorDrawable scrimBg = (android.graphics.drawable.ColorDrawable) scrim.getBackground();
        assertEquals(90, android.graphics.Color.alpha(scrimBg.getColor()));

        // Rail container background must exist with high-opacity frosted glass
        ViewGroup railContainer = (ViewGroup) railView.getChildAt(1);
        assertNotNull(railContainer.getBackground());
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
    public void testFullscreenHidesGrabberAndBlocksExpand() {
        railView.expand();
        assertTrue(railView.isOpen());

        railView.setFullscreenHidden(true);
        assertFalse(railView.isOpen());

        View grabber = railView.getChildAt(2);
        assertEquals(View.GONE, grabber.getVisibility());

        // Expanding while in fullscreenHidden must be blocked
        railView.expand();
        assertFalse(railView.isOpen());

        // Exit fullscreen restores grabber
        railView.setFullscreenHidden(false);
        assertEquals(View.VISIBLE, grabber.getVisibility());
    }

    @Test
    public void testHandleAppearanceUpdateOnDayNight() {
        // Call appearance change
        railView.onAppearanceChanged();
        FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
        assertNotNull(grabber.getContentDescription());
        assertTrue(grabber.getContentDescription().toString().contains("Glass"));

        // Verify inner grip text
        FrameLayout innerPill = (FrameLayout) grabber.getChildAt(0);
        TextView tvGrip = (TextView) innerPill.getChildAt(0);
        assertEquals("›", tvGrip.getText().toString());
    }

    @Test
    public void testRapidAnimationInterruptionSafety() {
        // Rapidly toggle states to verify generation token cancels obsolete callbacks without crash
        railView.expand();
        railView.collapse();
        railView.expand();
        assertTrue(railView.isOpen());

        railView.collapseImmediate();
        assertFalse(railView.isOpen());
    }

    @Test
    public void testKeyActivationSingleShotGuard() {
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        activity.setContentView(railView);
        railView.expand();

        ViewGroup railContainer = (ViewGroup) railView.getChildAt(1);
        View homeBtn = railContainer.getChildAt(0);
        homeBtn.setFocusableInTouchMode(true);
        homeBtn.requestFocus();
        homeBtn.layout(0, 0, 100, 100);

        // Initial key down (repeatCount = 0)
        KeyEvent initialDown = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0);
        boolean handledInitial = homeBtn.dispatchKeyEvent(initialDown);
        assertTrue("Initial ENTER press must be handled", handledInitial);

        // Repeated key down when holding key (repeatCount = 1)
        KeyEvent repeatedDown = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 1);
        boolean handledRepeated = homeBtn.dispatchKeyEvent(repeatedDown);
        assertFalse("Repeated key down (holding key) must not re-trigger action", handledRepeated);
    }
}
