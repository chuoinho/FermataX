package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ControlPanelSizingPolicyTest {
	@Test
	public void narrowCellScalesGlyphAndKeepsPrimarySurfaceSquare() {
		ControlPanelSizingPolicy.Geometry g = ControlPanelSizingPolicy.resolve(44, 64);
		assertEquals(44, g.visualSize());
		assertEquals(23, g.glyphSize());
		assertEquals(10, g.horizontalPadding());
		assertEquals(20, g.verticalPadding());
		assertEquals(0, g.backgroundInsetX());
		assertEquals(10, g.backgroundInsetY());
	}

	@Test
	public void wideCellCentersAReasonableVisualWithoutGrowingWithTheWholeRail() {
		ControlPanelSizingPolicy.Geometry g = ControlPanelSizingPolicy.resolve(96, 64);
		assertEquals(64, g.visualSize());
		assertEquals(33, g.glyphSize());
		assertEquals(31, g.horizontalPadding());
		assertEquals(15, g.verticalPadding());
		assertEquals(16, g.backgroundInsetX());
		assertEquals(0, g.backgroundInsetY());
	}

	@Test
	public void geometryNeverProducesNegativeInsetsOrPadding() {
		ControlPanelSizingPolicy.Geometry g = ControlPanelSizingPolicy.resolve(1, 1);
		assertTrue(g.glyphSize() >= 1);
		assertTrue(g.horizontalPadding() >= 0);
		assertTrue(g.verticalPadding() >= 0);
		assertTrue(g.backgroundInsetX() >= 0);
		assertTrue(g.backgroundInsetY() >= 0);
	}
}
