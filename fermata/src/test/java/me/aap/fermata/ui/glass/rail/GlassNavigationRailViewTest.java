package me.aap.fermata.ui.glass.rail;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.concurrent.TimeUnit;
import org.robolectric.shadows.ShadowLooper;

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

        // Hit envelope must be at least 64dp width and 104dp height
        int minHitWidth = GlassDimensions.dpToPx(context, 64);
        int expectedHitHeight = GlassDimensions.dpToPx(context, 104);
        assertTrue("Grabber hit width must be at least 64dp", lp.width >= minHitWidth);
        assertEquals(expectedHitHeight, lp.height);

        // Inner visual semicircle pill must be 44dp width x 88dp height
        assertEquals(1, grabber.getChildCount());
        View innerPill = grabber.getChildAt(0);
        ViewGroup.LayoutParams pillLp = innerPill.getLayoutParams();
        assertEquals(GlassDimensions.dpToPx(context, 44), pillLp.width);
        assertEquals(GlassDimensions.dpToPx(context, 88), pillLp.height);

        // Child of inner pill is the app launcher ImageView
        assertEquals(1, ((FrameLayout) innerPill).getChildCount());
        ImageView ivLauncher = (ImageView) ((FrameLayout) innerPill).getChildAt(0);
        assertNotNull("Launcher icon must not be null", ivLauncher);
        assertEquals(View.VISIBLE, ivLauncher.getVisibility());
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

        // Grabber handle hit target for Car must be 80dp
        FrameLayout grabber = (FrameLayout) carRail.getChildAt(2);
        assertEquals(GlassDimensions.dpToPx(context, 80), grabber.getLayoutParams().width);
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

        // Verify inner launcher icon
        FrameLayout innerPill = (FrameLayout) grabber.getChildAt(0);
        ImageView ivLauncher = (ImageView) innerPill.getChildAt(0);
        assertNotNull(ivLauncher);
        assertEquals(View.VISIBLE, ivLauncher.getVisibility());
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

    @Test
    public void testAutoHideTimerCollapsesAfterDelay() {
        railView.expand();
        assertTrue(railView.isOpen());

        ShadowLooper.idleMainLooper(5000, TimeUnit.MILLISECONDS);

        assertFalse("Rail must auto-collapse after 5000ms idle timeout", railView.isOpen());
    }

    @Test
    public void testTouchEventResetsAutoHideTimer() {
        railView.expand();
        assertTrue(railView.isOpen());

        // Advance 3000ms
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS);
        assertTrue(railView.isOpen());

        // Touch event down and up resets timer
        long now = SystemClock.uptimeMillis();
        railView.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        railView.dispatchTouchEvent(MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, 10f, 10f, 0));

        // Advance another 3000ms (total 6000ms from start, but only 3000ms from touch reset)
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS);
        assertTrue("Rail must remain open because touch event reset the 5000ms countdown", railView.isOpen());

        // Advance remaining 2000ms to hit 5000ms from touch reset
        ShadowLooper.idleMainLooper(2000, TimeUnit.MILLISECONDS);
        assertFalse("Rail must auto-collapse when 5000ms elapses after last touch", railView.isOpen());
    }

    @Test
    public void testVerticalDragClampingAndRatioCalculation() {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY);
        railView.measure(widthSpec, heightSpec);
        railView.layout(0, 0, 800, 480);

        FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
        // Default ratio 0.5f: parent 480, handle 104, minMargin 12 -> minY 12, maxY 364
        // targetY = 12 + 0.5 * 352 = 188
        assertEquals(188f, grabber.getTranslationY(), 1.0f);

        long now = SystemClock.uptimeMillis();
        grabber.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 188f, 0));
        grabber.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_MOVE, 10f, 288f, 0));
        assertEquals(288f, grabber.getTranslationY(), 1.0f);

        // Past top boundary (clamped to 12)
        grabber.dispatchTouchEvent(MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_MOVE, 10f, -50f, 0));
        assertEquals(12f, grabber.getTranslationY(), 1.0f);

        grabber.dispatchTouchEvent(MotionEvent.obtain(now, now + 150, MotionEvent.ACTION_UP, 10f, -50f, 0));
        assertFalse("Dragging must not expand the rail", railView.isOpen());
    }

    @Test
    public void testQuickTapExpandsRailWithoutDrag() {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY);
        railView.measure(widthSpec, heightSpec);
        railView.layout(0, 0, 800, 480);

        FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
        long now = SystemClock.uptimeMillis();
        grabber.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 188f, 0));
        grabber.dispatchTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, 10f, 189f, 0));

        assertTrue("Quick tap on grabber must expand the rail", railView.isOpen());
    }

    @Test
    public void testSwipeInwardOnGrabberHandleExpandsRail() {
        railView.collapse();
        assertFalse(railView.isOpen());

        FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
        long downTime = SystemClock.uptimeMillis();

        // Down on grabber handle
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 20f, 50f, 0);
        grabber.dispatchTouchEvent(down);

        // Swipe horizontally inward by 50px (rail is on left)
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 70f, 52f, 0);
        grabber.dispatchTouchEvent(move);

        assertTrue("Inward horizontal swipe on grabber handle must expand rail during move", railView.isOpen());
    }

    @Test
    public void testVerticalDragOnGrabberHandleRepositionsWithoutExpanding() {
        railView.collapse();
        FrameLayout grabber = (FrameLayout) railView.getChildAt(2);
        grabber.layout(0, 0, 80, 104);
        railView.layout(0, 0, 800, 480);
        float initialY = grabber.getTranslationY();

        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 20f, 50f, 0);
        grabber.dispatchTouchEvent(down);

        // Drag vertically down by 80px
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 22f, 130f, 0);
        grabber.dispatchTouchEvent(move);

        MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 22f, 130f, 0);
        grabber.dispatchTouchEvent(up);

        assertFalse("Vertical drag must not expand rail", railView.isOpen());
        assertNotEquals("Vertical drag must update grabber translationY", initialY, grabber.getTranslationY(), 0.1f);
    }

    @Test
    public void testEdgeSwipeOutsideHandleTriggersBackAction() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.collapse();
        FrameLayout grabber = (FrameLayout) carRail.getChildAt(2);
        carRail.layout(0, 0, 800, 480);
        grabber.setTranslationY(200f);

        final boolean[] backTriggered = {false};
        carRail.setBackActionListenerForTesting(() -> backTriggered[0] = true);

        long downTime = SystemClock.uptimeMillis();
        // Touch near left edge (x=10, y=50) - clearly above grabber handle
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 50f, 0);
        assertFalse("ACTION_DOWN must not intercept immediately so children receive touch", carRail.onInterceptTouchEvent(down));

        // Swipe inward to x=70, y=52 (> 45px and dominant horizontal)
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 70f, 52f, 0);
        assertTrue("Inward edge swipe must be intercepted", carRail.onInterceptTouchEvent(move));

        // Deliver to onTouchEvent
        carRail.onTouchEvent(move);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 70f, 52f, 0);
        carRail.onTouchEvent(up);

        assertTrue("Edge swipe outside grabber handle must trigger Back action", backTriggered[0]);
    }

    @Test
    public void testCenterScreenSwipeDoesNotInterceptOrTriggerBack() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.collapse();
        carRail.layout(0, 0, 800, 480);

        final boolean[] backTriggered = {false};
        carRail.setBackActionListenerForTesting(() -> backTriggered[0] = true);

        long downTime = SystemClock.uptimeMillis();
        // Touch in center of screen: x=300, y=200
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 300f, 200f, 0);
        assertFalse(carRail.onInterceptTouchEvent(down));

        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 380f, 200f, 0);
        assertFalse("Center screen swipe must NOT be intercepted", carRail.onInterceptTouchEvent(move));

        assertFalse("Back must NOT trigger for center screen swipe", backTriggered[0]);
    }

    @Test
    public void testEdgeSwipeInsideHandleBoundsDoesNotTriggerBack() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.collapse();
        FrameLayout grabber = (FrameLayout) carRail.getChildAt(2);
        carRail.layout(0, 0, 800, 480);
        grabber.setTranslationY(150f);

        final boolean[] backTriggered = {false};
        carRail.setBackActionListenerForTesting(() -> backTriggered[0] = true);

        long downTime = SystemClock.uptimeMillis();
        // Touch at y=180 (inside grabber handle envelope)
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 180f, 0);
        assertFalse("Touch inside grabber handle envelope must not be intercepted for Back", carRail.onInterceptTouchEvent(down));

        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 70f, 180f, 0);
        assertFalse("Swipe inside grabber handle must not be intercepted as edge Back", carRail.onInterceptTouchEvent(move));
        assertFalse("Back must NOT trigger inside grabber handle", backTriggered[0]);
    }

    @Test
    public void testPhoneHostDoesNotInterceptEdgeSwipe() {
        // Phone host: isCar = false
        GlassNavigationRailView phoneRail = new GlassNavigationRailView(context, false);
        phoneRail.collapse();
        phoneRail.layout(0, 0, 800, 480);

        final boolean[] backTriggered = {false};
        phoneRail.setBackActionListenerForTesting(() -> backTriggered[0] = true);

        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 50f, 0);
        assertFalse(phoneRail.onInterceptTouchEvent(down));

        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 70f, 52f, 0);
        assertFalse("Phone host must not intercept edge swipe", phoneRail.onInterceptTouchEvent(move));
        assertFalse("Back must NOT trigger on phone host", backTriggered[0]);
    }

    @Test
    public void testEdgeSwipeInwardOnRightRailOrientation() {
        GlassNavigationRailView carRail = new GlassNavigationRailView(context, true);
        carRail.setRightForTesting(true);
        carRail.collapse();
        carRail.layout(0, 0, 800, 480);
        FrameLayout grabber = (FrameLayout) carRail.getChildAt(2);
        grabber.setTranslationY(200f);

        final boolean[] backTriggered = {false};
        carRail.setBackActionListenerForTesting(() -> backTriggered[0] = true);

        long downTime = SystemClock.uptimeMillis();
        // Right edge: x=790 (width 800, edge slop 36), y=50 (outside grabber at 200..304)
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 790f, 50f, 0);
        assertFalse(carRail.onInterceptTouchEvent(down));

        // Swipe inward to the left: x=730 (dx = -60px <= -45px)
        MotionEvent move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 730f, 52f, 0);
        assertTrue("Inward edge swipe from right edge must be intercepted", carRail.onInterceptTouchEvent(move));

        carRail.onTouchEvent(move);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, 730f, 52f, 0);
        carRail.onTouchEvent(up);

        assertTrue("Edge swipe on right rail must trigger Back action", backTriggered[0]);
    }
}
