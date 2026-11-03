package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class PhoneDimActivityTest {

	@Test
	public void leavingEndsTheDimScreen() {
		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		c.pause().stop();
		assertTrue(c.get().isFinishing());
	}

	@Test
	public void rotatingKeepsTheDimScreen() {
		ActivityController<PhoneDimActivity> c = Robolectric.buildActivity(PhoneDimActivity.class).setup();
		PhoneDimActivity beforeRotation = c.get();
		c.recreate();
		assertFalse(beforeRotation.isFinishing());
		assertFalse(c.get().isFinishing());
	}
}
