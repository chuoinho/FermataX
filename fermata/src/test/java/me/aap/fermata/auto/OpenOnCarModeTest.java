package me.aap.fermata.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OpenOnCarModeTest {
	@Test public void removingListenerStopsNotificationsAndListenerMayReadMode() {
		OpenOnCarMode mode = new OpenOnCarMode();
		java.util.List<Boolean> events = new java.util.ArrayList<>();
		OpenOnCarMode.Listener listener = (available, enabled, revision) -> {
			assertEquals(mode.isEnabled(), enabled);
			events.add(enabled);
		};
		mode.addListener(listener);
		mode.setAvailable(true, 1);
		mode.removeListener(listener);
		mode.setEnabled(true);
		assertEquals(java.util.List.of(false, false), events);
	}
	@Test public void listenerSeesDisableAndAvailabilityImmediately() {
		OpenOnCarMode mode = new OpenOnCarMode();
		java.util.List<String> events = new java.util.ArrayList<>();
		mode.addListener((available, enabled, revision) -> events.add(available + ":" + enabled));
		mode.setAvailable(true, 1);
		mode.setEnabled(true);
		mode.setAvailable(false, 2);
		assertEquals(java.util.List.of("false:false", "true:false", "true:true", "false:false"), events);
	}
	@Test
	public void reconnectNeverReenables() {
		OpenOnCarMode mode = new OpenOnCarMode();

		mode.setAvailable(true, 1);
		mode.setEnabled(true);
		assertTrue(mode.isEnabled());
		long enabledRevision = mode.revision();

		mode.setAvailable(false, 2);
		assertFalse(mode.isEnabled());
		assertTrue(mode.revision() > enabledRevision);

		mode.setAvailable(true, 3);
		assertTrue(mode.isAvailable());
		assertFalse(mode.isEnabled());
	}

	@Test
	public void enablingWithoutHostIsIgnored() {
		OpenOnCarMode mode = new OpenOnCarMode();
		long initialRevision = mode.revision();

		mode.setEnabled(true);

		assertFalse(mode.isAvailable());
		assertFalse(mode.isEnabled());
		assertEquals(initialRevision, mode.revision());
	}

	@Test
	public void aNewEpochCancelsEnabledModeEvenWhenAvailabilityStaysTrue() {
		OpenOnCarMode mode = new OpenOnCarMode();
		mode.setAvailable(true, 1);
		mode.setEnabled(true);
		long enabledRevision = mode.revision();

		mode.setAvailable(true, 2);

		assertTrue(mode.isAvailable());
		assertFalse(mode.isEnabled());
		assertTrue(mode.revision() > enabledRevision);
	}
}
