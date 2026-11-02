package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.media.service.AutomotiveRuntimeGate;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.RootState;
import me.app.fermatax.auto.carapps.CarDisplayClient;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CarSessionCoordinatorTest {

	@Before
	public void setUp() {
		CarDisplayClient.get().resetForTesting();
	}

	@After
	public void tearDown() throws Exception {
		CarDisplayClient.get().resetForTesting();
		CarAppsFeature.setState(RootState.ABSENT);
		Field activeField = CarService.class.getDeclaredField("active");
		activeField.setAccessible(true);
		activeField.setBoolean(null, false);
	}

	@Test
	public void sessionStartedArmsRuntimeGate() {
		boolean started = CarSessionCoordinator.sessionStarted();
		assertTrue(started);
		assertTrue(AutomotiveRuntimeGate.allowsNewWork());
		assertTrue(AutomotiveRuntimeGate.isProjectionActive());
	}

	@Test
	public void hostDisconnectSkipsShutdownWhenProjectionConnected() {
		// Simulate active projection
		AutomotiveRuntimeGate.forceActive();
		AutomotiveConnectionState.get().connectionChanged(true);
		assertTrue(AutomotiveConnectionState.get().isProjectionConnected());

		CarSessionCoordinator.onAutomotiveHostDisconnected(RuntimeEnvironment.getApplication());

		// RuntimeGate should remain active because projection is connected
		assertTrue(AutomotiveRuntimeGate.allowsNewWork());
	}

	@Test
	public void shutdownReleasesCarDisplayBeforeMirror() {
		Context context = RuntimeEnvironment.getApplication();
		CarDisplayClient client = CarDisplayClient.get();

		MirrorDisplay md = MirrorDisplay.get();
		assertFalse(md.isClosed());

		client.setCommandRunnerForTest(cmd -> {});
		client.ensureRunning(context);
		assertEquals(CarDisplayClient.State.STARTING, client.state());

		AtomicBoolean carDisplayReleased = new AtomicBoolean(false);
		AtomicBoolean mirrorClosedBeforeCarDisplay = new AtomicBoolean(false);

		client.addStateListener(state -> {
			if (state == CarDisplayClient.State.IDLE) {
				carDisplayReleased.set(true);
				if (md.isClosed()) {
					mirrorClosedBeforeCarDisplay.set(true);
				}
			}
		});

		CarSessionCoordinator.sessionStarted();
		CarSessionCoordinator.shutdown(context);

		assertTrue("Car display should have been released during shutdown", carDisplayReleased.get());
		assertFalse("MirrorDisplay must NOT be closed before car_display is released",
				mirrorClosedBeforeCarDisplay.get());
		assertTrue("MirrorDisplay should be closed after shutdown completes", md.isClosed());
		assertEquals(CarDisplayClient.State.IDLE, client.state());
	}

	@Test
	public void sessionStartTriggersAutoStartHook() throws Exception {
		Context context = RuntimeEnvironment.getApplication();
		CarDisplayClient client = CarDisplayClient.get();

		// Configure auto-start conditions: carActive=true, rootState=GRANTED, pref=true
		CarAppsFeature.setState(RootState.GRANTED);
		Field activeField = CarService.class.getDeclaredField("active");
		activeField.setAccessible(true);
		activeField.setBoolean(null, true);

		AtomicInteger commandRunCount = new AtomicInteger(0);
		client.setCommandRunnerForTest(cmd -> commandRunCount.incrementAndGet());

		CarSessionCoordinator.start(context);
		CarSessionCoordinator.sessionStarted();

		assertEquals("CarDisplayClient should transition to STARTING via sessionStarted hook",
				CarDisplayClient.State.STARTING, client.state());
		assertEquals(1, commandRunCount.get());
	}

	@Test
	public void releaseWhenNoSessionIsNoOp() {
		Context context = RuntimeEnvironment.getApplication();
		CarDisplayClient client = CarDisplayClient.get();

		assertEquals(CarDisplayClient.State.IDLE, client.state());

		client.release();
		assertEquals(CarDisplayClient.State.IDLE, client.state());

		CarSessionCoordinator.shutdown(context);
		assertEquals(CarDisplayClient.State.IDLE, client.state());
	}
}
