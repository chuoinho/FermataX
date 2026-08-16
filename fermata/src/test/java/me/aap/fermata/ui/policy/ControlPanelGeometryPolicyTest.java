package me.aap.fermata.ui.policy;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ControlPanelGeometryPolicyTest {
	@Test
	public void widePlayerbarKeepsDefaultTransportGeometry() {
		int size = ControlPanelGeometryPolicy.getTransportButtonSize(
				800, 0.19F, 0.81F, 64, 5);
		assertEquals(64, size);
		assertEquals(16, ControlPanelGeometryPolicy.getTransportPadding(size, 16));
		assertEquals(3, ControlPanelGeometryPolicy.getTransportTopMargin(size, 64, 3));
	}

	@Test
	public void narrowPlayerbarShrinksHeightAndPaddingToWeightedSlotWidth() {
		int size = ControlPanelGeometryPolicy.getTransportButtonSize(
				336, 0.19F, 0.81F, 64, 5);
		assertEquals(41, size);
		assertEquals(10, ControlPanelGeometryPolicy.getTransportPadding(size, 16));
		assertEquals(14, ControlPanelGeometryPolicy.getTransportTopMargin(size, 64, 3));
	}

	@Test
	public void unresolvedWidthFallsBackToStableDefaultGeometry() {
		assertEquals(64, ControlPanelGeometryPolicy.getTransportButtonSize(
				0, 0.19F, 0.81F, 64, 5));
		assertEquals(64, ControlPanelGeometryPolicy.getTransportButtonSize(
				336, 0.81F, 0.19F, 64, 5));
	}
}
