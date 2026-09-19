package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.widget.SwitchCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.R;
import me.aap.fermata.auto.OpenOnCarMode;
import me.aap.fermata.ui.policy.RuntimeHostMode;

@RunWith(RobolectricTestRunner.class)
public class PhoneOpenOnCarStripControllerTest {
	@Test public void disconnectedModeDisablesAndUnchecksThePhoneStrip() {
		OpenOnCarMode mode = new OpenOnCarMode();
		SwitchCompat toggle = new SwitchCompat(activity());
		PhoneOpenOnCarStripController controller = controller(mode, toggle);

		mode.setAvailable(true, 1);
		mode.setEnabled(true);
		mode.setAvailable(false, 2);

		assertFalse(toggle.isEnabled());
		assertFalse(toggle.isChecked());
		controller.close();
	}

	@Test public void reconnectKeepsTheStripUncheckedUntilThePhoneEnablesItAgain() {
		OpenOnCarMode mode = new OpenOnCarMode();
		SwitchCompat toggle = new SwitchCompat(activity());
		PhoneOpenOnCarStripController controller = controller(mode, toggle);

		mode.setAvailable(true, 1);
		mode.setEnabled(true);
		mode.setAvailable(false, 2);
		mode.setAvailable(true, 3);

		assertTrue(toggle.isEnabled());
		assertFalse(toggle.isChecked());
		controller.close();
	}

	@Test public void userToggleUpdatesModeWithoutRenderFeedback() {
		OpenOnCarMode mode = new OpenOnCarMode();
		mode.setAvailable(true, 1);
		SwitchCompat toggle = new SwitchCompat(activity());
		PhoneOpenOnCarStripController controller = controller(mode, toggle);

		toggle.setChecked(true);

		assertTrue(mode.isEnabled());
		assertTrue(toggle.isChecked());
		controller.close();
	}

	@Test public void visibilityIsOwnedByThePhoneShell() {
		OpenOnCarMode mode = new OpenOnCarMode();
		Activity activity = activity();
		LinearLayout strip = new LinearLayout(activity);
		SwitchCompat toggle = new SwitchCompat(activity);
		strip.addView(toggle);
		PhoneOpenOnCarStripController controller = new PhoneOpenOnCarStripController(mode, strip, toggle);

		controller.setVisible(true);
		assertEquals(VISIBLE, strip.getVisibility());
		controller.setVisible(false);
		assertEquals(GONE, strip.getVisibility());
		controller.close();
	}

	@Test public void onlyThePhoneShellMayShowTheStrip() {
		assertEquals(VISIBLE, PhoneOpenOnCarStripController.resolveVisibility(
				RuntimeHostMode.PHONE, false));
		assertEquals(GONE, PhoneOpenOnCarStripController.resolveVisibility(
				RuntimeHostMode.AA_PROJECTION, false));
		assertEquals(GONE, PhoneOpenOnCarStripController.resolveVisibility(
				RuntimeHostMode.MIRROR, false));
		assertEquals(GONE, PhoneOpenOnCarStripController.resolveVisibility(
				RuntimeHostMode.PHONE, true));
	}

	private static PhoneOpenOnCarStripController controller(OpenOnCarMode mode, SwitchCompat toggle) {
		return new PhoneOpenOnCarStripController(mode, new View(toggle.getContext()), toggle);
	}

	private static Activity activity() {
		return Robolectric.buildActivity(Activity.class).setup().get();
	}
}
