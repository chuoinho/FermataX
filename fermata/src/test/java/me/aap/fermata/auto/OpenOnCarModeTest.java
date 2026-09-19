package me.aap.fermata.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OpenOnCarModeTest {
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
