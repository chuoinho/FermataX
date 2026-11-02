package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdleExitPolicyTest {

	@Test
	public void idleExitOnlyAfterTenMinutesWithoutClient() {
		assertEquals(600_000L, IdleExitPolicy.IDLE_LIMIT_MS);
		assertFalse(IdleExitPolicy.shouldExit(1_000L + 599_999L, 1_000L, false));
		assertTrue(IdleExitPolicy.shouldExit(1_000L + 600_000L, 1_000L, false));
	}

	@Test
	public void neverExitsWhileClientAttached() {
		assertFalse(IdleExitPolicy.shouldExit(Long.MAX_VALUE / 2, 0L, true));
		assertFalse(IdleExitPolicy.shouldExit(1_000_000L, 0L, true));
	}
}
