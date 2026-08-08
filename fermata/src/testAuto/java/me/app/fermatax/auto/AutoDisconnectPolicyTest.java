package me.app.fermatax.auto;

import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_NATIVE;
import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_NOT_CONNECTED;
import static androidx.car.app.connection.CarConnection.CONNECTION_TYPE_PROJECTION;
import static me.app.fermatax.auto.AutoDisconnectPolicy.Action.CANCEL_SHUTDOWN;
import static me.app.fermatax.auto.AutoDisconnectPolicy.Action.NONE;
import static me.app.fermatax.auto.AutoDisconnectPolicy.Action.SCHEDULE_SHUTDOWN;
import static me.app.fermatax.auto.AutoDisconnectPolicy.State.ACTIVE;
import static me.app.fermatax.auto.AutoDisconnectPolicy.State.QUIESCENT;
import static me.app.fermatax.auto.AutoDisconnectPolicy.State.UNSEEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoDisconnectPolicyTest {
	@Test
	public void initialDisconnectedAndProviderFailureDoNotShutdown() {
		AutoDisconnectPolicy policy = new AutoDisconnectPolicy();
		assertEquals(NONE, policy.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false));
		assertEquals(NONE, policy.onConnectionType(CONNECTION_TYPE_NATIVE, false));
		assertFalse(policy.onDisconnectTimeout(true));
		assertEquals(UNSEEN, policy.stateForTests());
	}

	@Test
	public void rejectedProjectionCannotArmDisconnect() {
		AutoDisconnectPolicy policy = new AutoDisconnectPolicy();
		assertEquals(NONE, policy.onConnectionType(CONNECTION_TYPE_PROJECTION, false));
		assertEquals(NONE, policy.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false));
		assertEquals(UNSEEN, policy.stateForTests());
	}

	@Test
	public void stableConfirmedDisconnectShutsDownExactlyOnce() {
		AutoDisconnectPolicy policy = new AutoDisconnectPolicy();
		assertEquals(NONE, policy.onConnectionType(CONNECTION_TYPE_PROJECTION, true));
		assertEquals(ACTIVE, policy.stateForTests());
		assertEquals(SCHEDULE_SHUTDOWN,
				policy.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false));
		assertTrue(policy.onDisconnectTimeout(true));
		assertFalse(policy.onDisconnectTimeout(true));
		assertEquals(QUIESCENT, policy.stateForTests());
	}

	@Test
	public void reconnectDuringGraceCancelsShutdown() {
		AutoDisconnectPolicy policy = new AutoDisconnectPolicy();
		policy.onConnectionType(CONNECTION_TYPE_PROJECTION, true);
		assertEquals(SCHEDULE_SHUTDOWN,
				policy.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false));
		assertEquals(CANCEL_SHUTDOWN,
				policy.onConnectionType(CONNECTION_TYPE_PROJECTION, true));
		assertFalse(policy.onDisconnectTimeout(false));
		assertEquals(ACTIVE, policy.stateForTests());
	}

	@Test
	public void nonProjectionStateCancelsPendingDisconnect() {
		AutoDisconnectPolicy policy = new AutoDisconnectPolicy();
		policy.onConnectionType(CONNECTION_TYPE_PROJECTION, true);
		policy.onConnectionType(CONNECTION_TYPE_NOT_CONNECTED, false);
		assertEquals(CANCEL_SHUTDOWN, policy.onConnectionType(CONNECTION_TYPE_NATIVE, false));
		assertFalse(policy.onDisconnectTimeout(false));
		assertEquals(UNSEEN, policy.stateForTests());
	}
}
