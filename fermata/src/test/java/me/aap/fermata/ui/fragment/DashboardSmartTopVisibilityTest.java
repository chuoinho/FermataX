package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.R;

public class DashboardSmartTopVisibilityTest {
	@Test
	public void phoneNeverShowsSmartTop() {
		assertFalse(DashboardModelBuilder.shouldShowSmartTop(false));
	}

	@Test
	public void everyAutomotiveHostShowsSmartTopRegardlessOfWidth() {
		assertTrue(DashboardModelBuilder.shouldShowSmartTop(true));
	}

	@Test
	public void RecentIsSuppressedOnlyWhenSmartTopIsActuallyVisible() {
		assertFalse(DashboardModelBuilder.shouldSuppressRecent(false, R.id.recent_fragment));
		assertTrue(DashboardModelBuilder.shouldSuppressRecent(true, R.id.recent_fragment));
		assertFalse(DashboardModelBuilder.shouldSuppressRecent(true, R.id.favorites_fragment));
	}
}
