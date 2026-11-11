package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;

/**
 * Wireless debugging that is already on but whose mdns record is stale (mdnsd was stopped, adbd announced itself to the old
 * one): the port is never found. The start switches it off and on once, waits for the port again, and the user's value is
 * still what is restored afterwards.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = me.aap.fermata.FermataApplication.class)
public class ServerStarterReRegisterTest {
	private static final String ADB_WIFI = "adb_wifi_enabled";
	private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
	private static final String START = "CLASSPATH='/a.apk' nohup app_process / Main 1 2 pkg persist >/dev/null 2>&1";

	private Context ctx;
	private SharedPreferences prefs;
	private java.util.function.Predicate<Context> wifi;
	private java.util.function.Function<Context, Object> network;
	private BiFunction<Context, Long, FutureSupplier<Integer>> finder;
	private ServerStarter.Runner runner;
	private final List<String> events = new ArrayList<>();
	private final List<FutureSupplier<Integer>> answers = new ArrayList<>();

	@Before public void setUp() {
		ctx = RuntimeEnvironment.getApplication();
		prefs = ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE);
		prefs.edit().clear().commit();
		shadowOf((Application) ctx).grantPermissions(PERMISSION);
		wifi = AdbRecoveryJob.wifiCheck;
		AdbRecoveryJob.wifiCheck = c -> true;
		network = AdbRecoveryJob.wifiNetwork;
		AdbRecoveryJob.wifiNetwork = c -> null;
		AdbRecoveryJob.refusedWifi = null;
		finder = ServerStarter.portFinder;
		runner = ServerStarter.runner;
		setAdbWifi(1);
		ServerStarter.portFinder = (c, timeout) -> {
			events.add("find " + timeout + " adb_wifi=" + adbWifi());
			return answers.remove(0);
		};
		ServerStarter.runner = (c, port, command) -> {
			events.add("run " + port + " " + command.endsWith(START));
			return Completed.completedVoid();
		};
	}

	@After public void tearDown() {
		ServerStarter.portFinder = finder;
		ServerStarter.runner = runner;
		AdbRecoveryJob.wifiCheck = wifi;
		AdbRecoveryJob.wifiNetwork = network;
		AdbRecoveryJob.refusedWifi = null;
	}

	private static FutureSupplier<Integer> miss() {
		return Completed.failed(new TimeoutException());
	}

	private int adbWifi() {
		return Settings.Global.getInt(ctx.getContentResolver(), ADB_WIFI, 0);
	}

	private void setAdbWifi(int value) {
		Settings.Global.putInt(ctx.getContentResolver(), ADB_WIFI, value);
	}

	private void idle(long ms) {
		shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
	}

	@Test public void portFoundTheFirstTimeNeverTogglesAnything() {
		answers.add(Completed.completed(40000));

		FutureSupplier<Void> run = ServerStarter.startViaAdb(ctx, START);

		assertTrue(run.isDone() && !run.isFailed());
		assertEquals(List.of("find 12000 adb_wifi=1", "run 40000 true"), events);
		assertEquals(1, adbWifi());
		assertFalse(prefs.contains(AdbRecoveryJob.PREF_RESTORE));
	}

	@Test public void aStaleRecordIsFixedByOneToggleAndTheUsersValueComesBack() {
		answers.add(miss());
		answers.add(Completed.completed(41000));

		FutureSupplier<Void> run = ServerStarter.startViaAdb(ctx, START);

		assertTrue(run.isDone() && !run.isFailed());
		assertEquals("the discovery is running before wireless debugging is switched off",
				List.of("find 12000 adb_wifi=1", "find 10000 adb_wifi=1", "run 41000 true"), events);
		assertEquals("off now, on again after the gap", 0, adbWifi());
		assertEquals("the marker holds the user's value", 1, prefs.getInt(AdbRecoveryJob.PREF_RESTORE, -1));
		idle(2_000);
		assertEquals(1, adbWifi());

		AdbRecoveryJob.restoreAdbWifi(ctx); // what the client does when the start ends
		assertEquals(1, adbWifi());
		assertFalse(prefs.contains(AdbRecoveryJob.PREF_RESTORE));
	}

	@Test public void aKillBetweenTheTwoWritesIsRepairedToTheUsersValue() {
		answers.add(miss());
		answers.add(Completed.completed(41000));
		ServerStarter.startViaAdb(ctx, START);
		assertEquals(0, adbWifi());

		AdbRecoveryJob.restoreAdbWifi(ctx); // the repair at the next startup

		assertEquals(1, adbWifi());
		assertFalse(prefs.contains(AdbRecoveryJob.PREF_RESTORE));
	}

	@Test public void stillNoPortAfterTheToggleFailsOnceWithoutAnotherToggle() {
		answers.add(miss());
		answers.add(miss());

		FutureSupplier<Void> run = ServerStarter.startViaAdb(ctx, START);

		assertTrue(run.isFailed());
		assertEquals(2, events.size());
		assertTrue(AdbRecoveryJob.refusedWifi == null); // adb_wifi is 0 only until the gap: Wi-Fi was not refused
		idle(2_000);
		assertEquals(1, adbWifi());
		// the next attempt of the same start owns the marker: it must not toggle again
		answers.add(miss());
		ServerStarter.startViaAdb(ctx, START);
		assertEquals("no second toggle", 3, events.size());
	}

	@Test public void refusedWifiIsRememberedAndTheMarkerDropped() {
		AdbRecoveryJob.wifiNetwork = c -> "home";
		answers.add(miss());
		answers.add(miss());

		// the setting is off when the second wait ends: Android turned it off again, so it refuses this network
		ServerStarter.startViaAdb(ctx, START);

		assertEquals("home", AdbRecoveryJob.refusedWifi);
		assertFalse("restoring a 1 would show Android's dialog again", prefs.contains(AdbRecoveryJob.PREF_RESTORE));
		assertFalse("no more tries on this network", AdbRecoveryJob.hasWifi(ctx));
	}

	@Test public void withoutThePermissionTheNormalFailureHappens() {
		shadowOf((Application) ctx).denyPermissions(PERMISSION);
		answers.add(miss());

		FutureSupplier<Void> run = ServerStarter.startViaAdb(ctx, START);

		assertTrue(run.isFailed());
		assertEquals(1, events.size());
		assertEquals(1, adbWifi());
		assertFalse(prefs.contains(AdbRecoveryJob.PREF_RESTORE));
	}

	@Test public void aSettingWeSwitchedOnOurselvesOrAnOffOneIsNotToggled() {
		prefs.edit().putInt(AdbRecoveryJob.PREF_RESTORE, 0).commit(); // owned by this start: it is a fresh registration
		answers.add(miss());
		assertTrue(ServerStarter.startViaAdb(ctx, START).isFailed());
		assertEquals(1, events.size());
		assertEquals(1, adbWifi());
		assertEquals(0, prefs.getInt(AdbRecoveryJob.PREF_RESTORE, -1));

		prefs.edit().clear().commit();
		setAdbWifi(0);
		answers.add(miss());
		assertTrue(ServerStarter.startViaAdb(ctx, START).isFailed());
		assertEquals(2, events.size());
		assertEquals(0, adbWifi());
	}

	@Test public void noWifiMeansNoToggle() {
		AdbRecoveryJob.wifiCheck = c -> false;
		answers.add(miss());

		assertTrue(ServerStarter.startViaAdb(ctx, START).isFailed());

		assertEquals(1, events.size());
		assertEquals(1, adbWifi());
	}
}
