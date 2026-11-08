package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
