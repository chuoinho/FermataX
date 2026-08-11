package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavRailScrollPolicyTest {
	@Test
	public void dragScrollRemainsDirectionalAndClamped() {
		assertEquals(75, NavRailScrollPolicy.nextScroll(50, 25.8f, 180));
		assertEquals(20, NavRailScrollPolicy.nextScroll(50, -30.9f, 180));
		assertEquals(180, NavRailScrollPolicy.nextScroll(170, 50, 180));
		assertEquals(0, NavRailScrollPolicy.nextScroll(10, -50, 180));
	}

	@Test
	public void passiveAffordanceVisibilityTracksScrollBoundaries() {
		assertFalse(NavRailScrollPolicy.canScrollUp(0));
		assertTrue(NavRailScrollPolicy.canScrollDown(0, 120));
		assertTrue(NavRailScrollPolicy.canScrollUp(40));
		assertTrue(NavRailScrollPolicy.canScrollDown(40, 120));
		assertTrue(NavRailScrollPolicy.canScrollUp(120));
		assertFalse(NavRailScrollPolicy.canScrollDown(120, 120));
	}
}
