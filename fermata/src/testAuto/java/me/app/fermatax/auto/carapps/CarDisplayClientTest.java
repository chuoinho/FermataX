package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
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
import org.robolectric.shadows.ShadowPowerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.fermata.FermataApplication.class)
public class CarDisplayClientTest {

	private Context context;
	private CarDisplayClient client;

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		client = CarDisplayClient.get();
		client.resetForTesting();
	}

	@After
	public void tearDown() {
		client.resetForTesting();
		CarAppsFeature.setState(RootState.ABSENT);
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

	@Test
	public void autoStartOnlyWhenGrantedAndCarActive() {
		assertTrue(AutoStartPolicy.shouldStart(true, RootState.GRANTED, true));
		assertFalse(AutoStartPolicy.shouldStart(false, RootState.GRANTED, true));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.AVAILABLE, true));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.ABSENT, true));
		assertFalse(AutoStartPolicy.shouldStart(true, RootState.GRANTED, false));
		assertFalse(AutoStartPolicy.shouldStart(false, RootState.AVAILABLE, false));
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

	@Test
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
		server.caps = 0x1f | ICarDisplayServer.CAP_OWN_POWER_GROUP;
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
		assertEquals(context.getPackageCodePath(), recordedAttrs.get("server_starting").get("apkPath"));

		// 2. server_connected
		AtomicBoolean released = new AtomicBoolean(false);
		int currentVersion = client.getExpectedVersion();
		FakeServerBinder server = new FakeServerBinder(currentVersion, released);
		server.caps = 0x1f;
		client.onServerBinderReceived(server);

		assertTrue(recordedEvents.contains("server_connected"));
		assertEquals(currentVersion, recordedAttrs.get("server_connected").get("version"));
		assertEquals(0x1f, recordedAttrs.get("server_connected").get("capabilities"));

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

	private static class FakeServerBinder extends Binder implements ICarDisplayServer {
		final List<Surface> surfaces = new ArrayList<>();
		private final int version;
		private final AtomicBoolean released;
		private DeathRecipient deathRecipient;
		int caps = 0x1f;

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
		public void inject(MotionEvent e) {
		}

		@Override
		public void injectKey(int keyCode) {
		}

		@Override
		public int launch(ComponentName c, int intentFlags) {
			return 0;
		}

		@Override
		public void launchOnPhone(ComponentName c, int intentFlags) {
		}

		@Override
		public String closeTopApp(boolean force) {
			return "";
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
