package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NavigationCoordinatorTest {
	@Test
	public void topLevelRouteBecomesTheSelectedDestination() {
		assertEquals(42, NavigationCoordinator.resolveRouteSelection(7, 42, true));
	}

	@Test
	public void nonNavRoutePreservesThePreviousTopLevelDestination() {
		int tvDestination = 42;
		int settingsRoute = 99;
		assertEquals(tvDestination,
				NavigationCoordinator.resolveRouteSelection(tvDestination, settingsRoute, false));
	}
}
