package me.app.fermatax.auto.carapps.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.app.fermatax.auto.carapps.AutoCarAppsBackend;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class AdbRecoveryJobTest {
	private static final String ADB_WIFI = "adb_wifi_enabled";
	private static final String ADB_ENABLED = "adb_enabled";
	private Context ctx;
	private Predicate<Context> wifi;
	private SharedPreferences prefs;
	private final List<Integer> usbWrites = new ArrayList<>();

	@Before public void setUp() {
		ctx = RuntimeEnvironment.getApplication();
		prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		prefs.edit().clear().commit();
		wifi = AdbRecoveryJob.wifiCheck;
		AdbRecoveryJob.wifiCheck = c -> true;
		shadowOf((Application) ctx).grantPermissions("android.permission.WRITE_SECURE_SETTINGS");
		setAdbWifi(0);
		usbWrites.clear();
		AdbRecoveryJob.usbDebuggingWriter = (c, v) -> {
			usbWrites.add(v);
			Settings.Global.putInt(c.getContentResolver(), ADB_ENABLED, v);
		};
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setAdbPaired(true);
		jobs().cancelAll();
	}

	@After public void tearDown() {
		assertFalse("the app never turns USB debugging off", usbWrites.contains(0));
		AdbRecoveryJob.usbDebuggingWriter = AdbRecoveryJob::putAdbEnabled;
		AdbRecoveryJob.wifiCheck = wifi;
		AdbRecoveryJob.adbWifiWriter = AdbRecoveryJob::putAdbWifi;
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setAdbPaired(false);
		AdbPairing.recoveryScheduler = () -> {
		};
	}

	private JobScheduler jobs() {
		return (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
	}

	private int pendingJobs() {
		return jobs().getAllPendingJobs().size();
	}

	private int adbWifi() {
		return Settings.Global.getInt(ctx.getContentResolver(), ADB_WIFI, 0);
	}

	private void setAdbWifi(int value) {
		Settings.Global.putInt(ctx.getContentResolver(), ADB_WIFI, value);
	}

	private void setUsb(int value) {
		Settings.Global.putInt(ctx.getContentResolver(), ADB_ENABLED, value);
	}

	private int usb() {
		return Settings.Global.getInt(ctx.getContentResolver(), ADB_ENABLED, -1);
	}

	private boolean markerPresent() {
		return prefs.contains(AdbRecoveryJob.PREF_RESTORE);
	}

	@Test public void usbDebuggingIsTurnedOnWhenItIsOff() {
		setUsb(0);

		AdbRecoveryJob.ensureUsbDebugging(ctx);

		assertEquals(List.of(1), usbWrites);
		assertEquals(1, usb());
		assertFalse("no marker: it is never given back", markerPresent());
	}

	@Test public void usbDebuggingIsTurnedOnWhenUnreadable() {
		assertEquals("absent, as Android 17 hides it from apps", -1, usb());

		AdbRecoveryJob.ensureUsbDebugging(ctx);

		assertEquals(List.of(1), usbWrites);
	}

	@Test public void usbDebuggingAlreadyOnIsNotWritten() {
		setUsb(1);

		AdbRecoveryJob.ensureUsbDebugging(ctx);

		assertTrue(usbWrites.isEmpty());
		assertEquals(1, usb());
	}

	@Test public void usbDebuggingIsNotWrittenWithoutThePermission() {
		shadowOf((Application) ctx).denyPermissions("android.permission.WRITE_SECURE_SETTINGS");

		AdbRecoveryJob.ensureUsbDebugging(ctx);

		assertTrue(usbWrites.isEmpty());
		assertEquals(-1, usb());
	}

	@Test public void aRefusedUsbDebuggingWriteIsSwallowed() {
		AdbRecoveryJob.usbDebuggingWriter = (c, v) -> {
			throw new SecurityException("not granted");
		};

		AdbRecoveryJob.ensureUsbDebugging(ctx);

		assertEquals(-1, usb());
	}

	@Test public void theAppNeverWritesUsbDebuggingOffAcrossEnableAndRestore() {
		AdbRecoveryJob.ensureUsbDebugging(ctx);
		AdbRecoveryJob.enableAdbWifi(ctx);
		AdbRecoveryJob.restoreAdbWifi(ctx);
		setUsb(0);
		AdbRecoveryJob.ensureUsbDebugging(ctx);

		assertEquals(List.of(1, 1), usbWrites); // tearDown asserts no 0 anywhere
	}

	@Test public void markerIsWrittenBeforeTheSettingIsEnabled() {
		List<String> writes = new ArrayList<>();
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			writes.add(v + ":marker=" + markerPresent());
			AdbRecoveryJob.putAdbWifi(c, v);
		};

		AdbRecoveryJob.enableAdbWifi(ctx);

		assertEquals(List.of("1:marker=true"), writes);
		assertEquals(1, adbWifi());
		assertTrue(markerPresent());
	}

	@Test public void restoreGivesTheValueBackThenClearsTheMarker() {
		AdbRecoveryJob.enableAdbWifi(ctx);
		List<String> writes = new ArrayList<>();
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			writes.add(v + ":marker=" + markerPresent());
			AdbRecoveryJob.putAdbWifi(c, v);
		};

		AdbRecoveryJob.restoreAdbWifi(ctx);

		assertEquals(List.of("0:marker=true"), writes); // the marker outlives the write: a kill in between repairs again
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test public void aValueTheUserSetIsLeftAlone() {
		setAdbWifi(1);
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			throw new AssertionError("must not write");
		};

		AdbRecoveryJob.enableAdbWifi(ctx);
		AdbRecoveryJob.restoreAdbWifi(ctx);

		assertEquals(1, adbWifi());
		assertFalse(markerPresent());
	}

	@Test public void nothingIsEnabledWithoutWifiOrThePermission() {
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			throw new AssertionError("must not write");
		};
		AdbRecoveryJob.wifiCheck = c -> false;
		AdbRecoveryJob.enableAdbWifi(ctx);
		AdbRecoveryJob.wifiCheck = c -> true;
		shadowOf((Application) ctx).denyPermissions("android.permission.WRITE_SECURE_SETTINGS");
		AdbRecoveryJob.enableAdbWifi(ctx);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test public void aSecondEnableKeepsTheFirstPreviousValue() {
		AdbRecoveryJob.enableAdbWifi(ctx);
		AdbRecoveryJob.enableAdbWifi(ctx);

		AdbRecoveryJob.restoreAdbWifi(ctx);

		assertEquals(0, adbWifi()); // the second call must not take our own 1 for the user's value
	}

	@Test public void aRetryTurnsItOnAgainWithoutTouchingTheSavedValue() {
		AdbRecoveryJob.enableAdbWifi(ctx);
		setAdbWifi(0); // switched off meanwhile: a network change, or the user
		List<String> writes = new ArrayList<>();
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			writes.add(v + ":marker=" + prefs.getInt(AdbRecoveryJob.PREF_RESTORE, -1));
			AdbRecoveryJob.putAdbWifi(c, v);
		};

		AdbRecoveryJob.enableAdbWifi(ctx);

		assertEquals(List.of("1:marker=0"), writes);
		assertEquals(1, adbWifi());
		AdbRecoveryJob.restoreAdbWifi(ctx);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test public void aRetryWithoutWifiOrPermissionDoesNotTurnItOnAgain() {
		AdbRecoveryJob.enableAdbWifi(ctx);
		setAdbWifi(0);
		AdbRecoveryJob.wifiCheck = c -> false;
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			throw new AssertionError("must not write");
		};

		AdbRecoveryJob.enableAdbWifi(ctx);

		assertTrue(markerPresent());
	}

	@Test public void restoreKeepsTheMarkerWhenTheWriteIsRefused() {
		AdbRecoveryJob.enableAdbWifi(ctx);
		AdbRecoveryJob.adbWifiWriter = (c, v) -> {
			throw new SecurityException("revoked");
		};

		AdbRecoveryJob.restoreAdbWifi(ctx);

		assertTrue(markerPresent());
		assertEquals(1, adbWifi());
	}

	@Test public void startupRepairsASettingLeftOnByAKilledRun() {
		prefs.edit().putInt(AdbRecoveryJob.PREF_RESTORE, 0).commit();
		setAdbWifi(1);

		AutoCarAppsBackend.install(ctx);

		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test public void startupWiresTheHookThatPairingCalls() {
		AutoCarAppsBackend.install(ctx);
		assertEquals(0, pendingJobs());

		AdbPairing.recoveryScheduler.run();

		assertEquals(1, pendingJobs());
	}

	@Test public void scheduleBuildsOneJobOnUnmeteredWifi() {
		AdbRecoveryJob.schedule(ctx);
		AdbRecoveryJob.schedule(ctx);

		assertEquals(1, pendingJobs());
		JobInfo job = jobs().getAllPendingJobs().get(0);
		assertEquals(AdbRecoveryJob.JOB_ID, job.getId());
		android.net.NetworkRequest network = job.getRequiredNetwork();
		assertNotNull("the constraint must be the same Wi-Fi the job checks, not any unmetered network", network);
		assertTrue(network.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI));
		assertTrue(network.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
		assertFalse(job.isPeriodic());
		assertEquals(AdbRecoveryJob.class.getName(), job.getService().getClassName());
	}

	@Test public void scheduleNeedsAPairingAndNoUsableRoot() {
		CarAppsFeature.setAdbPaired(false);
		AdbRecoveryJob.schedule(ctx);
		assertEquals(0, pendingJobs());

		CarAppsFeature.setAdbPaired(true);
		CarAppsFeature.setState(RootState.GRANTED);
		AdbRecoveryJob.schedule(ctx);
		assertEquals("root phones never schedule", 0, pendingJobs());

		CarAppsFeature.setState(RootState.AVAILABLE);
		AdbRecoveryJob.schedule(ctx);
		assertEquals(1, pendingJobs());
	}

	private void broadcast(String action) {
		new AdbRecoveryReceiver().onReceive(ctx, new Intent(action));
	}

	@Test public void bootAndUpdateScheduleWhenPaired() {
		broadcast(Intent.ACTION_BOOT_COMPLETED);
		assertEquals(1, pendingJobs());

		jobs().cancelAll();
		broadcast(Intent.ACTION_MY_PACKAGE_REPLACED);
		assertEquals(1, pendingJobs());
	}

	@Test public void bootAndUpdateDoNothingWhenNotPaired() {
		CarAppsFeature.setAdbPaired(false);
		broadcast(Intent.ACTION_BOOT_COMPLETED);
		broadcast(Intent.ACTION_MY_PACKAGE_REPLACED);
		assertEquals(0, pendingJobs());
	}

	@Test public void bootDoesNothingOnRootPhones() {
		CarAppsFeature.setState(RootState.GRANTED);
		broadcast(Intent.ACTION_BOOT_COMPLETED);
		assertEquals(0, pendingJobs());
	}

	@Test public void otherBroadcastsAreIgnored() {
		broadcast(Intent.ACTION_USER_PRESENT);
		new AdbRecoveryReceiver().onReceive(ctx, new Intent());
		new AdbRecoveryReceiver().onReceive(ctx, null);
		assertEquals(0, pendingJobs());
	}
}
