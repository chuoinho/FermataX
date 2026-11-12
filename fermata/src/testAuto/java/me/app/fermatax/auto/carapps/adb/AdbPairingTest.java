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
import me.aap.utils.async.FutureSupplier;

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
	private final List<Boolean> pairedWhenGrantRan = new ArrayList<>();
	private final List<String> events = new ArrayList<>();
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
		AdbPairing.serverStarter = c -> {
			events.add("server");
			serverStarts.incrementAndGet();
		};
		AdbRecoveryJob.usbDebuggingWriter = (c, v) -> events.add("usb=" + v);
		shadowOf((android.app.Application) ctx).grantPermissions("android.permission.WRITE_SECURE_SETTINGS");
		Settings.Global.putInt(ctx.getContentResolver(), "adb_enabled", 0);
		// the setup screen tests here start after the user picked the way without root
		me.aap.fermata.FermataApplication.get().getPreferenceStore().applyIntPref(
				me.aap.fermata.ui.carapps.PhoneAppsGuide.PREF_MODE, me.aap.fermata.ui.carapps.PhoneAppsGuide.Mode.NO_ROOT.ordinal());
	}

	@After public void tearDown() throws Exception {
		AdbRecoveryJob.usbDebuggingWriter = AdbRecoveryJob::putAdbEnabled;
		me.aap.fermata.FermataApplication.get().getPreferenceStore().removePref(me.aap.fermata.ui.carapps.PhoneAppsGuide.PREF_MODE);
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
			pairedWhenGrantRan.add(CarAppsFeature.isAdbPaired());
			shell.add(destination);
			events.add("grant");
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

	@Test public void theJournalTellsThePairingOutcomeButNeverTheCode() throws Exception {
		me.app.fermatax.auto.carapps.PhoneAppsJournal j = me.app.fermatax.auto.carapps.JournalAssert.install();
		try {
			pairResult = false;
			submit("246813");
			submit("12a");
			pairResult = true;
			submit("135792");
			me.app.fermatax.auto.carapps.JournalAssert.has(j, "pairing", "result=wrong_code");
			me.app.fermatax.auto.carapps.JournalAssert.has(j, "pairing", "result=invalid_input");
			me.app.fermatax.auto.carapps.JournalAssert.has(j, "pairing", "result=ok");
			me.app.fermatax.auto.carapps.JournalAssert.has(j, "pm_grant", "result=ok", "granted=true");
			me.app.fermatax.auto.carapps.JournalAssert.has(j, "usb_debugging", "result=written");
			String text = String.join("\n", j.lines());
			assertFalse(text, text.contains("246813") || text.contains("135792") || text.contains("12a"));
		} finally {
			me.app.fermatax.auto.carapps.JournalAssert.uninstall();
		}
	}

	@Test public void aMissingPairingServiceIsATimeoutInTheJournal() throws Exception {
		me.app.fermatax.auto.carapps.PhoneAppsJournal j = me.app.fermatax.auto.carapps.JournalAssert.install();
		try {
			AdbPairing.pairingPort = c -> failed(new TimeoutException());
			submit("123456");
			me.app.fermatax.auto.carapps.JournalAssert.has(j, "pairing", "result=failed", "error=TimeoutException");
		} finally {
			me.app.fermatax.auto.carapps.JournalAssert.uninstall();
		}
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

	@Test public void pairedIsRecordedRightAfterPairingBeforeTheGrant() throws Exception {
		List<Boolean> pairedWhenPortLooked = new ArrayList<>();
		AdbPairing.connectPort = c -> {
			pairedWhenPortLooked.add(CarAppsFeature.isAdbPaired());
			return completed(41000);
		};
		submit("123456");
		assertEquals(List.of(true), pairedWhenPortLooked);
		assertEquals(List.of(true), pairedWhenGrantRan);
	}

	@Test public void throwingRecoveryHookDoesNotStickTheFlow() throws Exception {
		AdbPairing.recoveryScheduler = () -> { throw new IllegalStateException("scheduler down"); };
		submit("123456");
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
		assertEquals("The server is still started", 1, serverStarts.get());
		assertTrue(CarAppsFeature.isAdbPaired());
		assertFalse(hasReplyField(notifications()[0]));
	}

	@Test public void throwingServerStarterStillEndsDone() throws Exception {
		AdbPairing.serverStarter = c -> { throw new IllegalStateException("no client"); };
		submit("123456");
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
	}

	@Test public void factoryFailureEndsInFailedNotWorking() throws Exception {
		AdbPairing.adbFactory = c -> { throw new IllegalStateException("no key store"); };
		submit("123456");
		assertEquals(AdbPairing.State.FAILED, AdbPairing.state());
		assertTrue(hasReplyField(notifications()[0]));
	}

	@Test public void inputWhileWorkingIsIgnoredEvenWhenMalformed() throws Exception {
		me.aap.utils.async.Promise<Integer> port = new me.aap.utils.async.Promise<>();
		AdbPairing.pairingPort = c -> port;
		FutureSupplier<Void> first = AdbPairing.submit(ctx, "123456");
		assertEquals(AdbPairing.State.WORKING, AdbPairing.state());
		AdbPairing.submit(ctx, "abc").get(2, TimeUnit.SECONDS);
		assertEquals("A stray reply must not interrupt the running pairing", AdbPairing.State.WORKING, AdbPairing.state());
		port.complete(40369);
		first.get(5, TimeUnit.SECONDS);
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
	}

	@Test public void replyNotificationIsNotLeftOnForever() {
		AdbPairing.start(ctx);
		assertTrue(notifications()[0].getTimeoutAfter() >= 60000);
	}

	private void reply(CharSequence text) {
		android.content.Intent intent = new android.content.Intent();
		if (text != null) {
			android.os.Bundle results = new android.os.Bundle();
			results.putCharSequence("code", text);
			androidx.core.app.RemoteInput.addResultsToIntent(new androidx.core.app.RemoteInput[]{
					new androidx.core.app.RemoteInput.Builder("code").build()}, intent, results);
		}
		new AdbPairing.Receiver().onReceive(ctx, intent); // direct call: goAsync() is null here
	}

	private void awaitState(AdbPairing.State wanted) throws Exception {
		for (int i = 0; i < 100 && AdbPairing.state() != wanted; i++) Thread.sleep(50);
		assertEquals(wanted, AdbPairing.state());
	}

	@Test public void receiverTrimsTheReplyAndPairsOnce() throws Exception {
		reply(" 123456" + (char) 10);
		awaitState(AdbPairing.State.DONE);
		assertEquals(List.of("123456"), codes);
		assertEquals(1, shell.size());
	}

	@Test public void receiverShowsErrorForEmptyMissingOrInvalidReply() throws Exception {
		for (CharSequence text : new CharSequence[]{"", null, "12a456", "12345"}) {
			AdbPairing.resetForTesting();
			reply(text);
			assertEquals(String.valueOf(text), AdbPairing.State.WRONG_CODE, AdbPairing.state());
			assertTrue(hasReplyField(notifications()[0]));
		}
		assertTrue("Nothing was submitted", codes.isEmpty());
	}

	@Test public void receiverSurvivesASynchronousFailure() throws Exception {
		AdbPairing.adbFactory = c -> { throw new IllegalStateException("boom"); };
		reply("123456");
		assertEquals(AdbPairing.State.FAILED, AdbPairing.state());
	}

	@Test public void pairedTurnsUsbDebuggingOnAfterTheGrantAndBeforeTheServerStart() throws Exception {
		submit("123456");
		assertEquals(List.of("grant", "usb=1", "server"), events);
	}

	@Test public void failedGrantWithThePermissionStillTurnsUsbDebuggingOn() throws Exception {
		grantError = new java.io.IOException("shell failed");
		submit("123456");
		assertEquals(List.of("grant", "usb=1", "server"), events);
	}

	@Test public void withoutThePermissionPairingDoesNotTouchUsbDebugging() throws Exception {
		grantError = new java.io.IOException("shell failed");
		shadowOf((android.app.Application) ctx).denyPermissions("android.permission.WRITE_SECURE_SETTINGS");
		submit("123456");
		assertEquals(List.of("grant", "server"), events);
		assertEquals(AdbPairing.State.DONE, AdbPairing.state());
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

	/** The reply notification times out and can be swiped away: coming back to the screen posts it again. */
	@Test public void returningToTheSetupPostsTheNotificationAgain() throws Exception {
		Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
		org.robolectric.android.controller.ActivityController<AdbSetupActivity> controller =
				Robolectric.buildActivity(AdbSetupActivity.class).setup();
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		assertEquals(AdbPairing.State.WAITING, AdbPairing.state());

		Runnable[] reach = {
				() -> { }, // WAITING
				() -> AdbPairing.submit(ctx, "12a"), // WRONG_CODE
				() -> {
					AdbPairing.pairingPort = c -> failed(new java.io.IOException("gone"));
					AdbPairing.submit(ctx, "123456"); // FAILED
				}};
		AdbPairing.State[] reached = {AdbPairing.State.WAITING, AdbPairing.State.WRONG_CODE, AdbPairing.State.FAILED};
		for (int i = 0; i < reach.length; i++) {
			reach[i].run();
			assertEquals(reached[i], AdbPairing.state());
			nm.cancelAll(); // timed out or swiped away
			controller.pause().resume();
			assertEquals(1, notifications().length);
			assertTrue(hasReplyField(notifications()[0]));
		}
		assertFalse(CarAppsFeature.isAdbPaired());
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
