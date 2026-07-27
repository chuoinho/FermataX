package me.aap.fermata.addon.stremio.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StremioLayoutPolicyTest {
	@Test
	public void posterColumnsStayReadableAcrossTargetWidths() {
		assertEquals(2, StremioLayoutPolicy.posterColumns(288));
		assertEquals(1, StremioLayoutPolicy.posterColumns(180));
		assertEquals(3, StremioLayoutPolicy.posterColumns(460));
		assertEquals(5, StremioLayoutPolicy.posterColumns(728));
		assertEquals(7, StremioLayoutPolicy.posterColumns(952));
		assertEquals(11, StremioLayoutPolicy.posterColumns(1_500));
		assertEquals(16, StremioLayoutPolicy.posterColumns(2_500));
	}

	@Test
	public void invalidWidthUsesSafeMinimum() {
		assertEquals(2, StremioLayoutPolicy.posterColumns(0));
		assertEquals(2, StremioLayoutPolicy.posterColumns(-1));
	}

	@Test
	public void shelfHeightTracksResponsivePosterWidth() {
		assertEquals(240, StremioLayoutPolicy.shelfHeightDp(128));
		assertEquals(216, StremioLayoutPolicy.shelfHeightDp(112));
		assertEquals(276, StremioLayoutPolicy.shelfHeightDp(152));
		assertEquals(240, StremioLayoutPolicy.shelfHeightDp(0));
	}

	@Test
	public void detailsHeaderScalesWithoutCrowdingSmallDisplays() {
		assertEquals(88, StremioLayoutPolicy.detailsPosterWidthDp(320));
		assertEquals(104, StremioLayoutPolicy.detailsPosterWidthDp(460));
		assertEquals(116, StremioLayoutPolicy.detailsPosterWidthDp(728));
		assertEquals(128, StremioLayoutPolicy.detailsPosterWidthDp(952));
		assertEquals(160, StremioLayoutPolicy.detailsBackdropHeightDp(320));
		assertEquals(192, StremioLayoutPolicy.detailsBackdropHeightDp(728));
	}
}
