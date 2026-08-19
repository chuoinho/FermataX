package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import me.aap.fermata.R;

public class DashboardSmartTopVisibilityTest {
	@Test
	public void phoneNeverShowsSmartTopEvenWhenWide() {
		assertFalse(DashboardModelBuilder.shouldShowSmartTop(false, 393));
		assertFalse(DashboardModelBuilder.shouldShowSmartTop(false, 800));
		assertFalse(DashboardModelBuilder.shouldShowSmartTop(false, 1280));
	}

	@Test
	public void automotiveDisplaysBelow799HideSmartTop() {
		assertFalse(DashboardModelBuilder.shouldShowSmartTop(true, 480));
		assertFalse(DashboardModelBuilder.shouldShowSmartTop(true, 798));
	}

	@Test
	public void automotiveDisplaysAt799AndAboveShowSmartTop() {
		assertTrue(DashboardModelBuilder.shouldShowSmartTop(true, 799));
		assertTrue(DashboardModelBuilder.shouldShowSmartTop(true, 800));
		assertTrue(DashboardModelBuilder.shouldShowSmartTop(true, 1280));
	}

	@Test
	public void RecentIsSuppressedOnlyWhenSmartTopIsActuallyVisible() {
		assertFalse(DashboardModelBuilder.shouldSuppressRecent(false, R.id.recent_fragment));
		assertTrue(DashboardModelBuilder.shouldSuppressRecent(true, R.id.recent_fragment));
		assertFalse(DashboardModelBuilder.shouldSuppressRecent(true, R.id.favorites_fragment));
	}
}
