package me.app.fermatax.auto.carapps.adb;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.R;
import me.aap.fermata.ui.carapps.CarAppsFeature;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class AdbPairingTest {
	@Rule public TemporaryFolder folder = new TemporaryFolder();
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private final AtomicInteger recovery = new AtomicInteger();
	private final AtomicInteger serverStarts = new AtomicInteger();
	private final List<String> shell = new ArrayList<>();
	private final List<String> codes = new ArrayList<>();
	private Context ctx;
	private boolean pairResult = true;
	private Exception grantError;

	@Before public void setUp() throws Exception {
		ctx = RuntimeEnvironment.getApplication();
		ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
		CarAppsFeature.setAdbPaired(false);
		File files = folder.newFolder();
		AdbPairing.resetForTesting();
		AdbPairing.adbFactory = c -> new LocalAdb(files, executor, scheduler, (host, socket, key, cert, ms) -> new Transport(), 3000);
		AdbPairing.pairingPort = c -> completed(40369);
		AdbPairing.connectPort = c -> completed(41000);
		AdbPairing.recoveryScheduler = recovery::incrementAndGet;
		AdbPairing.serverStarter = c -> serverStarts.incrementAndGet();
	}

	@After public void tearDown() throws Exception {
		AdbPairing.resetForTesting();
		CarAppsFeature.setAdbPaired(false);
		executor.shutdownNow();
		scheduler.shutdownNow();
		assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
	}

	private final class Transport implements LocalAdb.Transport {
		public boolean pair(int port, String code) {
			codes.add(code);
			return pairResult;
		}
		public void run(int port, String destination) throws Exception {
			shell.add(destination);
			if (grantError != null) throw grantError;
		}
		public void close() { }
	}

	private void submit(String code) throws Exception {
		AdbPairing.submit(ctx, code).get(5, TimeUnit.SECONDS);
	}

	private Notification[] notifications() {
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		return shadowOf(nm).getAllNotifications().toArray(new Notification[0]);
	}

	private static boolean hasReplyField(Notification n) {
		return (n.actions != null) && (n.actions.length == 1) && (n.actions[0].getRemoteInputs() != null);
	}

	@Test public void validCodeIsExactlySixDigits() {
		assertTrue(AdbPairing.validCode("123456"));
		assertFalse(AdbPairing.validCode("12345"));
		assertFalse(AdbPairing.validCode("1234567"));
		assertFalse(AdbPairing.validCode("12a456"));
		assertFalse(AdbPairing.validCode(" 12345"));
		assertFalse(AdbPairing.validCode(""));
		assertFalse(AdbPairing.validCode(null));
	}

	@Test public void startPostsOneReplyNotification() {
		AdbPairing.start(ctx);
		Notification[] posted = notifications();
		assertEquals(1, posted.length);
		assertTrue(hasReplyField(posted[0]));
		assertEquals(AdbPairing.State.WAITING, AdbPairing.state());
		AdbPairing.start(ctx);
		assertEquals("Same notification is replaced, never duplicated", 1, notifications().length);
	}

	@Test public void malformedInputShowsErrorAndKeepsReplyFieldWithoutPairing() throws Exception {
		submit("12a456");
		assertEquals(AdbPairing.State.WRONG_CODE, AdbPairing.state());
		assertTrue(codes.isEmpty());
		Notification[] posted = notifications();
		assertEquals(1, posted.length);
		assertTrue("The user can type the code again", hasReplyField(posted[0]));
		assertEquals(ctx.getString(R.string.car_apps_setup_wrong_code), posted[0].extras.getString(Notification.EXTRA_TEXT));
		assertFalse(CarAppsFeature.isAdbPaired());
	}

	@Test public void wrongCodeShowsErrorThenCorrectCodeWorks() throws Exception {
		pairResult = false;
		submit("000000");
		assertEquals(AdbPairing.State.WRONG_CODE, AdbPairing.state());
		assertTrue(hasReplyField(notifications()[0]));
		assertFalse(CarAppsFeature.isAdbPaired());
		assertTrue(shell.isEmpty());
		assertEquals(0, recovery.get());
		pairResult = true;
		submit("111111");
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
		assertTrue(CarAppsFeature.isAdbPaired());
		assertEquals(2, codes.size());
	}

	@Test public void missingPairingServiceAllowsRetry() throws Exception {
		AdbPairing.pairingPort = c -> failed(new TimeoutException());
		submit("123456");
		assertEquals(AdbPairing.State.FAILED, AdbPairing.state());
		assertTrue(hasReplyField(notifications()[0]));
		assertTrue(codes.isEmpty());
		assertFalse(CarAppsFeature.isAdbPaired());
	}

	@Test public void pairedGrantsOnceMarksPairedSchedulesRecoveryAndStartsServer() throws Exception {
		submit("123456");
		assertEquals(List.of("shell:pm grant " + ctx.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS"), shell);
		assertTrue(CarAppsFeature.isAdbPaired());
		assertTrue("Persisted for the next process", ctx.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.getBoolean("CAR_APPS_ADB_PAIRED", false));
		assertEquals(1, recovery.get());
		assertEquals(1, serverStarts.get());
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
		Notification[] posted = notifications();
		assertEquals(1, posted.length);
		assertFalse("The input is replaced by the result", hasReplyField(posted[0]));
		assertEquals(ctx.getString(R.string.car_apps_setup_done), posted[0].extras.getString(Notification.EXTRA_TEXT));
	}

	@Test public void failedGrantStillCountsAsPaired() throws Exception {
		grantError = new java.io.IOException("shell failed");
		submit("123456");
		assertEquals("The grant is tried once, not retried", 1, shell.size());
		assertTrue(CarAppsFeature.isAdbPaired());
		assertEquals(1, recovery.get());
		assertEquals(1, serverStarts.get());
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
	}

	@Test public void missingConnectPortStillCountsAsPaired() throws Exception {
		AdbPairing.connectPort = c -> failed(new TimeoutException());
		submit("123456");
		assertTrue(shell.isEmpty());
		assertTrue(CarAppsFeature.isAdbPaired());
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
	}

	@Test public void notificationsOffNeverRequestsPermissionAndPostsNothing() {
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		shadowOf(nm).setNotificationsEnabled(false);
		Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);

		AdbSetupActivity activity = Robolectric.buildActivity(AdbSetupActivity.class).setup().get();

		assertNull("No permission prompt of any kind", shadowOf(activity).getLastRequestedPermission());
		assertEquals(AdbPairing.State.NOTIFICATIONS_OFF, AdbPairing.state());
		assertEquals(0, notifications().length);
		assertNotNull("Guidance with a way to the notification settings", findButton(activity.getWindow().getDecorView(),
				activity.getString(R.string.car_apps_setup_open_notif)));
		assertNull(shadowOf(activity).getLastRequestedPermission());
	}

	@Test public void developerOptionsOffShowsGuideAndAdvancesWhenEnabled() {
		Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
		org.robolectric.android.controller.ActivityController<AdbSetupActivity> controller =
				Robolectric.buildActivity(AdbSetupActivity.class).setup();
		AdbSetupActivity activity = controller.get();
		View root = activity.getWindow().getDecorView();
		Button about = findButton(root, activity.getString(R.string.car_apps_setup_open_about));
		assertNotNull(about);
		assertNull(findButton(root, activity.getString(R.string.car_apps_setup_open_dev)));
		assertEquals("The code notification is not posted before developer options exist", 0, notifications().length);
		about.performClick();
		assertEquals(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS,
				shadowOf(activity).getNextStartedActivity().getAction());

		Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
		controller.pause().resume();

		assertNotNull(findButton(root, activity.getString(R.string.car_apps_setup_open_dev)));
		assertNull(findButton(root, activity.getString(R.string.car_apps_setup_open_about)));
		assertEquals(1, notifications().length);
	}

	@Test public void pairedPhoneShowsDone() {
		CarAppsFeature.setAdbPaired(true);
		AdbSetupActivity activity = Robolectric.buildActivity(AdbSetupActivity.class).setup().get();
		assertTrue(textOf(activity.getWindow().getDecorView()).contains(activity.getString(R.string.car_apps_setup_done)));
	}

	private static Button findButton(View v, String text) {
		if ((v instanceof Button b) && text.contentEquals(b.getText()) && (b.getVisibility() == View.VISIBLE)) return b;
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) {
				Button b = findButton(g.getChildAt(i), text);
				if (b != null) return b;
			}
		}
		return null;
	}

	private static String textOf(View v) {
		if (v.getVisibility() != View.VISIBLE) return "";
		StringBuilder sb = new StringBuilder();
		if (v instanceof TextView t) sb.append(t.getText()).append('\n');
		if (v instanceof ViewGroup g) {
			for (int i = 0; i < g.getChildCount(); i++) sb.append(textOf(g.getChildAt(i)));
		}
		return sb.toString();
	}
}
