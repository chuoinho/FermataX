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
import me.aap.fermata.ui.glass.speedo.TripModePolicy.Access;

/** Location permission presentation remains explicit and color-blind accessible. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class TripBackgroundStatusTest {

	@Test
	public void locationRowUsesTextForEachPermissionLevel() {
		assertEquals(R.string.location_state_missing, InterfacePrefsBuilder.locationAccessState(Access.NOT_GRANTED));
		assertEquals(R.string.location_state_while_using, InterfacePrefsBuilder.locationAccessState(Access.WHILE_USING));
		assertEquals(R.string.location_state_always, InterfacePrefsBuilder.locationAccessState(Access.ALWAYS));
	}

	@Test public void locationStateColorsDistinguishAllThreeStates() {
		assertEquals(0xFFEF4444, InterfacePrefsBuilder.locationAccessColor(Access.NOT_GRANTED));
		assertEquals(0xFFF59E0B, InterfacePrefsBuilder.locationAccessColor(Access.WHILE_USING));
		assertEquals(0xFF22C55E, InterfacePrefsBuilder.locationAccessColor(Access.ALWAYS));
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

	@Test
	public void backgroundReadinessUsesTheStandardBatteryOptimizationSettingsPage() {
		Intent i = ReadinessPrefsBuilder.settingsIntent(me.aap.fermata.ui.control.PhoneReadiness.Action.BATTERY_SETTINGS,
				RuntimeEnvironment.getApplication().getPackageName(), 35);
		assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, i.getAction());
	}
}
