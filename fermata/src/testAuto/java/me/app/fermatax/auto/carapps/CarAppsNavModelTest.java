package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarAppsNavModelTest {

	@Test
	public void drawerScreenDisablesBackAndClose() {
		CarAppsNavModel model = CarAppsNavModel.of(CarAppsNavModel.Screen.DRAWER);
		assertFalse(model.backEnabled);
		assertTrue(model.drawerHighlighted);
		assertFalse(model.closeEnabled);
	}

	@Test
	public void appScreenEnablesAllThree() {
		CarAppsNavModel model = CarAppsNavModel.of(CarAppsNavModel.Screen.APP);
		assertTrue(model.backEnabled);
		assertFalse(model.drawerHighlighted);
		assertTrue(model.closeEnabled);
	}
}
