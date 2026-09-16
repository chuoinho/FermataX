package me.aap.fermata.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class AutomotiveConnectionStateTest {
	@Test
	public void visibleHostDominatesConnectionAndTransitionsAreDeduplicated() {
		AutomotiveConnectionState state = new AutomotiveConnectionState();
		List<AutomotiveConnectionState.State> events = new ArrayList<>();
		state.addListener(events::add);

		assertEquals(AutomotiveConnectionState.State.DISCONNECTED, state.state());
		Object host = new Object();
		state.connectionChanged(true);
		state.connectionChanged(true);
		state.appVisibilityChanged(host, true);
		state.connectionChanged(false);
		state.appVisibilityChanged(host, false);

		assertEquals(List.of(
				AutomotiveConnectionState.State.CONNECTED,
				AutomotiveConnectionState.State.APP_VISIBLE,
				AutomotiveConnectionState.State.DISCONNECTED), events);
	}

	@Test
	public void staleHostCannotHideReplacement() {
		AutomotiveConnectionState state = new AutomotiveConnectionState();
		Object oldHost = new Object();
		Object newHost = new Object();
		state.connectionChanged(true);
		state.appVisibilityChanged(oldHost, true);
		state.appVisibilityChanged(newHost, true);
		state.appVisibilityChanged(oldHost, false);
		assertEquals(AutomotiveConnectionState.State.APP_VISIBLE, state.state());
		state.appVisibilityChanged(newHost, false);
		assertEquals(AutomotiveConnectionState.State.CONNECTED, state.state());
	}

	@Test
	public void officialObservationAndChangesAdvanceEpochOnlyOnce() {
		AutomotiveConnectionState state = new AutomotiveConnectionState();
		assertFalse(state.hasConnectionObservation());
		assertFalse(state.isProjectionConnected());
		assertEquals(0L, state.connectionEpoch());

		state.connectionChanged(true);
		assertTrue(state.hasConnectionObservation());
		assertTrue(state.isProjectionConnected());
		assertEquals(1L, state.connectionEpoch());
		state.connectionChanged(true);
		assertEquals(1L, state.connectionEpoch());
		state.connectionChanged(false);
		assertEquals(2L, state.connectionEpoch());
	}

	@Test
	public void removedListenerReceivesNoMoreEvents() {
		AutomotiveConnectionState state = new AutomotiveConnectionState();
		List<AutomotiveConnectionState.State> events = new ArrayList<>();
		AutomotiveConnectionState.Listener listener = events::add;
		state.addListener(listener);
		state.removeListener(listener);
		state.connectionChanged(true);
		assertEquals(List.of(), events);
	}
}
