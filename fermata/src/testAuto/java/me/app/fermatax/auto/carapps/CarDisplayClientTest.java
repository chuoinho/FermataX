package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.Surface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
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

	@Test
	public void setPanelOffCallsServerAndBroadcastScreenOffRestores() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
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
	public void serverDeathRestoresPanelPower() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		assertTrue(client.isPanelOff());

		server.triggerDeath();

		assertFalse("Panel state must be restored to on when server dies", client.isPanelOff());
		assertEquals(Boolean.TRUE, server.lastPanelPower);
	}

	@Test
	public void releaseRestoresPanelPower() {
		client.ensureRunning(context);
		FakeServerBinder server = new FakeServerBinder(client.getExpectedVersion(), new AtomicBoolean(false));
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
		server.caps = 0; // missing CAP_PANEL_POWER
		client.onServerBinderReceived(server);

		client.setPanelOff(true);
		assertFalse("Panel must remain on when CAP_PANEL_POWER is missing", client.isPanelOff());
		assertEquals(0, server.panelPowerCalls.size());
	}

	private static class FakeServerBinder extends Binder implements ICarDisplayServer {
		private final int version;
		private final AtomicBoolean released;
		private DeathRecipient deathRecipient;
		int caps = 0x1f;
		final List<Boolean> panelPowerCalls = new ArrayList<>();
		Boolean lastPanelPower = null;

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
			return 100;
		}

		@Override
		public void setSurface(Surface s, int w, int h, int dpi) {
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
		public String closeTopApp(boolean force) {
			return "";
		}

		@Override
		public void setPanelPower(boolean on) {
			panelPowerCalls.add(on);
			lastPanelPower = on;
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
