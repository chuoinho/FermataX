package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import me.aap.fermata.ui.control.PhoneReadiness.Action;

@RunWith(AndroidJUnit4.class)
public class ReadinessSettingsIntentTest {
	private static final String PACKAGE = "me.app.fermataX.p0test";

	@Test
	public void noActionDoesNotCreateAnIntent() {
		assertNull(ReadinessPrefsBuilder.settingsIntent(Action.NONE, PACKAGE, 35));
	}

	@Test
	public void notificationSettingsTargetsTheRunningPackageOnModernAndroid() {
		Intent intent = ReadinessPrefsBuilder.settingsIntent(Action.NOTIFICATION_SETTINGS,
				PACKAGE, 35);

		assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.getAction());
		assertEquals(PACKAGE, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE));
	}

	@Test
	public void legacyNotificationAndOverlayUseAppDetails() {
		Intent notification = ReadinessPrefsBuilder.settingsIntent(Action.NOTIFICATION_SETTINGS,
				PACKAGE, 25);
		Intent overlay = ReadinessPrefsBuilder.settingsIntent(Action.OVERLAY_SETTINGS, PACKAGE, 22);

		assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, notification.getAction());
		assertEquals("package:" + PACKAGE, notification.getDataString());
		assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, overlay.getAction());
		assertEquals("package:" + PACKAGE, overlay.getDataString());
	}

	@Test
	public void overlayAndBatteryActionsUseOnlyTheDocumentedSettingsPages() {
		Intent overlay = ReadinessPrefsBuilder.settingsIntent(Action.OVERLAY_SETTINGS, PACKAGE, 35);
		Intent battery = ReadinessPrefsBuilder.settingsIntent(Action.BATTERY_SETTINGS, PACKAGE, 35);

		assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, overlay.getAction());
		assertEquals("package:" + PACKAGE, overlay.getDataString());
		assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, battery.getAction());
	}
}
