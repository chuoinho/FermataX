package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DashboardFragmentTest {
	@Test
	public void spanCountAdaptsToAvailableWidth() {
		assertEquals(1, DashboardFragment.getSpanCountForWidthDp(400));
		assertEquals(2, DashboardFragment.getSpanCountForWidthDp(600));
		assertEquals(3, DashboardFragment.getSpanCountForWidthDp(726));
		assertEquals(4, DashboardFragment.getSpanCountForWidthDp(1000));
		assertEquals(4, DashboardFragment.getSpanCountForWidthDp(1400));
	}

	@Test
	public void spanCountUsesProjectedDisplayWidthInsteadOfPhoneDensity() {
		assertEquals(3, DashboardFragment.getSpanCount(726, 800, 800, 2F));
		assertEquals(4, DashboardFragment.getSpanCount(1000, 1100, 1100, 3F));
	}

	@Test
	public void fullWidthRecoversAfterTransientSplitLayout() {
		int fullWidthDp = 800;
		assertEquals(1, DashboardFragment.getSpanCount(360, 800, fullWidthDp, 2F));
		assertEquals(3, DashboardFragment.getSpanCount(726, 800, fullWidthDp, 2F));
	}

	@Test
	public void fullWidthIgnoresTransientSplitConfiguration() {
		assertEquals(800, DashboardFragment.getFullWidthDp(400, 800, 1F));
		assertEquals(800, DashboardFragment.getFullWidthDp(800, 800, 2F));
		assertEquals(393, DashboardFragment.getFullWidthDp(393, 1080, 2.75F));
	}

	@Test
	public void spanCountFallsBackToDisplayDensityWithoutRootMetrics() {
		assertEquals(3, DashboardFragment.getSpanCount(1452, 0, 0, 2F));
		assertEquals(4, DashboardFragment.getSpanCount(2000, 0, 0, 2F));
	}

	@Test
	public void viewportTransitionRejectsTransientAndStaleLayouts() {
		DashboardFragment.DashboardViewportState state =
				new DashboardFragment.DashboardViewportState();
		assertTrue(state.canApplyLayout(true));

		int stale = state.beginTransition();
		assertFalse(state.canApplyLayout(true));
		int current = state.beginTransition();
		assertFalse(state.finishTransition(stale, true));
		assertFalse(state.canApplyLayout(true));
		assertTrue(state.finishTransition(current, true));
		assertTrue(state.canApplyLayout(true));
	}

	@Test
	public void failedViewportTransitionUnlocksFutureStableLayout() {
		DashboardFragment.DashboardViewportState state =
				new DashboardFragment.DashboardViewportState();
		int transition = state.beginTransition();
		assertFalse(state.finishTransition(transition, false));
		assertTrue(state.canApplyLayout(true));
		assertFalse(state.canApplyLayout(false));
	}

	@Test
	public void ownedTransitionDoesNotDependOnFragmentCommitTiming() {
		assertTrue(DashboardFragment.isStableDashboardViewport(true, true, false, false));
		assertFalse(DashboardFragment.isStableDashboardViewport(true, true, true, false));
		assertTrue(DashboardFragment.isStableDashboardViewport(true, true, true, true));
		assertFalse(DashboardFragment.isStableDashboardViewport(false, true, false, true));
		assertFalse(DashboardFragment.isStableDashboardViewport(true, false, false, true));
	}

	@Test
	public void dashboardEditMovesOnlyToAdjacentEditableCard() {
		assertEquals(2, DashboardFragment.getMoveTarget(1, 1, 4, position -> position == 0));
		assertEquals(1, DashboardFragment.getMoveTarget(2, -1, 4, position -> position == 0));
	}

	@Test
	public void dashboardEditUsesAtMostTwoColumnsForCarSafeActions() {
		assertEquals(1, DashboardFragment.getEditSpanCount(1));
		assertEquals(2, DashboardFragment.getEditSpanCount(3));
		assertEquals(2, DashboardFragment.getEditSpanCount(4));
	}

	@Test
	public void dashboardEditCannotMoveSmartTopOrCrossItsBoundary() {
		assertEquals(-1, DashboardFragment.getMoveTarget(0, 1, 4, position -> position == 0));
		assertEquals(-1, DashboardFragment.getMoveTarget(1, -1, 4, position -> position == 0));
		assertEquals(-1, DashboardFragment.getMoveTarget(3, 1, 4, position -> position == 0));
	}
}
