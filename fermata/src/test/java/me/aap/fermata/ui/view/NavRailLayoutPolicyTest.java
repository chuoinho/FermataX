package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavRailLayoutPolicyTest {
	@Test
	public void sharedWidthIsResponsiveAndBoundedPerHost() {
		assertEquals(80, NavRailLayoutPolicy.railWidthDp(false, 360, 1F));
		assertEquals(81, NavRailLayoutPolicy.railWidthDp(false, 393, 1.85F));
		assertEquals(96, NavRailLayoutPolicy.railWidthDp(false, 600, 4F));
		assertEquals(96, NavRailLayoutPolicy.railWidthDp(true, 600, 1.75F));
		assertEquals(96, NavRailLayoutPolicy.railWidthDp(true, 800, 1.85F));
		assertEquals(104, NavRailLayoutPolicy.railWidthDp(true, 1200, 2F));
		assertEquals(112, NavRailLayoutPolicy.railWidthDp(true, 1200, 4F));
	}

	@Test
	public void mobileTargetsAreCompactWhileProjectionKeepsCarSafeGeometry() {
		assertEquals(64, NavRailLayoutPolicy.touchTargetExtentDp(false, 400));
		assertEquals(64, NavRailLayoutPolicy.touchTargetExtentDp(false, 632));
		assertEquals(76, NavRailLayoutPolicy.touchTargetExtentDp(true, 400));
		assertEquals(76, NavRailLayoutPolicy.touchTargetExtentDp(true, 520));
		assertEquals(80, NavRailLayoutPolicy.touchTargetExtentDp(true, 632));
		assertEquals(56, NavRailLayoutPolicy.visualTileExtentDp(76));
		assertEquals(60, NavRailLayoutPolicy.visualTileExtentDp(80));
		assertEquals(36, NavRailLayoutPolicy.iconExtentDp());
		assertEquals(8, NavRailLayoutPolicy.separatorExtentDp());
	}

	@Test
	public void compactDhuLeavesThreeFullDestinationTargetsVisible() {
		int railHeight = 400;
		int target = NavRailLayoutPolicy.touchTargetExtentDp(true, railHeight);
		int viewport = railHeight - (target * 2) -
				NavRailLayoutPolicy.separatorExtentDp();

		assertEquals(240, viewport);
		assertEquals(3, viewport / target);
	}

	@Test
	public void projectionRaisesVerticalSlopAndHorizontalIntentNeedsMoreTravel() {
		assertEquals(8, NavRailLayoutPolicy.verticalGestureSlopPx(8, false));
		assertEquals(14, NavRailLayoutPolicy.verticalGestureSlopPx(8, true));
		assertEquals(20, NavRailLayoutPolicy.horizontalGestureSlopPx(8));
	}

	@Test
	public void gestureAxisWaitsForItsDominantAxisThresholdAndThenLocksDirection() {
		assertEquals(NavRailLayoutPolicy.GestureAxis.UNDECIDED,
				NavRailLayoutPolicy.resolveGestureAxis(7, 8, 8, true));
		assertEquals(NavRailLayoutPolicy.GestureAxis.UNDECIDED,
				NavRailLayoutPolicy.resolveGestureAxis(19, 15, 8, true));
		assertEquals(NavRailLayoutPolicy.GestureAxis.VERTICAL,
				NavRailLayoutPolicy.resolveGestureAxis(15, 16, 8, true));
		assertEquals(NavRailLayoutPolicy.GestureAxis.HORIZONTAL,
				NavRailLayoutPolicy.resolveGestureAxis(21, 15, 8, true));
		assertEquals(NavRailLayoutPolicy.GestureAxis.VERTICAL,
				NavRailLayoutPolicy.resolveGestureAxis(30, 31, 8, true));
		assertEquals(NavRailLayoutPolicy.GestureAxis.HORIZONTAL,
				NavRailLayoutPolicy.resolveGestureAxis(31, 30, 8, true));
	}
}
