package me.app.fermatax.auto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.media.service.AutomotiveRuntimeGate;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CarSessionCoordinatorTest {

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
		AutomotiveConnectionState.get().connectionChanged(true);
		assertTrue(AutomotiveConnectionState.get().isProjectionConnected());

		CarSessionCoordinator.onAutomotiveHostDisconnected(RuntimeEnvironment.getApplication());

		// RuntimeGate should remain active because projection is connected
		assertTrue(AutomotiveRuntimeGate.allowsNewWork());
	}
}
