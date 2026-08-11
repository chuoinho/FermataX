package me.aap.fermata.ui.view;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ControlPanelContentInsetCoordinatorTest {
	@Test
	public void panelInsetAddsToAndRestoresOriginalListPadding() {
		assertEquals(84, ControlPanelContentInsetCoordinator.bottomPadding(8, 76));
		assertEquals(8, ControlPanelContentInsetCoordinator.bottomPadding(8, 0));
		assertEquals(76, ControlPanelContentInsetCoordinator.bottomPadding(-4, 76));
	}
}
