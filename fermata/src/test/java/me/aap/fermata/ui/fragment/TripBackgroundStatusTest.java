package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.aap.fermata.R;
import me.aap.fermata.ui.glass.speedo.TripModePolicy.Mode;

/** Turning background recording off cannot revoke "Allow all the time"; the UI must say so. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class TripBackgroundStatusTest {

	@Test
	public void subtitleSaysThePermissionIsStillAllowedWhenOff() {
		assertEquals(R.string.trip_background_sub_on, InterfacePrefsBuilder.tripBackgroundSubtitle(Mode.ON, true));
		assertEquals(R.string.trip_background_sub_off, InterfacePrefsBuilder.tripBackgroundSubtitle(Mode.OFF, false));
		assertEquals(R.string.trip_background_sub_off_still_allowed,
				InterfacePrefsBuilder.tripBackgroundSubtitle(Mode.OFF, true));
		assertEquals(R.string.trip_background_sub_needs_permission,
				InterfacePrefsBuilder.tripBackgroundSubtitle(Mode.NEEDS_BACKGROUND_PERMISSION, false));
		assertEquals(R.string.trip_background_sub_needs_permission,
				InterfacePrefsBuilder.tripBackgroundSubtitle(Mode.NEEDS_LOCATION_PERMISSION, false));
	}

	@Test
	public void initialSetupRowUsesTheSameWarning() {
		assertEquals(R.string.initial_setup_perm_background_sub,
				InterfacePrefsBuilder.tripBackgroundPurpose(Mode.ON, true));
		assertEquals(R.string.initial_setup_perm_background_sub,
				InterfacePrefsBuilder.tripBackgroundPurpose(Mode.OFF, false));
		assertEquals(R.string.trip_background_sub_off_still_allowed,
				InterfacePrefsBuilder.tripBackgroundPurpose(Mode.OFF, true));
	}

	@Test
	public void downgradeIsOfferedOnlyWhileAllowedAllTheTime() {
		Application app = RuntimeEnvironment.getApplication();
		shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_BACKGROUND_LOCATION);
		assertTrue(InterfacePrefsBuilder.offersPermissionDowngrade(app));
		shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
		assertFalse(InterfacePrefsBuilder.offersPermissionDowngrade(app));
	}

	@Test
	public void downgradeOpensTheAppPermissionPage() {
		Application app = RuntimeEnvironment.getApplication();
		Intent i = InterfacePrefsBuilder.permissionSettingsIntent(app);
		assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, i.getAction());
		assertEquals("package:" + app.getPackageName(), i.getDataString());
	}
}
