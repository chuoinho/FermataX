package me.aap.fermata.ui.glass.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class GlassWidgetTest {

	@Test
	public void glassSwitchTogglesAndNotifiesListener() {
		Context context = RuntimeEnvironment.getApplication();
		GlassSwitch glassSwitch = new GlassSwitch(context);

		assertFalse(glassSwitch.isChecked());

		boolean[] notified = new boolean[1];
		glassSwitch.setOnCheckedChangeListener((view, isChecked) -> {
			notified[0] = isChecked;
		});

		glassSwitch.setChecked(true);
		assertTrue(glassSwitch.isChecked());
		assertTrue(notified[0]);

		glassSwitch.toggle();
		assertFalse(glassSwitch.isChecked());
		assertFalse(notified[0]);
	}
}
