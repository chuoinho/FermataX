package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ControlPanelCenteringPolicyTest {
	@Test
	public void exactCenterNeedsNoCorrection() {
		assertEquals(0F, ControlPanelSizingPolicy.centerOffset(346, 151, 195), 0F);
	}

	@Test
	public void weightedChainRoundingIsCorrectedFromMeasuredBounds() {
		assertEquals(1.5F, ControlPanelSizingPolicy.centerOffset(346, 149, 194), 0F);
		assertEquals(-1.5F, ControlPanelSizingPolicy.centerOffset(346, 152, 197), 0F);
	}

	@Test
	public void correctionScalesWithAnyParentWidthInsteadOfUsingFixedDp() {
		assertEquals(2F, ControlPanelSizingPolicy.centerOffset(800, 348, 448), 0F);
		assertEquals(0.5F, ControlPanelSizingPolicy.centerOffset(1280, 608, 671), 0F);
	}
}
