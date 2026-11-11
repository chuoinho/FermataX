package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDisplay;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowPowerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.adb.AdbPairingRequiredException;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.app.fermatax.auto.carapps.adb.AdbRecoveryJob;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class CarDisplayClientTest {

	private Context context;
	private CarDisplayClient client;
	private java.util.function.Predicate<Context> savedWifi;
	private java.util.function.Function<Context, Object> savedNetwork;
	private final List<String> usbEvents = new ArrayList<>();
	private java.util.function.ObjIntConsumer<Context> savedUsbWriter;

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		client = CarDisplayClient.get();
		client.resetForTesting();
		savedWifi = AdbRecoveryJob.wifiCheck;
		savedNetwork = AdbRecoveryJob.wifiNetwork;
		AdbRecoveryJob.wifiCheck = c -> true; // an ADB start without Wi-Fi fails at once; the real check finds none here
		savedUsbWriter = AdbRecoveryJob.usbDebuggingWriter;
		usbEvents.clear();
		AdbRecoveryJob.usbDebuggingWriter = (c, v) -> usbEvents.add("usb=" + v);
	}

	@After
	public void tearDown() {
		assertFalse("the app never turns USB debugging off", usbEvents.contains("usb=0"));
		AdbRecoveryJob.usbDebuggingWriter = savedUsbWriter;
		AdbRecoveryJob.wifiCheck = savedWifi;
		AdbRecoveryJob.wifiNetwork = savedNetwork;
		AdbRecoveryJob.refusedWifi = null;
		client.resetForTesting();
		CarAppsFeature.setState(RootState.ABSENT);
		CarAppsFeature.setAdbPaired(false);
		CarAppsFeature.setEnabled(new me.aap.utils.pref.BasicPreferenceStore(), true);
	}

	@Test
	public void secondEnsureRunningWhileStartingDoesNotStartTwice() {
		AtomicInteger runCount = new AtomicInteger(0);
		client.setCommandRunnerForTest(cmd -> runCount.incrementAndGet());

		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.STARTING, client.state());
		assertEquals(1, runCount.get());

		// Second call while STARTING should not trigger command again
		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.STARTING, client.state());
		assertEquals(1, runCount.get());
	}

	@Test public void secondSleepWithinTwoSecondsIsIgnored() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server);

		client.sleepPhone();
		client.sleepPhone();
		assertEquals(1, server.sleepCount.get()); // a second power key would wake the phone

		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
		client.sleepPhone();
		assertEquals(2, server.sleepCount.get());
	}

	@Test public void sleepPhoneWithoutAServerDoesNothing() {
		client.sleepPhone();
		assertEquals(CarDisplayClient.State.IDLE, client.state());
	}

	@Test
	public void autoStartOnlyWhenGrantedAndCarActive() {
		assertTrue(AutoStartPolicy.shouldStart(true, RootState.GRANTED, true, 34));
		assertFalse(AutoStartPolicy.shouldStart(false, RootState.GRANTED, true, 34));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.AVAILABLE, true, 34));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.ABSENT, true, 34));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.GRANTED, false, 34));
		assertFalse(AutoStartPolicy.shouldStart(false, RootState.AVAILABLE, false, 34));
		assertFalse("12L is hidden", AutoStartPolicy.shouldStart(true, RootState.GRANTED, true, 32));
	}

	@Test
	public void autoStartNeverRequestsGrant() {
		CarAppsFeature.setState(RootState.AVAILABLE);
		AtomicInteger runCount = new AtomicInteger(0);
		client.setCommandRunnerForTest(cmd -> runCount.incrementAndGet());

		client.onCarSessionStarted(context);
		assertEquals(CarDisplayClient.State.IDLE, client.state());
		assertEquals(0, runCount.get());
		assertEquals(RootState.AVAILABLE, CarAppsFeature.state());
	}

	private List<String> rootCommands = new ArrayList<>();
	private List<String> adbCommands = new ArrayList<>();

	/** Installed before the feature state changes: a fragment left by another test starts the client when it does. */
	private void useChannels(FutureSupplier<Void> adbResult) {
		rootCommands = new ArrayList<>();
		adbCommands = new ArrayList<>();
		client.setStartersForTest((ctx, cmd) -> rootCommands.add(cmd), (ctx, cmd) -> {
			adbCommands.add(cmd);
			return adbResult;
		});
	}

	private void useChannels(java.util.function.Supplier<FutureSupplier<Void>> adbResults) {
		rootCommands = new ArrayList<>();
		adbCommands = new ArrayList<>();
		client.setStartersForTest((ctx, cmd) -> rootCommands.add(cmd), (ctx, cmd) -> {
			adbCommands.add(cmd);
			return adbResults.get();
		});
	}

	/** The foreground server keeps running when the session times out, so the timeout must not stop a READY client. */
	@Test
	public void adbTimeoutAfterReadyChangesNothing() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		CarAppsFeature.setAdbPaired(true);
		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)));
		assertEquals(CarDisplayClient.State.READY, client.state());

		run.completeExceptionally(new java.util.concurrent.TimeoutException());
		ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.READY, client.state());
		assertEquals(1, adbCommands.size());
		assertTrue(CarAppsFeature.isAdbPaired());
	}

	@Test
	public void adbTimeoutWhileStartingRetriesBoundedAndOneAtATime() {
		int[] live = {0, 0}; // running now, most at once
		List<Promise<Void>> runs = new ArrayList<>();
		useChannels(() -> {
			Promise<Void> run = new Promise<>();
			runs.add(run);
			live[0]++;
			live[1] = Math.max(live[1], live[0]);
			run.onCompletion((r, err) -> live[0]--);
			return run;
		});
		CarAppsFeature.setAdbPaired(true);
		client.ensureRunning(context);

		long[] delays = {1000, 2000, 4000};
		for (int i = 0; i < delays.length; i++) {
			runs.get(i).completeExceptionally(new java.util.concurrent.TimeoutException());
			assertEquals(CarDisplayClient.State.RETRYING, client.state());
			ShadowLooper.idleMainLooper(delays[i], java.util.concurrent.TimeUnit.MILLISECONDS);
		}
		runs.get(runs.size() - 1).completeExceptionally(new java.util.concurrent.TimeoutException());
		ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals(1, live[1]);
		assertTrue(CarAppsFeature.isAdbPaired());
	}

	/** Measured on Android 14: after the first timeout the retries ran in RETRYING, where no timer fires, so it hung. */
	@Test
	public void rootStartThatNeverConnectsFailsAfterTheBoundedAttempts() {
		useChannels(new Promise<>());
		CarAppsFeature.setState(RootState.GRANTED);
		List<Object> diagnostics = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((event, attrs) -> {
			if (event.equals("server_failed")) diagnostics.add(attrs.get("reason"));
		});

		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.STARTING, client.state());
		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.MINUTES);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals("the first attempt and the three retries", 4, rootCommands.size());
		assertTrue(diagnostics.contains("max_retries_exceeded"));
	}

	@Test
	public void adbStartThatNeverConnectsFailsAfterTheBoundedAttempts() {
		useChannels(() -> Completed.completedVoid()); // delivered each time, the server never reports back
		CarAppsFeature.setAdbPaired(true);

		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.MINUTES);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals(4, adbCommands.size());
		assertTrue("the pairing is still good", CarAppsFeature.isAdbPaired());
	}

	@Test
	public void revokedAuthorizationIsRecordedEvenWhenTheClientIsNoLongerStarting() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		CarAppsFeature.setAdbPaired(true);
		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)));

		run.completeExceptionally(new AdbPairingRequiredException("revoked"));

		assertFalse(CarAppsFeature.isAdbPaired());
		assertEquals(CarDisplayClient.State.READY, client.state());
	}

	@Test
	public void revokedAuthorizationIsRecordedAfterRelease() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		CarAppsFeature.setAdbPaired(true);
		client.ensureRunning(context);
		client.release();
		assertEquals(CarDisplayClient.State.IDLE, client.state());

		run.completeExceptionally(new AdbPairingRequiredException("revoked"));

		assertFalse(CarAppsFeature.isAdbPaired());
		assertEquals(CarDisplayClient.State.IDLE, client.state());
	}

	@Test
	public void noChannelFailsFastWithoutAskingRoot() {
		useChannels(new Promise<>());
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit()
				.putBoolean(me.aap.fermata.ui.carapps.RootProbe.PREF_ROOT_GRANTED, true).commit();
		CarAppsFeature.setState(RootState.AVAILABLE);

		client.ensureRunning(context);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertTrue(rootCommands.isEmpty());
		assertTrue(adbCommands.isEmpty());
		assertTrue("root grant record is left alone", context.getSharedPreferences("fermata", Context.MODE_PRIVATE)
				.getBoolean(me.aap.fermata.ui.carapps.RootProbe.PREF_ROOT_GRANTED, false));
		context.getSharedPreferences("fermata", Context.MODE_PRIVATE).edit().clear().commit();
	}

	/** A command still running after release() may start a server: a new start waits for it, never runs a second. */
	@Test
	public void aStartAfterReleaseWaitsForTheCommandStillInFlight() {
		List<Promise<Void>> runs = new ArrayList<>();
		useChannels(() -> {
			Promise<Void> run = new Promise<>();
			runs.add(run);
			return run;
		});
		CarAppsFeature.setAdbPaired(true);
		client.ensureRunning(context);
		assertEquals(1, adbCommands.size());

		client.release();
		client.ensureRunning(context);
		assertEquals("one command at a time", 1, adbCommands.size());
		assertEquals(CarDisplayClient.State.STARTING, client.state());

		runs.get(0).completeExceptionally(new java.io.IOException("no port"));
		assertEquals("its outcome is the new start's", CarDisplayClient.State.RETRYING, client.state());
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(2, adbCommands.size());
	}

	@Test
	public void sessionStopNeverLeavesTheClientStarting() {
		List<Promise<Void>> runs = new ArrayList<>();
		useChannels(() -> {
			if (!runs.isEmpty()) return Completed.failed(new java.io.IOException("no port")); // the retries
			Promise<Void> run = new Promise<>();
			runs.add(run);
			return run;
		});
		CarAppsFeature.setAdbPaired(true);
		client.ensureRunning(context);

		client.onCarSessionStopped();
		runs.get(0).completeExceptionally(new java.io.IOException("no port"));

		assertEquals(CarDisplayClient.State.RETRYING, client.state());
		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.MINUTES);
		assertEquals(CarDisplayClient.State.FAILED, client.state());
	}

	@Test
	public void adbChannelStartsTheForegroundCommandAndNeverTouchesSu() {
		useChannels(new Promise<>());
		CarAppsFeature.setState(RootState.AVAILABLE);
		CarAppsFeature.setAdbPaired(true);

		client.ensureRunning(context);

		assertEquals(CarDisplayClient.State.STARTING, client.state());
		assertEquals(List.of(ServerCommand.buildForAdb(context.getPackageCodePath(),
				android.os.Process.myUid(), client.getExpectedVersion(), context.getPackageName())), adbCommands);
		assertTrue("su must not be asked", rootCommands.isEmpty());
	}

	@Test
	public void rootChannelKeepsTheRootCommand() {
		useChannels(new Promise<>());
		CarAppsFeature.setState(RootState.GRANTED);
		CarAppsFeature.setAdbPaired(true);

		client.ensureRunning(context);

		assertEquals(List.of(ServerCommand.buildForRoot(context.getPackageCodePath(), android.os.Process.myUid(),
				client.getExpectedVersion(), context.getPackageName(), 35)), rootCommands);
		assertTrue(adbCommands.isEmpty());
	}

	private int pings;

	/** Counts the pings; true: the ping file could be written, so the client waits for a running server. */
	private void usePing(boolean written) {
		pings = 0;
		client.setServerPingForTest(c -> {
			pings++;
			return written;
		});
	}

	/** The ADB-started server outlives the session: the next one, off Wi-Fi, takes it back without any command. */
	@Test
	public void aLiveAdbServerIsTakenBackAfterReleaseWithoutACommand() {
		useChannels(() -> Completed.completedVoid());
		CarAppsFeature.setAdbPaired(true);
		usePing(true);
		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(6, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(1, adbCommands.size());
		AtomicBoolean released = new AtomicBoolean(false);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), released), 2000);
		int epoch = client.serverEpoch();

		client.release();
		assertTrue("release still frees the display", released.get());
		assertEquals(CarDisplayClient.State.IDLE, client.state());

		AdbRecoveryJob.wifiCheck = c -> false; // the next trip, away from home
		int pinged = pings;
		client.ensureRunning(context);

		assertEquals(CarDisplayClient.State.READY, client.state());
		assertEquals("no command", 1, adbCommands.size());
		assertEquals("no ping needed", pinged, pings);
		assertTrue("the released display is gone: the car attaches again", client.serverEpoch() > epoch);
	}

	/** The app process was killed between two trips: the ping makes the running server hand its binder over again. */
	@Test
	public void afterAProcessRestartThePingedServerAnswersAndNoCommandRuns() {
		useChannels(() -> Completed.completedVoid());
		CarAppsFeature.setAdbPaired(true);
		AdbRecoveryJob.wifiCheck = c -> false;
		usePing(true);
		List<Boolean> askingWhenStarting = new ArrayList<>();
		client.addStateListener(s -> {
			if (s == CarDisplayClient.State.STARTING) askingWhenStarting.add(client.isAskingRunningServer());
		});

		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.STARTING, client.state());
		assertEquals("the car never sees 'Connect Wi-Fi' meanwhile", List.of(true), askingWhenStarting);
		assertEquals(1, pings);
		assertTrue("waiting for the running server, not for Wi-Fi", client.isAskingRunningServer());

		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)), 2000);
		assertEquals(CarDisplayClient.State.READY, client.state());
		assertFalse(client.isAskingRunningServer());

		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(CarDisplayClient.State.READY, client.state());
		assertTrue("a live server answered: no start command", adbCommands.isEmpty());
	}

	/** The session ended before the pinged server answered: its binder is kept for the next session, not made READY. */
	@Test
	public void aLatePingAnswerAfterReleaseIsKeptNotAdopted() {
		useChannels(() -> new Promise<>());
		CarAppsFeature.setAdbPaired(true);
		usePing(true);
		client.ensureRunning(context);
		client.release();

		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)), 2000);
		assertEquals("no session: not READY", CarDisplayClient.State.IDLE, client.state());

		int pinged = pings;
		client.ensureRunning(context);
		assertEquals("the next session takes it at once", CarDisplayClient.State.READY, client.state());
		assertEquals(pinged, pings);
		assertTrue(adbCommands.isEmpty());
	}

	@Test
	public void nobodyAnswersThePingSoTheServerIsStartedOnce() {
		useChannels(() -> new Promise<>());
		CarAppsFeature.setAdbPaired(true);
		usePing(true);

		client.ensureRunning(context);
		client.ensureRunning(context); // single flight while waiting
		assertEquals(1, pings);
		ShadowLooper.idleMainLooper(4, java.util.concurrent.TimeUnit.SECONDS);
		assertTrue(adbCommands.isEmpty());

		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(1, adbCommands.size());
		assertEquals(CarDisplayClient.State.STARTING, client.state());
		assertFalse(client.isAskingRunningServer());
		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(1, adbCommands.size());
	}

	@Test
	public void nobodyAnswersOffWifiEndsAskingForWifi() {
		useChannels(() -> new Promise<>());
		CarAppsFeature.setAdbPaired(true);
		AdbRecoveryJob.wifiCheck = c -> false;
		usePing(true);

		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(6, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertTrue(adbCommands.isEmpty());
	}

	/** An older server left from before an app update answers too: it is replaced by one start, not two. */
	@Test
	public void aStaleServerAnsweringThePingIsReplacedByOneStart() {
		useChannels(() -> new Promise<>());
		CarAppsFeature.setAdbPaired(true);
		usePing(true);
		AtomicBoolean released = new AtomicBoolean(false);

		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion() - 1, released), 2000);

		assertTrue(released.get());
		assertEquals(1, adbCommands.size());
		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals("the ping timer was cancelled", 1, adbCommands.size());
	}

	@Test
	public void releaseWhileAskingCancelsTheFallbackStart() {
		useChannels(() -> new Promise<>());
		CarAppsFeature.setAdbPaired(true);
		usePing(true);

		client.ensureRunning(context);
		client.release();
		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.IDLE, client.state());
		assertTrue(adbCommands.isEmpty());
		assertFalse(client.isAskingRunningServer());
	}

	@Test
	public void rootChannelNeverPingsNorKeepsAServer() {
		useChannels(new Promise<>());
		CarAppsFeature.setState(RootState.GRANTED);
		usePing(true);
		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)));
		client.release();

		client.ensureRunning(context);

		assertEquals(0, pings);
		assertEquals("root starts again as before", 2, rootCommands.size());
		assertEquals(CarDisplayClient.State.STARTING, client.state());
	}

	private int stops;

	/** Counts the stop requests to an ADB-started server (the STOP ping). */
	private void useStop() {
		stops = 0;
		client.setServerStopForTest(c -> {
			stops++;
			return true;
		});
	}

	private static void setEnabled(boolean enabled) {
		CarAppsFeature.setEnabled(new me.aap.utils.pref.BasicPreferenceStore(), enabled); // the app's backend reacts
	}

	/** Phone Apps is an addon: turned off, its server stops and no trigger starts one; on again, the pairing serves. */
	@Test
	public void turnedOffTheAdoptedServerStopsAndNothingStartsOneUntilItIsOnAgain() {
		useChannels(() -> Completed.completedVoid());
		CarAppsFeature.setAdbPaired(true);
		usePing(true);
		useStop();
		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(6, java.util.concurrent.TimeUnit.SECONDS);
		AtomicBoolean released = new AtomicBoolean(false);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), released), 2000);
		assertEquals(CarDisplayClient.State.READY, client.state());

		setEnabled(false);
		assertTrue("the display is freed through the live binder", released.get());
		assertEquals("the persistent server is told to exit", 1, stops);
		assertEquals(CarDisplayClient.State.IDLE, client.state());

		int pinged = pings;
		client.ensureRunning(context);
		client.onCarSessionStarted(context);
		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(CarDisplayClient.State.IDLE, client.state());
		assertEquals("no command", 1, adbCommands.size());
		assertEquals("the stopped server is not asked back", pinged, pings);
		assertTrue("the pairing is kept", CarAppsFeature.isAdbPaired());

		setEnabled(true);
		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(6, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals("on again: the existing pairing starts it, no setup", 2, adbCommands.size());
	}

	/** A command in flight when turned off may still start a server: it is stopped, not adopted, and the setting given back. */
	@Test
	public void aServerFromACommandInFlightWhenTurnedOffIsStoppedAndTheSettingGivenBack() {
		List<Promise<Void>> runs = new ArrayList<>();
		useChannels(() -> {
			Promise<Void> run = new Promise<>();
			runs.add(run);
			return run;
		});
		recoveryEnv(true, 0);
		useStop();
		usePing(false); // counts: no ping answered, so the command runs at once
		client.ensureRunning(context);
		assertEquals(1, adbWifi());
		int pinged = pings;

		setEnabled(false);
		assertEquals("the recovery job is cancelled", 0,
				((android.app.job.JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getAllPendingJobs().size());
		assertEquals("the command in flight owns the setting", 1, adbWifi());
		AtomicBoolean released = new AtomicBoolean(false);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), released), 2000);
		assertTrue(released.get());
		assertEquals(CarDisplayClient.State.IDLE, client.state());
		assertEquals("told to exit at the switch and again when it came up", 2, stops);
		assertEquals("never pinged while off, so it cannot hand over again: no loop", pinged, pings);

		runs.get(0).complete(null);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
		assertEquals(1, runs.size());
	}

	@Test
	public void rootPhonesStopStartingWhenTurnedOff() {
		useChannels(new Promise<>());
		CarAppsFeature.setState(RootState.GRANTED);
		useStop();
		client.ensureRunning(context);
		AtomicBoolean released = new AtomicBoolean(false);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), released));

		setEnabled(false);
		assertTrue("released: a root server then exits when idle", released.get());
		client.ensureRunning(context);
		assertEquals(1, rootCommands.size());
		assertEquals(CarDisplayClient.State.IDLE, client.state());

		setEnabled(true);
		client.ensureRunning(context);
		assertEquals(2, rootCommands.size());
	}

	@Test
	public void theStopIsASecondFileAndThePingIsLeftAlone() throws Exception {
		java.io.File dir = context.getExternalFilesDir(null);
		java.io.File ping = new java.io.File(dir, me.app.fermatax.auto.carapps.server.AppPing.FILE);
		java.io.File stop = new java.io.File(dir, me.app.fermatax.auto.carapps.server.AppPing.STOP_FILE);
		assertTrue(CarDisplayClient.pingServer(context));
		assertTrue(ping.setLastModified(1000L));
		assertTrue(stop.delete() || !stop.exists());
		assertTrue(CarDisplayClient.stopServer(context));
		assertTrue(stop.isFile());
		assertEquals("a stop never looks like a ping (no handoff, no loop)", 1000L, ping.lastModified());
	}

	@Test
	public void thePingIsAFileInTheAppsExternalFilesDir() throws Exception {
		java.io.File f = new java.io.File(context.getExternalFilesDir(null),
				me.app.fermatax.auto.carapps.server.AppPing.FILE);
		f.delete();
		assertTrue(CarDisplayClient.pingServer(context));
		assertTrue(f.isFile());
		assertTrue(f.setLastModified(1000L));
		assertTrue(CarDisplayClient.pingServer(context));
		assertTrue("the server watches its time", f.lastModified() > 1000L);
	}

	/** Readiness offers "Set up again" (a new pairing) only for a failure Wi-Fi does not explain, and not in the car. */
	@Test
	public void readinessOffersSettingUpAgainOnlyForAFailureWifiDoesNotExplain() throws Exception {
		AutoCarAppsBackend.install(context);
		useChannels(() -> Completed.failed(new java.io.IOException("Connection failed")));
		CarAppsFeature.setAdbPaired(true);
		AdbRecoveryJob.wifiCheck = c -> false;
		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertFalse("no Wi-Fi says nothing about the pairing", CarAppsFeature.serverFailed());

		AdbRecoveryJob.wifiCheck = c -> true;
		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.MINUTES);
		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertFalse(client.failedForWifi());
		assertTrue(CarAppsFeature.serverFailed());

		java.lang.reflect.Field active = me.app.fermatax.auto.CarService.class.getDeclaredField("active");
		active.setAccessible(true);
		active.setBoolean(null, true);
		try {
			assertFalse("not during a car session", CarAppsFeature.serverFailed());
		} finally {
			active.setBoolean(null, false);
		}
	}

	@Test
	public void concurrentEnsureRunningStartsOneCommand() throws Exception {
		useChannels(new Promise<>());
		CarAppsFeature.setAdbPaired(true);
		int threads = 8;
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
		List<java.util.concurrent.Future<?>> done = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			done.add(pool.submit(() -> {
				go.await();
				client.ensureRunning(context);
				return null;
			}));
		}
		go.countDown();
		for (java.util.concurrent.Future<?> f : done) f.get(10, java.util.concurrent.TimeUnit.SECONDS);
		pool.shutdown();

		assertEquals(1, adbCommands.size());
		assertEquals(CarDisplayClient.State.STARTING, client.state());
	}

	@Test
	public void revokedAdbAuthorizationFailsTheClientAndForgetsThePairing() {
		useChannels(Completed.failed(new AdbPairingRequiredException("revoked")));
		CarAppsFeature.setAdbPaired(true);

		client.ensureRunning(context);

		assertFalse(CarAppsFeature.isAdbPaired());
		assertEquals(CarDisplayClient.State.FAILED, client.state());
	}

	@Test
	public void otherAdbFailuresRetryInsteadOfForgettingThePairing() {
		useChannels(Completed.failed(new java.io.IOException("no wifi")));
		CarAppsFeature.setAdbPaired(true);

		client.ensureRunning(context);

		assertTrue(CarAppsFeature.isAdbPaired());
		assertEquals(CarDisplayClient.State.RETRYING, client.state());
	}

	@Test
	public void autoStartAlsoWorksOverAdb() {
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.ABSENT, true, 34));
		CarAppsFeature.setAdbPaired(true);
		assertTrue(AutoStartPolicy.shouldStart(true, RootState.ABSENT, true, 34));
		assertTrue(AutoStartPolicy.shouldStart(true, RootState.AVAILABLE, true, 34));
		assertFalse("12L is hidden", AutoStartPolicy.shouldStart(true, RootState.ABSENT, true, 32));
		assertFalse(AutoStartPolicy.shouldStart(false, RootState.ABSENT, true, 34));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.ABSENT, false, 34));
	}

	@Test
	public void versionMismatchReplacesOldServer() {
		AtomicBoolean released = new AtomicBoolean(false);
		int currentVersion = client.getExpectedVersion();

		FakeServerBinder oldServer = new FakeServerBinder(currentVersion - 1, released);
		client.onServerBinderReceived(oldServer);

		assertTrue("Old server with version mismatch must be released", released.get());
		assertFalse("Client should not become READY on version mismatch", client.state() == CarDisplayClient.State.READY);
	}

	@Test
	public void serverDeathMovesToRetryingThenFailedAfterThreeAttempts() {
		AtomicBoolean released = new AtomicBoolean(false);
		int currentVersion = client.getExpectedVersion();
		FakeServerBinder server = new FakeServerBinder(currentVersion, released);

		client.onServerBinderReceived(server);
		assertEquals(CarDisplayClient.State.READY, client.state());

		// Trigger death
		server.triggerDeath();
		assertEquals(CarDisplayClient.State.RETRYING, client.state());

		// Simulate 3 retry failures
		client.retryConnectionForTesting();
		assertEquals(CarDisplayClient.State.RETRYING, client.state());

		client.retryConnectionForTesting();
		assertEquals(CarDisplayClient.State.RETRYING, client.state());

		client.retryConnectionForTesting();
		assertEquals(CarDisplayClient.State.FAILED, client.state());
	}

	@Test
	public void successfulReconnectResetsAttempts() {
		AtomicBoolean released = new AtomicBoolean(false);
		int currentVersion = client.getExpectedVersion();
		FakeServerBinder server1 = new FakeServerBinder(currentVersion, released);

		client.onServerBinderReceived(server1);
		assertEquals(CarDisplayClient.State.READY, client.state());

		server1.triggerDeath();
		assertEquals(CarDisplayClient.State.RETRYING, client.state());

		FakeServerBinder server2 = new FakeServerBinder(currentVersion, new AtomicBoolean(false));
		client.onServerBinderReceived(server2);
		assertEquals(CarDisplayClient.State.READY, client.state());
		assertEquals(0, client.getRetryAttemptCountForTesting());
	}

	@Test
	public void setPanelOffCallsServerAndBroadcastScreenOffRestores() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		assertTrue("Panel state must be off", client.isPanelOff());
		assertEquals("Server setPanelPower must be called with false", Boolean.FALSE, server.lastPanelPower);

		// Now user presses phone power button -> ACTION_SCREEN_OFF broadcast
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();

		assertFalse("Panel state must be restored to on after screen off broadcast", client.isPanelOff());
		assertEquals("Server setPanelPower must be called with true", Boolean.TRUE, server.lastPanelPower);
	}

	@Test
	public void displayChangedToNonOnRestoresPanelPower() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);
		client.attach(null, 100, 100, 160);

		client.setPanelOff(true);
		assertTrue(client.isPanelOff());

		setPhoneScreen(Display.STATE_OFF);

		assertFalse("Panel state must be restored when default display turns off", client.isPanelOff());
		assertEquals(Boolean.TRUE, server.lastPanelPower);
	}

	@Test
	public void serverDeathRestoresPanelPowerAndRestoresOnReconnect() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		assertTrue(client.isPanelOff());

		server.triggerDeath();

		assertFalse("Panel state must be restored to on when server dies", client.isPanelOff());

		FakeServerBinder server2 = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server2);

		assertEquals("New server must be told to restore panel on reconnect", Boolean.TRUE, server2.lastPanelPower);
	}

	@Test
	public void releaseRestoresPanelPower() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		assertTrue(client.isPanelOff());

		client.release();

		assertFalse("Panel state must be restored to on on release", client.isPanelOff());
		assertEquals(Boolean.TRUE, server.lastPanelPower);
	}

	@Test
	public void capabilityMissingDoesNotTurnPanelOff() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17; // missing CAP_PANEL_POWER
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		assertFalse("Panel must remain on when CAP_PANEL_POWER is missing", client.isPanelOff());
		org.junit.Assert.assertNull(server.lastPanelPower);
	}

	@Test
	public void serverDeathLeavesASleepingPhoneAsleep() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_OWN_POWER_GROUP;
		client.onServerBinderReceived(server);
		client.attach(null, 100, 100, 160);

		server.triggerDeath();

		assertPhoneNotWoken();
	}

	@Test
	public void serverDeathWithPanelOffWakesThePhone() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);
		client.setPanelOff(true);

		server.triggerDeath();

		assertEquals("fermatax:panelwake", Shadows.shadowOf(latestWakeLock()).getTag());
	}

	@Test
	public void deadServerLeftThePanelOffSoTheNextScreenOffWakesThePhoneOnce() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);
		client.setPanelOff(true);
		server.triggerDeath();
		ShadowPowerManager.clearWakeLocks();

		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();
		assertEquals("fermatax:panelwake", Shadows.shadowOf(latestWakeLock()).getTag());

		ShadowPowerManager.clearWakeLocks();
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();
		assertPhoneNotWoken();

		FakeServerBinder server2 = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server2);
		org.junit.Assert.assertNull("the system already lit the panel", server2.lastPanelPower);
	}

	@Test
	public void reconnectedServerRestoresThePanelAndTheNextScreenOffIsLeftAlone() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);
		client.setPanelOff(true);
		server.triggerDeath();
		FakeServerBinder server2 = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server2);
		assertEquals(Boolean.TRUE, server2.lastPanelPower);
		ShadowPowerManager.clearWakeLocks();

		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();

		assertPhoneNotWoken();
	}

	@Test
	public void phoneSleepingAfterPanelRestoreIsNotWokenAgain() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER | ICarDisplayServer.CAP_OWN_POWER_GROUP;
		client.onServerBinderReceived(server);
		client.attach(null, 100, 100, 160);
		client.setSurface(null, 0, 0, 0);
		client.setPanelOff(true);
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();
		assertFalse(client.isPanelOff());
		ShadowPowerManager.clearWakeLocks();

		setPhoneScreen(Display.STATE_ON);
		setPhoneScreen(Display.STATE_OFF);
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();

		assertPhoneNotWoken();
	}

	@Test
	public void failedPanelOffDropsPanelMode() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		server.panelFails = true;
		client.onServerBinderReceived(server);
		client.setPanelOffRequested(true);

		client.setPanelOff(true);

		assertFalse(client.isPanelOff());
		assertFalse(client.isPanelOffRequested());
	}

	private FakeServerBinder attachWithPanelPower() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);
		client.attach(null, 100, 100, 160);
		return server;
	}

	@Test public void litPanelLetsThePhoneTimeOutDarkPanelKeepsItAwake() {
		attachWithPanelPower();
		PowerManager.WakeLock wl = latestWakeLock();
		assertTrue("lit panel: the phone's own screen timeout applies", wl == null || !wl.isHeld()
				|| !"fermatax:carapps".equals(Shadows.shadowOf(wl).getTag()));

		client.setPanelOff(true);
		wl = latestWakeLock();
		assertTrue(wl.isHeld());
		assertEquals("fermatax:carapps", Shadows.shadowOf(wl).getTag());

		client.setPanelOff(false);
		assertFalse(wl.isHeld());
	}

	@Test public void phoneTimingOutWithALitPanelIsWokenAndDarkened() {
		setPhoneScreen(android.view.Display.STATE_ON);
		FakeServerBinder server = attachWithPanelPower();
		setPhoneScreen(android.view.Display.STATE_OFF);

		assertEquals("fermatax:panelwake", Shadows.shadowOf(latestWakeLock()).getTag());
		assertEquals(java.util.List.of(PhoneDimActivity.class.getName()), server.phoneLaunches);
		assertTrue(client.isPanelOffRequested());
	}

	@Test public void lockedPhoneTimingOutIsLeftAsleep() {
		setDeviceLocked(true);
		FakeServerBinder server = attachWithPanelPower();
		setPhoneScreen(android.view.Display.STATE_OFF);
		assertPhoneNotWoken();
		assertTrue(server.phoneLaunches.isEmpty());
	}

	@Test public void powerKeyThatLightsTheDarkPanelIsNotUndoneByItsOwnScreenOff() {
		setPhoneScreen(android.view.Display.STATE_ON);
		FakeServerBinder server = attachWithPanelPower();
		client.setPanelOff(true);
		setPhoneScreen(android.view.Display.STATE_OFF); // the press: panel restored
		setPhoneScreen(android.view.Display.STATE_ON);
		setPhoneScreen(android.view.Display.STATE_OFF); // the same press, reported again
		assertTrue(server.phoneLaunches.isEmpty());

		ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(4));
		setPhoneScreen(android.view.Display.STATE_ON);
		setPhoneScreen(android.view.Display.STATE_OFF); // a later press or the timeout
		assertEquals(1, server.phoneLaunches.size());
	}

	@Test public void phoneTurnedOffAgainAndAgainIsLeftAsleep() {
		setPhoneScreen(android.view.Display.STATE_ON); // the display state outlives a test
		FakeServerBinder server = attachWithPanelPower();
		for (int i = 0; i < 5; i++) {
			setPhoneScreen(android.view.Display.STATE_OFF);
			setPhoneScreen(android.view.Display.STATE_ON);
		}
		assertEquals("at most 3 in 60 s", 3, server.phoneLaunches.size());

		ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(61));
		setPhoneScreen(android.view.Display.STATE_OFF);
		assertEquals(4, server.phoneLaunches.size());
	}

	private static void assertPhoneNotWoken() {
		PowerManager.WakeLock wl = latestWakeLock();
		if (wl != null) assertFalse("the phone must not be woken", "fermatax:panelwake".equals(Shadows.shadowOf(wl).getTag()));
	}

	private void attachDisplay() {
		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)));
		client.attach(null, 100, 100, 160);
	}

	private static void setPhoneScreen(int state) {
		Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setState(state);
		ShadowLooper.idleMainLooper();
	}

	private static PowerManager.WakeLock latestWakeLock() {
		return ShadowPowerManager.getLatestWakeLock();
	}

	private void setDeviceLocked(boolean locked) {
		org.robolectric.shadows.ShadowKeyguardManager km = Shadows.shadowOf(
				(android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE));
		km.setIsKeyguardSecure(true);
		km.setKeyguardLocked(locked);
	}

	private void sendUserPresent() {
		context.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_USER_PRESENT));
		ShadowLooper.idleMainLooper();
	}

	private void sendUnlocked() {
		setDeviceLocked(false);
		sendUserPresent();
	}

	@Test
	public void unlockBroadcastIsTrustedOverAStaleLockState() {
		Surface surface = newSurface();
		setDeviceLocked(true);
		FakeServerBinder server = attachDisplay(surface);

		sendUserPresent();

		assertFalse(client.isPhoneLocked());
		assertSame(surface, lastOf(server));
		assertTrue(latestWakeLock().isHeld());
	}

	@Test
	public void unlockReceiverAcceptsTheBroadcastFromSystemUi() {
		attachDisplay();

		boolean found = false;
		for (org.robolectric.shadows.ShadowApplication.Wrapper w :
				Shadows.shadowOf((Application) context.getApplicationContext()).getRegisteredReceivers()) {
			if (w.getIntentFilter().hasAction(android.content.Intent.ACTION_USER_PRESENT)) {
				found = true;
				// SystemUI is not the system uid: a not-exported receiver never gets it on a real phone
				assertTrue((w.flags & Context.RECEIVER_EXPORTED) != 0);
			}
		}
		assertTrue(found);
	}

	@Test public void keyguardHoldsThePhoneByVersion() {
		assertTrue(CarDisplayClient.keyguardHoldsPhone(33, true, true));
		assertFalse(CarDisplayClient.keyguardHoldsPhone(33, false, true));
		assertTrue(CarDisplayClient.keyguardHoldsPhone(31, false, true));
		assertTrue(CarDisplayClient.keyguardHoldsPhone(30, true, true));
		assertFalse(CarDisplayClient.keyguardHoldsPhone(31, true, false));
	}

	@Test public void lockAfterTheScreenChangeIsReadWhenTheScreenComesOn() {
		attachDisplay();
		setDeviceLocked(true); // no display change follows: the race seen on a real power press
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
		ShadowLooper.idleMainLooper();
		assertTrue(client.isPhoneLocked());
	}

	@Test public void lockAfterScreenOnIsReadOneSecondLater() {
		attachDisplay();
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
		ShadowLooper.idleMainLooper();
		setDeviceLocked(true);
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
		assertTrue(client.isPhoneLocked());
	}

	@Test public void unlockCancelsTheLateRead() {
		attachDisplay();
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
		ShadowLooper.idleMainLooper();
		setDeviceLocked(true); // stale: SystemUI already said the user is present
		sendUserPresent();
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
		assertFalse(client.isPhoneLocked());
	}

	@Test public void sessionEndCancelsTheLateRead() {
		attachDisplay();
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
		ShadowLooper.idleMainLooper();
		client.onCarSessionStopped();
		setDeviceLocked(true);
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
		assertFalse(client.isPhoneLocked());
		for (org.robolectric.shadows.ShadowApplication.Wrapper w :
				Shadows.shadowOf((Application) context.getApplicationContext()).getRegisteredReceivers()) {
			assertFalse(w.getIntentFilter().hasAction(Intent.ACTION_SCREEN_ON));
		}
	}

	@Test
	@Config(sdk = 33)
	public void keyguardWithoutACredentialDoesNotBlockTheCar() {
		Shadows.shadowOf((android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE))
				.setKeyguardLocked(true);

		attachDisplay();

		assertFalse(client.isPhoneLocked());
	}

	private FakeServerBinder attachDisplay(Surface surface) {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server);
		client.attach(surface, 100, 100, 160);
		return server;
	}

	private static Surface newSurface() {
		return new Surface(new android.graphics.SurfaceTexture(0));
	}

	private static Surface lastOf(FakeServerBinder server) {
		return server.surfaces.get(server.surfaces.size() - 1);
	}

	@Test
	public void lockingTakesTheCarSurfaceFromTheDisplay() {
		FakeServerBinder server = attachDisplay(newSurface());
		setPhoneScreen(Display.STATE_OFF);
		setDeviceLocked(true);

		setPhoneScreen(Display.STATE_ON);

		// with no surface the display turns off: its apps cannot be seen or keep the phone screen on
		assertEquals(null, lastOf(server));
	}

	@Test
	public void unlockingGivesTheCarSurfaceBack() {
		Surface surface = newSurface();
		setDeviceLocked(true);
		FakeServerBinder server = attachDisplay(surface);
		assertEquals(null, lastOf(server));

		sendUnlocked();

		assertSame(surface, lastOf(server));
	}

	@Test
	public void displayOpenedOnALockedPhoneNeverGetsTheSurface() {
		setDeviceLocked(true);

		FakeServerBinder server = attachDisplay(newSurface());

		assertEquals(null, server.surfaces.get(0));
	}

	@Test
	public void attachingAgainWhileLockedKeepsTheSurfaceBack() {
		setDeviceLocked(true);
		Surface surface = newSurface();
		FakeServerBinder server = attachDisplay(surface);

		client.attach(surface, 100, 100, 160);
		assertEquals(null, lastOf(server));

		sendUnlocked();
		assertSame(surface, lastOf(server));
	}

	@Test
	public void newCarSurfaceWaitsForTheUnlock() {
		setDeviceLocked(true);
		FakeServerBinder server = attachDisplay(newSurface());
		Surface newer = newSurface();

		client.setSurface(newer, 100, 100, 160);
		assertEquals(null, lastOf(server));

		sendUnlocked();
		assertSame(newer, lastOf(server));
	}

	@Test
	public void releasingWhileLockedDoesNotShowTheDisplayAgain() {
		setDeviceLocked(true);
		FakeServerBinder server = attachDisplay(newSurface());

		client.release();

		assertEquals(null, lastOf(server));
	}

	@Test
	public void lockedPhoneIsNotKeptAwake() {
		setDeviceLocked(true);

		attachDisplay();

		assertTrue(client.isPhoneLocked());
		PowerManager.WakeLock wl = latestWakeLock();
		assertTrue("the lock screen must time out normally", wl == null || !wl.isHeld());
	}

	@Test
	public void unlockingKeepsThePhoneAwakeAndTellsListeners() {
		setDeviceLocked(true);
		attachDisplay();
		AtomicBoolean toldUnlocked = new AtomicBoolean();
		client.addStateListener(s -> toldUnlocked.compareAndSet(false, !client.isPhoneLocked()));

		sendUnlocked();

		assertFalse(client.isPhoneLocked());
		assertTrue(latestWakeLock().isHeld());
		assertTrue(toldUnlocked.get());
	}

	@Test
	public void lockingWhileAsleepIsSeenWhenTheScreenComesBack() {
		attachDisplay();
		PowerManager.WakeLock wl = latestWakeLock();
		setPhoneScreen(Display.STATE_OFF);
		setDeviceLocked(true);

		setPhoneScreen(Display.STATE_ON);

		assertTrue(client.isPhoneLocked());
		assertFalse(wl.isHeld());
	}

	@Test
	public void unlockAfterReleaseIsIgnored() {
		setDeviceLocked(true);
		attachDisplay();
		client.release();

		sendUnlocked();

		PowerManager.WakeLock wl = latestWakeLock();
		assertTrue(wl == null || !wl.isHeld());
		assertFalse(client.isPhoneLocked());
	}

	@Test
	public void attachedDisplayKeepsPhoneAwakeWithoutForcingItBright() {
		attachDisplay();

		PowerManager.WakeLock wl = latestWakeLock();
		assertTrue(wl.isHeld());
		assertEquals("fermatax:carapps", Shadows.shadowOf(wl).getTag());
	}

	@Test
	public void keepAwakeFollowsTheCarSurface() {
		attachDisplay();
		PowerManager.WakeLock wl = latestWakeLock();

		client.setSurface(null, 0, 0, 0);
		assertFalse("the phone must sleep normally once the car no longer shows the display", wl.isHeld());

		client.setSurface(new Surface(new android.graphics.SurfaceTexture(0)), 100, 100, 160);
		assertTrue(latestWakeLock().isHeld());
	}

	@Test
	public void releaseLetsThePhoneSleep() {
		attachDisplay();
		PowerManager.WakeLock wl = latestWakeLock();

		client.release();

		assertFalse(wl.isHeld());
	}

	@Test
	public void serverDeathLetsThePhoneSleep() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server);
		client.attach(null, 100, 100, 160);
		PowerManager.WakeLock wl = latestWakeLock();

		server.triggerDeath();

		assertFalse(wl.isHeld());
	}

	@Test
	public void powerKeySleepIsNotUndone() {
		attachDisplay();
		PowerManager.WakeLock keepAwake = latestWakeLock();

		setPhoneScreen(Display.STATE_OFF);

		assertTrue(client.isPhoneAsleep());
		assertSame("the phone must not be woken again", keepAwake, latestWakeLock());
	}

	@Test
	public void phoneAsleepEndsWhenTheScreenComesBack() {
		attachDisplay();
		setPhoneScreen(Display.STATE_OFF);

		setPhoneScreen(Display.STATE_ON);

		assertFalse(client.isPhoneAsleep());
	}

	@Test
	public void phoneAsleepChangesNotifyListeners() {
		attachDisplay();
		AtomicInteger notified = new AtomicInteger();
		client.addStateListener(s -> notified.incrementAndGet());

		setPhoneScreen(Display.STATE_OFF);

		assertEquals(1, notified.get());
	}

	@Test
	public void screenChangesAfterReleaseAreIgnored() {
		attachDisplay();
		client.release();

		setPhoneScreen(Display.STATE_OFF);

		assertFalse(client.isPhoneAsleep());
	}

	@Test
	public void wakeScreenWakesTheSleepingPhone() {
		attachDisplay();
		setPhoneScreen(Display.STATE_OFF);

		client.wakeScreen();

		PowerManager.WakeLock wl = latestWakeLock();
		assertEquals("fermatax:panelwake", Shadows.shadowOf(wl).getTag());
		assertTrue(wl.isHeld());
	}

	private FakeServerBinder attachWithOwnPowerGroup(Surface surface) {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_OWN_POWER_GROUP;
		client.onServerBinderReceived(server);
		client.attach(surface, 100, 100, 160);
		return server;
	}

	private void lockWhileAsleep() {
		setPhoneScreen(Display.STATE_OFF);
		setDeviceLocked(true);
		setPhoneScreen(Display.STATE_ON);
	}

	@Test
	public void ownPowerGroupLeavesThePhoneFreeToSleep() {
		attachWithOwnPowerGroup(newSurface());

		PowerManager.WakeLock wl = latestWakeLock();
		assertTrue(wl == null || !wl.isHeld());
		assertTrue(client.hasOwnPowerGroup());
	}

	@Test
	public void sharedPowerGroupStillKeepsThePhoneAwake() {
		attachDisplay(newSurface());

		assertTrue(latestWakeLock().isHeld());
		assertFalse(client.hasOwnPowerGroup());
	}

	@Test
	public void ownPowerGroupDoesNotReportASleepingPhone() {
		attachWithOwnPowerGroup(newSurface());

		setPhoneScreen(Display.STATE_OFF);

		assertFalse("apps keep running, the car must not ask to continue", client.isPhoneAsleep());
	}

	@Test
	public void lockingDuringAnUnlockedCarSessionKeepsTheCarRunning() {
		Surface surface = newSurface();
		FakeServerBinder server = attachWithOwnPowerGroup(surface);

		lockWhileAsleep();

		assertFalse(client.isPhoneLocked());
		assertSame(surface, lastOf(server));
	}

	@Test
	public void openingOnALockedPhoneStillNeedsTheUnlock() {
		setDeviceLocked(true);

		FakeServerBinder server = attachWithOwnPowerGroup(newSurface());

		assertTrue(client.isPhoneLocked());
		assertEquals(null, server.surfaces.get(0));
	}

	@Test
	public void unlockOpensTheCarSessionAndALaterLockKeepsItOpen() {
		Surface surface = newSurface();
		setDeviceLocked(true);
		FakeServerBinder server = attachWithOwnPowerGroup(surface);

		sendUnlocked();
		assertFalse(client.isPhoneLocked());
		assertSame(surface, lastOf(server));

		lockWhileAsleep();
		assertFalse(client.isPhoneLocked());
		assertSame(surface, lastOf(server));
	}

	@Test
	public void reopeningWithinTheCarSessionDoesNotAskForTheUnlockAgain() {
		Surface surface = newSurface();
		FakeServerBinder server = attachWithOwnPowerGroup(surface);
		client.setSurface(null, 0, 0, 0);
		setDeviceLocked(true);

		client.attach(surface, 100, 100, 160);

		assertFalse(client.isPhoneLocked());
		assertSame(surface, lastOf(server));
	}

	@Test
	public void leavingTheCarSessionEndsTheUnlock() {
		attachWithOwnPowerGroup(newSurface());
		client.release();
		setDeviceLocked(true);

		FakeServerBinder server = attachWithOwnPowerGroup(newSurface());

		assertTrue(client.isPhoneLocked());
		assertEquals(null, server.surfaces.get(0));
	}

	@Test
	public void losingTheCarSurfaceWhileLockedDoesNotUnlockTheSession() {
		setDeviceLocked(true);
		Surface surface = newSurface();
		FakeServerBinder server = attachWithOwnPowerGroup(surface);

		client.setSurface(null, 0, 0, 0);
		client.attach(surface, 100, 100, 160);

		assertTrue(client.isPhoneLocked());
		assertEquals(null, lastOf(server));
	}

	@Test
	public void serverDeathDoesNotEndTheCarSession() {
		Surface surface = newSurface();
		FakeServerBinder first = attachWithOwnPowerGroup(surface);
		first.triggerDeath();
		setDeviceLocked(true);

		FakeServerBinder second = attachWithOwnPowerGroup(surface);

		assertFalse(client.isPhoneLocked());
		assertSame(surface, lastOf(second));
	}

	@Test
	public void diagnosticsRecordedOnServerLifecycleEvents() {
		List<String> recordedEvents = new ArrayList<>();
		java.util.Map<String, java.util.Map<String, ?>> recordedAttrs = new java.util.HashMap<>();
		client.setDiagnosticsConsumerForTest((event, attrs) -> {
			recordedEvents.add(event);
			recordedAttrs.put(event, attrs);
		});

		// 1. server_starting
		client.setCommandRunnerForTest(cmd -> {});
		client.ensureRunning(context);
		assertTrue(recordedEvents.contains("server_starting"));
		assertEquals(0, recordedAttrs.get("server_starting").get("attempt"));

		// 2. server_connected
		AtomicBoolean released = new AtomicBoolean(false);
		int currentVersion = client.getExpectedVersion();
		FakeServerBinder server = new FakeServerBinder(currentVersion, released);
		server.caps = 0x17;
		client.onServerBinderReceived(server);

		assertTrue(recordedEvents.contains("server_connected"));
		assertEquals(currentVersion, recordedAttrs.get("server_connected").get("version"));
		assertEquals(0x17, recordedAttrs.get("server_connected").get("capabilities"));

		// 3. server_retry & server_failed
		server.triggerDeath();
		client.retryConnectionForTesting();
		assertTrue(recordedEvents.contains("server_retry"));
		assertEquals(0, recordedAttrs.get("server_retry").get("attempt"));

		client.retryConnectionForTesting();
		client.retryConnectionForTesting();
		assertTrue(recordedEvents.contains("server_failed"));
		assertEquals("max_retries_exceeded", recordedAttrs.get("server_failed").get("reason"));
	}

	@Test
	public void carAppsChecksWaitForAttachBecauseTheDisplayCanLowerCapabilities() {
		java.util.Map<String, java.util.Map<String, ?>> recorded = new java.util.HashMap<>();
		client.setDiagnosticsConsumerForTest(recorded::put);

		AtomicBoolean released = new AtomicBoolean(false);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), released);
		server.caps = ICarDisplayServer.CAP_VD | ICarDisplayServer.CAP_INJECT | ICarDisplayServer.CAP_TRUSTED | ICarDisplayServer.CAP_TASKS;
		client.onServerBinderReceived(server);

		assertTrue(recorded.containsKey("server_connected"));
		assertFalse(recorded.containsKey("carapps_checks"));

		server.caps &= ~ICarDisplayServer.CAP_TRUSTED; // what attach does when the system drops the flag
		client.attach(null, 800, 480, 160);

		java.util.Map<String, ?> checks = recorded.get("carapps_checks");
		org.junit.Assert.assertNotNull(checks);
		assertEquals("OK", checks.get("inject"));
		assertEquals("OK", checks.get("tasks"));
		if (android.os.Build.VERSION.SDK_INT >= 30) assertEquals("WARN", checks.get("trusted"));
	}

	@Test
	public void connectionRecordsWhichUidTheServerRunsAs() {
		java.util.Map<String, java.util.Map<String, ?>> recorded = new java.util.HashMap<>();
		client.setDiagnosticsConsumerForTest(recorded::put);
		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)), 1000);

		assertEquals(1000, recorded.get("server_connected").get("uid"));
		assertEquals(35, recorded.get("server_connected").get("sdk"));
	}

	@Test
	public void panelOffAndItsRestoreAreRecorded() {
		List<String> events = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((e, a) -> events.add(e + a.get("state") + "/" + a.get("reason")));
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();

		assertTrue(events.toString(), events.contains("phone_paneloff/requested"));
		assertTrue(events.toString(), events.contains("phone_panelon/screen_off"));
	}

	@Test
	public void panelThatCannotTurnOffIsRecorded() {
		List<String> events = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((e, a) -> events.add(e + a.get("state")));
		client.ensureRunning(context);
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)));

		client.setPanelOff(true);
		assertTrue(events.toString(), events.contains("phone_panelunavailable"));
	}

	@Test
	public void lockChangesAreRecorded() {
		List<java.util.Map<String, ?>> states = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((e, a) -> {
			if (e.equals("phone_state")) states.add(a);
		});
		attachDisplay();
		setDeviceLocked(true);
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
		ShadowLooper.idleMainLooper();

		assertEquals(true, states.get(states.size() - 1).get("locked"));
	}

	@Test
	public void serverDeathAndPhoneSleepAreRecorded() {
		List<String> events = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((e, a) -> events.add(e));
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server);

		client.sleepPhone();
		server.triggerDeath();

		assertTrue(events.toString(), events.contains("phone_sleep"));
		assertTrue(events.toString(), events.contains("server_died"));
	}

	/** The report drops unknown categories, event names and attribute keys: an unregistered event would be lost. */
	@Test
	public void everyCarAppsEventReachesTheReport() {
		List<String> names = new ArrayList<>();
		List<Map<String, ?>> attrs = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((e, a) -> {
			names.add(e);
			attrs.add(a);
		});
		client.setServerLogReaderForTest(() -> "I CarDisplayServer: su 1000 failed: denied");
		List<Runnable> touches = new ArrayList<>();
		client.setSendExecutorForTest(touches::add);
		client.setCommandRunnerForTest(cmd -> {});

		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.caps = 0x17 | ICarDisplayServer.CAP_PANEL_POWER;
		client.onServerBinderReceived(server, 1000);
		client.attach(null, 100, 100, 160);
		client.setPanelOff(true);
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
		ShadowLooper.idleMainLooper();
		client.sleepPhone();
		setDeviceLocked(true);
		context.sendBroadcast(new Intent(Intent.ACTION_SCREEN_ON));
		ShadowLooper.idleMainLooper();
		server.launchResult = 1;
		client.launch(new ComponentName("a", "a.B"), 0);
		long now = android.os.SystemClock.uptimeMillis();
		for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_MOVE}) {
			MotionEvent e = MotionEvent.obtain(now, now, action, 100f, 200f, 0);
			client.inject(e);
			e.recycle();
		}
		server.failInject = true;
		touches.get(0).run();
		server.triggerDeath();
		client.release();
		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(8, java.util.concurrent.TimeUnit.SECONDS);

		for (String expected : new String[]{"server_starting", "server_connected", "carapps_checks", "phone_panel",
				"phone_sleep", "phone_state", "server_call_failed", "touch_queue", "server_died", "server_log",
				"server_retry", "server_failed"}) {
			assertTrue(expected + " in " + names, names.contains(expected));
		}
		for (int i = 0; i < names.size(); i++) {
			assertTrue(names.get(i), me.aap.fermata.diagnostics.ReportSchemaProbe.keepsName("carapps", names.get(i)));
			assertEquals(names.get(i), attrs.get(i).keySet(),
					me.aap.fermata.diagnostics.ReportSchemaProbe.keptAttributes("carapps", names.get(i), attrs.get(i)).keySet());
		}
	}

	@Test
	public void aFailingCallIsRecordedOnceEveryTenSeconds() {
		List<Map<String, ?>> failures = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((e, a) -> {
			if (e.equals("server_call_failed")) failures.add(a);
		});
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		server.launchResult = 1;
		client.onServerBinderReceived(server);

		client.launch(new ComponentName("a", "a.B"), 0);
		client.launch(new ComponentName("a", "a.B"), 0);
		assertEquals(1, failures.size());
		assertEquals("launch", failures.get(0).get("call"));
		assertEquals("exit_1", failures.get(0).get("error"));

		ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.SECONDS);
		client.launch(new ComponentName("a", "a.B"), 0);
		assertEquals(2, failures.size());
	}

	@Test
	public void aServerThatDoesNotStartLeavesItsLogInTheReport() {
		Map<String, Map<String, ?>> recorded = new java.util.HashMap<>();
		client.setDiagnosticsConsumerForTest(recorded::put);
		client.setServerLogReaderForTest(() -> "a\nI CarDisplayServer: su 1000 failed: denied");
		client.setCommandRunnerForTest(cmd -> {});

		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(8, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals("startup_timeout", recorded.get("server_log").get("reason"));
		assertTrue(String.valueOf(recorded.get("server_log").get("lines")).endsWith("su 1000 failed: denied"));
	}

	@Test
	public void onlyTheLastServerLogLinesAreKept() {
		assertEquals("c\nd", CarDisplayClient.lastLines("a\nb\nc\nd\n", 2));
		assertEquals("a", CarDisplayClient.lastLines("a", 20));
	}

	@Test
	public void savedCapabilitiesOfAnotherAppVersionAreUnknown() {
		android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
		android.content.SharedPreferences prefs = ctx.getSharedPreferences("fermata", android.content.Context.MODE_PRIVATE);
		int fallback = ICarDisplayServer.CAP_INJECT;

		assertEquals("nothing saved", fallback, CarDisplayClient.savedCapabilities(ctx, fallback));

		prefs.edit().putInt("CAR_APPS_CAPABILITIES", 0x17).commit();
		assertEquals("saved by a build without the version", fallback, CarDisplayClient.savedCapabilities(ctx, fallback));

		prefs.edit().putInt("CAR_APPS_CAPABILITIES_VERSION", client.getExpectedVersion() - 1).commit();
		assertEquals("saved by another version", fallback, CarDisplayClient.savedCapabilities(ctx, fallback));

		prefs.edit().putInt("CAR_APPS_CAPABILITIES_VERSION", client.getExpectedVersion()).commit();
		assertEquals(0x17, CarDisplayClient.savedCapabilities(ctx, fallback));
	}

	@Test
	public void diagnosticsRecordedOnVersionMismatch() {
		List<String> recordedEvents = new ArrayList<>();
		java.util.Map<String, java.util.Map<String, ?>> recordedAttrs = new java.util.HashMap<>();
		client.setDiagnosticsConsumerForTest((event, attrs) -> {
			recordedEvents.add(event);
			recordedAttrs.put(event, attrs);
		});

		AtomicBoolean released = new AtomicBoolean(false);
		FakeServerBinder oldServer = new FakeServerBinder(client.getExpectedVersion() - 1, released);
		client.onServerBinderReceived(oldServer);

		assertTrue(recordedEvents.contains("server_failed"));
		assertEquals("version_mismatch", recordedAttrs.get("server_failed").get("reason"));
	}

	@Test
	public void injectSendsEventViaSendExecutor() {
		FakeServerBinder server = attachDisplay(newSurface());
		client.setSendExecutorForTest(Runnable::run);

		long now = android.os.SystemClock.uptimeMillis();
		MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 200f, 0);
		MotionEvent move = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_MOVE, 120f, 220f, 0);
		MotionEvent up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, 120f, 220f, 0);

		client.inject(down);
		client.inject(move);
		client.inject(up);

		down.recycle();
		move.recycle();
		up.recycle();

		assertEquals(3, server.injected.size());
		assertEquals(MotionEvent.ACTION_DOWN, server.injected.get(0).getActionMasked());
		assertEquals(MotionEvent.ACTION_MOVE, server.injected.get(1).getActionMasked());
		assertEquals(MotionEvent.ACTION_UP, server.injected.get(2).getActionMasked());
	}

	@Test
	public void injectQueuesAndCoalescesMovesWhenSendExecutorIsDeferred() {
		FakeServerBinder server = attachDisplay(newSurface());
		List<Runnable> tasks = new ArrayList<>();
		client.setSendExecutorForTest(tasks::add);

		long now = android.os.SystemClock.uptimeMillis();
		MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 200f, 0);
		MotionEvent move1 = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_MOVE, 110f, 210f, 0);
		MotionEvent move2 = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 150f, 250f, 0);
		MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, 150f, 250f, 0);

		client.inject(down);
		client.inject(move1);
		client.inject(move2);
		client.inject(up);

		down.recycle();
		move1.recycle();
		move2.recycle();
		up.recycle();

		assertEquals("Events should be queued and not sent until executor runs", 0, server.injected.size());
		assertEquals(1, tasks.size());

		tasks.get(0).run();

		assertEquals("MOVEs should be coalesced into one", 3, server.injected.size());
		assertEquals(MotionEvent.ACTION_DOWN, server.injected.get(0).getActionMasked());
		assertEquals(MotionEvent.ACTION_MOVE, server.injected.get(1).getActionMasked());
		assertEquals(150f, server.injected.get(1).getX(), 0.01f);
		assertEquals(250f, server.injected.get(1).getY(), 0.01f);
		assertEquals(MotionEvent.ACTION_UP, server.injected.get(2).getActionMasked());
	}

	@Test
	public void touchQueueDiagnosticsFlushedOnRelease() {
		FakeServerBinder server = attachDisplay(newSurface());
		List<Runnable> tasks = new ArrayList<>();
		client.setSendExecutorForTest(tasks::add);

		List<Map<String, ?>> recorded = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((evt, attrs) -> {
			if ("touch_queue".equals(evt)) {
				recorded.add(attrs);
			}
		});

		long now = android.os.SystemClock.uptimeMillis();
		MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 200f, 0);
		MotionEvent move1 = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_MOVE, 110f, 210f, 0);
		MotionEvent move2 = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 120f, 220f, 0);

		client.inject(down);
		client.inject(move1);
		client.inject(move2);

		down.recycle();
		move1.recycle();
		move2.recycle();

		tasks.get(0).run();

		client.release();

		assertEquals(1, recorded.size());
		assertEquals(1, recorded.get(0).get("merged"));
	}

	@Test
	public void cancelReachesTheServerAfterPendingMoves() {
		FakeServerBinder server = attachDisplay(newSurface());
		List<Runnable> tasks = new ArrayList<>();
		client.setSendExecutorForTest(tasks::add);

		long now = android.os.SystemClock.uptimeMillis();
		for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL}) {
			MotionEvent e = MotionEvent.obtain(now, now, action, 100f, 200f, 0);
			client.inject(e);
			e.recycle();
		}
		tasks.get(0).run();

		assertEquals(3, server.injected.size());
		assertEquals(MotionEvent.ACTION_CANCEL, server.injected.get(2).getActionMasked());
	}

	@Test
	public void failedInjectKeepsTheCountsForDiagnostics() {
		FakeServerBinder server = attachDisplay(newSurface());
		List<Runnable> tasks = new ArrayList<>();
		client.setSendExecutorForTest(tasks::add);
		List<Map<String, ?>> recorded = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((evt, attrs) -> {
			if ("touch_queue".equals(evt)) recorded.add(attrs);
		});

		long now = android.os.SystemClock.uptimeMillis();
		for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_MOVE}) {
			MotionEvent e = MotionEvent.obtain(now, now, action, 100f, 200f, 0);
			client.inject(e);
			e.recycle();
		}
		server.failInject = true;
		tasks.get(0).run();

		assertEquals(1, recorded.size());
		assertEquals(1, recorded.get(0).get("merged"));
	}

	// Non-root recovery: wireless debugging around the start, and the job that restarts the server

	private static final String ADB_WIFI = "adb_wifi_enabled";
	private final List<Boolean> finished = java.util.Collections.synchronizedList(new ArrayList<>());

	private android.content.SharedPreferences prefs() {
		return context.getSharedPreferences("fermata", Context.MODE_PRIVATE);
	}

	private int adbWifi() {
		return android.provider.Settings.Global.getInt(context.getContentResolver(), ADB_WIFI, 0);
	}

	private int tries() {
		return prefs().getInt(AdbRecoveryJob.PREF_TRIES, 0);
	}

	private boolean markerPresent() {
		return prefs().contains(AdbRecoveryJob.PREF_RESTORE);
	}

	private void recoveryEnv(boolean wifi, int adbWifiBefore) {
		prefs().edit().clear().commit();
		CarAppsFeature.setState(RootState.ABSENT); // another test class may have left root granted
		AdbRecoveryJob.wifiCheck = c -> wifi;
		Shadows.shadowOf((Application) context).grantPermissions("android.permission.WRITE_SECURE_SETTINGS");
		android.provider.Settings.Global.putInt(context.getContentResolver(), ADB_WIFI, adbWifiBefore);
		CarAppsFeature.setAdbPaired(true);
	}

	private void serverUp() {
		client.onServerBinderReceived(new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false)));
	}

	@Test
	public void adbStartTurnsWirelessDebuggingOnForTheCommandOnly() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		recoveryEnv(true, 0);

		client.ensureRunning(context);
		assertEquals(1, adbCommands.size());
		assertEquals(1, adbWifi());
		assertTrue(markerPresent());

		run.complete(null);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void failedConnectKeepsWirelessDebuggingOnForTheRetry() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);

		client.ensureRunning(context);

		assertEquals(CarDisplayClient.State.RETRYING, client.state());
		assertEquals("a retry of the same start must not switch it off and on again", 1, adbWifi());
		assertTrue(markerPresent());
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
		assertEquals(2, adbCommands.size());
		assertEquals(1, adbWifi());
	}

	/**
	 * On a Wi-Fi it does not trust yet Android switches wireless debugging off again and shows its own dialog: a retry,
	 * or the next start, would show it again. So that network counts as no Wi-Fi ("Connect Wi-Fi once to start").
	 */
	@Test
	public void wirelessDebuggingRefusedOnThisWifiIsNotWrittenThereAgain() {
		useChannels(() -> {
			android.provider.Settings.Global.putInt(context.getContentResolver(), ADB_WIFI, 0); // what Android does
			return Completed.failed(new java.util.concurrent.TimeoutException("no port"));
		});
		recoveryEnv(true, 0);
		Object[] network = {"cafe"};
		AdbRecoveryJob.wifiNetwork = c -> network[0];
		List<Object> reasons = new ArrayList<>();
		client.setDiagnosticsConsumerForTest((event, attrs) -> {
			if (event.equals("server_failed")) reasons.add(attrs.get("reason"));
		});

		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals("no retry", 1, adbCommands.size());
		assertEquals(List.of("adb_wifi_refused"), reasons);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
		assertFalse("shown as no Wi-Fi", AdbRecoveryJob.hasWifi(context));
		assertTrue("the pairing is fine: no Set up again", client.failedForWifi());

		client.ensureRunning(context);
		assertEquals("the next start does not write it again there", 1, adbCommands.size());
		assertEquals(0, adbWifi());

		android.provider.Settings.Global.putInt(context.getContentResolver(), ADB_WIFI, 1); // the user allowed it
		assertTrue(AdbRecoveryJob.hasWifi(context));
		android.provider.Settings.Global.putInt(context.getContentResolver(), ADB_WIFI, 0);
		network[0] = "home";
		assertTrue("another network is tried", AdbRecoveryJob.hasWifi(context));
		client.ensureRunning(context);
		assertEquals(2, adbCommands.size());
	}

	@Test
	public void wirelessDebuggingGoesBackWhenTheRetriesRunOut() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);

		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void releaseBetweenRetriesGivesWirelessDebuggingBack() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		assertEquals(1, adbWifi());

		client.release();

		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void lostWifiBetweenRetriesFailsAtOnceAndGivesTheSettingBack() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		AdbRecoveryJob.wifiCheck = c -> false;

		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals("no second command without Wi-Fi", 1, adbCommands.size());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void noWifiFailsAtOnceWithoutWaitingForAPortAndQueuesTheJob() {
		useChannels(() -> new Promise<>());
		recoveryEnv(false, 0);

		client.ensureRunning(context);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals(0, adbCommands.size());
		assertEquals("connecting Wi-Fi starts the server", 1,
				((android.app.job.JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).getAllPendingJobs().size());
	}

	@Test
	public void releaseLeavesTheSettingToTheCommandInFlight() {
		List<Promise<Void>> runs = new ArrayList<>();
		useChannels(() -> {
			Promise<Void> run = new Promise<>();
			runs.add(run);
			return run;
		});
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		client.release(); // the command still runs and owns the setting
		assertEquals(1, adbWifi());
		client.ensureRunning(context);
		assertEquals(1, runs.size());

		runs.get(0).complete(null);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void aLateBinderBetweenRetriesGivesTheSettingBack() {
		useChannels(() -> Completed.failed(new java.io.IOException("timed out, the server may already be up")));
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.RETRYING, client.state());
		assertEquals(1, adbWifi());

		serverUp();

		assertEquals(CarDisplayClient.State.READY, client.state());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void aBinderWhileACommandIsInFlightLeavesTheSettingToThatCommand() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		recoveryEnv(true, 0);
		client.ensureRunning(context);

		serverUp();
		assertEquals(1, adbWifi());
		assertTrue(markerPresent());

		run.complete(null);
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void losingThePairingBetweenRetriesGivesTheSettingBack() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		assertEquals(1, adbWifi());

		CarAppsFeature.setAdbPaired(false); // no channel any more
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void rootAppearingBetweenRetriesGivesTheSettingBack() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		assertEquals(1, adbWifi());

		CarAppsFeature.setState(RootState.GRANTED);
		ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(1, rootCommands.size());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void noWifiEndGivesTheSettingBackEvenWithoutARetryRunning() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		run.completeExceptionally(new java.io.IOException("no port")); // RETRYING, setting left on for the retry
		assertEquals(1, adbWifi());
		AdbRecoveryJob.wifiCheck = c -> false;

		client.ensureRunning(context); // a new start without Wi-Fi

		assertEquals(CarDisplayClient.State.FAILED, client.state());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void jobDoesNotRestoreUnderARetryInFlight() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);
		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.RETRYING, client.state());

		AdbRecoveryJob.start(context, finished::add);

		assertEquals(1, adbWifi());
		assertTrue("the retrying start still owns the marker", markerPresent());
	}

	@Test
	public void jobFinishesOnlyAfterItsCallerReturned() {
		useChannels(() -> Completed.failed(new AdbPairingRequiredException("revoked")));
		recoveryEnv(true, 0);

		AdbRecoveryJob.start(context, finished::add);
		assertTrue("jobFinished must not run inside onStartJob", finished.isEmpty());

		ShadowLooper.idleMainLooper();
		assertEquals(List.of(false), finished);
	}

	@Test
	public void wirelessDebuggingTheUserTurnedOnStaysOn() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		recoveryEnv(true, 1);

		client.ensureRunning(context);
		run.complete(null);

		assertEquals(1, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void adbStartTurnsUsbDebuggingOnBeforeWirelessDebuggingAndTheCommand() {
		Promise<Void> run = new Promise<>();
		recoveryEnv(true, 0);
		client.setStartersForTest((ctx, cmd) -> { }, (ctx, cmd) -> {
			usbEvents.add("command wifi=" + adbWifi());
			return run;
		});
		AdbRecoveryJob.usbDebuggingWriter = (c, v) -> usbEvents.add("usb=" + v + " wifi=" + adbWifi());

		client.ensureRunning(context);

		assertEquals(List.of("usb=1 wifi=0", "command wifi=1"), usbEvents);
		run.complete(null);
		AdbRecoveryJob.usbDebuggingWriter = (c, v) -> usbEvents.add("usb=" + v);
	}

	@Test
	public void everyRetryAsksAgainButWritesOnlyWhenItIsOff() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);

		client.ensureRunning(context);
		ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals("the test writer leaves the store alone, so each attempt asks once", adbCommands.size(), usbEvents.size());
		assertTrue(adbCommands.size() > 1);
	}

	@Test
	public void usbDebuggingTheUserLeftOnIsNotWritten() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);
		android.provider.Settings.Global.putInt(context.getContentResolver(), "adb_enabled", 1);

		client.ensureRunning(context);

		assertTrue(usbEvents.isEmpty());
	}

	@Test
	public void rootPhonesNeverTouchUsbDebugging() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);
		CarAppsFeature.setState(RootState.GRANTED);

		client.ensureRunning(context);

		assertEquals(1, rootCommands.size());
		assertTrue(usbEvents.isEmpty());
	}

	@Test
	public void anUnpairedPhoneNeverTouchesUsbDebugging() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);
		CarAppsFeature.setAdbPaired(false);

		client.ensureRunning(context);

		assertTrue(usbEvents.isEmpty());
	}

	@Test
	public void withoutWifiTheSettingIsNotTouched() {
		useChannels(() -> new Promise<>());
		recoveryEnv(false, 0);

		client.ensureRunning(context);

		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void jobStartsOneCommandAndEndsWhenTheServerIsUp() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		recoveryEnv(true, 0);

		assertNotNull(AdbRecoveryJob.start(context, finished::add));
		assertEquals(1, adbCommands.size());
		assertTrue(finished.isEmpty());

		run.complete(null);
		serverUp();
		ShadowLooper.idleMainLooper();

		assertEquals(List.of(false), finished);
		assertEquals(0, tries());
		assertEquals(0, adbWifi());
	}

	@Test
	public void jobAfterASessionStartedTheServerStartsNothingMore() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);

		client.ensureRunning(context);
		assertNotNull("it waits for the start in flight", AdbRecoveryJob.start(context, finished::add));

		assertEquals(1, adbCommands.size());
		assertEquals("the job must not hand the setting back under the running start", 1, adbWifi());
	}

	@Test
	public void sessionAfterTheJobStartsNothingMore() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);

		AdbRecoveryJob.start(context, finished::add);
		client.onCarSessionStarted(context);
		client.ensureRunning(context);

		assertEquals(1, adbCommands.size());
	}

	@Test
	public void jobAndSessionRacingStartOneCommand() throws Exception {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);
		java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
		List<Thread> threads = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			boolean job = (i % 2 == 0);
			Thread t = new Thread(() -> {
				try {
					go.await();
				} catch (InterruptedException e) {
					return;
				}
				if (job) AdbRecoveryJob.start(context, finished::add);
				else client.ensureRunning(context);
			});
			threads.add(t);
			t.start();
		}
		go.countDown();
		for (Thread t : threads) t.join(5000);

		assertEquals(1, adbCommands.size());
	}

	@Test
	public void jobTriesThreeTimesThenStops() {
		useChannels(() -> Completed.failed(new java.io.IOException("no port")));
		recoveryEnv(true, 0);

		for (int i = 0; i < 3; i++) {
			AdbRecoveryJob.start(context, finished::add);
			ShadowLooper.idleMainLooper(30, java.util.concurrent.TimeUnit.SECONDS); // the client's own bounded retries end in FAILED
			assertEquals(CarDisplayClient.State.FAILED, client.state());
		}

		assertEquals(List.of(true, true, false), finished);
		assertEquals("a later boot starts counting again", 0, tries());
		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	@Test
	public void revokedAuthorizationStopsTheJobAndLeavesSetup() {
		useChannels(() -> Completed.failed(new AdbPairingRequiredException("revoked")));
		recoveryEnv(true, 0);

		AdbRecoveryJob.start(context, finished::add);
		ShadowLooper.idleMainLooper();

		assertEquals(List.of(false), finished);
		assertFalse(CarAppsFeature.isAdbPaired());
		assertEquals(0, tries());
		assertEquals(0, adbWifi());
	}

	@Test
	public void jobHasNothingToDoWhenItCannotHelp() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);

		CarAppsFeature.setAdbPaired(false);
		assertNull("not paired", AdbRecoveryJob.start(context, finished::add));
		CarAppsFeature.setAdbPaired(true);
		CarAppsFeature.setState(RootState.GRANTED);
		assertNull("root phones never use the job", AdbRecoveryJob.start(context, finished::add));
		CarAppsFeature.setState(RootState.ABSENT);
		AdbRecoveryJob.wifiCheck = c -> false;
		assertNull("no Wi-Fi", AdbRecoveryJob.start(context, finished::add));
		AdbRecoveryJob.wifiCheck = c -> true;
		client.ensureRunning(context);
		serverUp();
		assertNull("the server runs", AdbRecoveryJob.start(context, finished::add));

		assertEquals(1, adbCommands.size());
		assertTrue(rootCommands.isEmpty());
		assertTrue(finished.isEmpty());
	}

	@Test
	public void jobThatNeverHearsBackCountsAsAFailure() {
		useChannels(() -> new Promise<>());
		recoveryEnv(true, 0);

		AdbRecoveryJob.start(context, finished::add);
		ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.SECONDS);
		assertTrue("the slowest start (4 attempts of port wait and watchdog, one re-registration) fits", finished.isEmpty());
		ShadowLooper.idleMainLooper(55, java.util.concurrent.TimeUnit.SECONDS);

		assertEquals(List.of(true), finished);
		assertEquals(1, tries());
	}

	@Test
	public void aStoppedJobStopsListening() {
		Promise<Void> run = new Promise<>();
		useChannels(() -> run);
		recoveryEnv(true, 0);

		AdbRecoveryJob.Run job = AdbRecoveryJob.start(context, finished::add);
		job.cancel();
		run.complete(null);
		serverUp();
		ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.SECONDS);

		assertTrue(finished.isEmpty());
	}

	@Test
	public void nextJobRepairsASettingAKilledRunLeftOn() {
		recoveryEnv(false, 1);
		prefs().edit().putInt(AdbRecoveryJob.PREF_RESTORE, 0).commit();

		assertNull(AdbRecoveryJob.start(context, finished::add));

		assertEquals(0, adbWifi());
		assertFalse(markerPresent());
	}

	private static class FakeServerBinder extends Binder implements ICarDisplayServer {
		final List<Surface> surfaces = new ArrayList<>();
		final List<MotionEvent> injected = new ArrayList<>();
		boolean failInject;
		int launchResult;
		private final int version;
		private final AtomicBoolean released;
		private DeathRecipient deathRecipient;
		int caps = 0x17;
		final java.util.List<String> phoneLaunches = new java.util.ArrayList<>();
		Boolean lastPanelPower;
		boolean panelFails;
		final AtomicInteger sleepCount = new AtomicInteger();

		FakeServerBinder(int version, AtomicBoolean released) {
			this.version = version;
			this.released = released;
			attachInterface(this, ICarDisplayServer.DESCRIPTOR);
		}

		void triggerDeath() {
			if (deathRecipient != null) {
				deathRecipient.binderDied();
			}
		}

		@Override
		public void linkToDeath(DeathRecipient recipient, int flags) {
			this.deathRecipient = recipient;
		}

		@Override
		public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
			this.deathRecipient = null;
			return true;
		}

		@Override
		public boolean pingBinder() {
			return true;
		}

		@Override
		public int capabilities() {
			return caps;
		}

		@Override
		public int attach(Surface s, int w, int h, int dpi, IBinder client) {
			surfaces.add(s);
			return 100;
		}

		@Override
		public void setSurface(Surface s, int w, int h, int dpi) {
			surfaces.add(s);
		}

		@Override
		public void inject(MotionEvent e) throws android.os.RemoteException {
			if (failInject) throw new android.os.RemoteException();
			injected.add(MotionEvent.obtain(e));
		}

		@Override
		public void injectKey(int keyCode) {
		}

		@Override
		public int launch(ComponentName c, int intentFlags) {
			return launchResult;
		}

		@Override
		public void launchOnPhone(ComponentName c, int intentFlags) {
			phoneLaunches.add(c.getClassName());
		}

		@Override
		public String closeTopApp(boolean force) {
			return "";
		}

		@Override
		public void setPanelPower(boolean on) {
			lastPanelPower = on;
			if (panelFails && !on) caps &= ~CAP_PANEL_POWER;
		}

		@Override
		public void sleepPhone() {
			sleepCount.incrementAndGet();
		}

		@Override
		public void release() {
			released.set(true);
		}

		@Override
		public int serverVersion() {
			return version;
		}

		@Override
		public IBinder asBinder() {
			return this;
		}
	}
}
