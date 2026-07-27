package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavRailScrollPolicyTest {
	@Test
	public void affordanceSpaceIsReservedOnlyWhenContentOverflows() {
		assertEquals(0, NavRailScrollPolicy.maxScroll(300, 400, 42));
		assertEquals(142, NavRailScrollPolicy.maxScroll(500, 400, 42));
	}

	@Test
	public void visibleBoundsKeepIconsOutsideAffordance() {
		assertEquals(0, NavRailScrollPolicy.visibleTop(0, 42));
		assertEquals(142, NavRailScrollPolicy.visibleTop(100, 42));
		assertEquals(358, NavRailScrollPolicy.visibleBottom(0, 400, 142, 42));
		assertEquals(542, NavRailScrollPolicy.visibleBottom(142, 400, 142, 42));
	}

	@Test
	public void affordanceConsumesOnlyTheVisibleEdgeStrip() {
		assertFalse(NavRailScrollPolicy.isAffordanceTouch(380, 400, 0, 0, 42));
		assertTrue(NavRailScrollPolicy.isAffordanceTouch(380, 400, 0, 142, 42));
		assertFalse(NavRailScrollPolicy.isAffordanceTouch(200, 400, 0, 142, 42));
		assertTrue(NavRailScrollPolicy.isAffordanceTouch(20, 400, 50, 142, 42));
		assertFalse(NavRailScrollPolicy.isAffordanceTouch(380, 400, 142, 142, 42));
	}

	@Test
	public void dragScrollRemainsDirectionalAndClamped() {
		assertEquals(75, NavRailScrollPolicy.nextScroll(50, 25.8f, 180));
		assertEquals(20, NavRailScrollPolicy.nextScroll(50, -30.9f, 180));
		assertEquals(180, NavRailScrollPolicy.nextScroll(170, 50, 180));
		assertEquals(0, NavRailScrollPolicy.nextScroll(10, -50, 180));
	}
}
