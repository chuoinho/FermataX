package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DetailedDiagnosticsPolicyTest {
	@Test
	public void schedulesOnlyAnEnabledFutureExpiry() {
		assertEquals(DetailedDiagnosticsPolicy.NO_SCHEDULE,
				DetailedDiagnosticsPolicy.expiryDelay(false, 200L, 100L));
		assertEquals(DetailedDiagnosticsPolicy.NO_SCHEDULE,
				DetailedDiagnosticsPolicy.expiryDelay(true, 0L, 100L));
		assertEquals(100L, DetailedDiagnosticsPolicy.expiryDelay(true, 200L, 100L));
		assertEquals(0L, DetailedDiagnosticsPolicy.expiryDelay(true, 100L, 100L));
		assertEquals(0L, DetailedDiagnosticsPolicy.expiryDelay(true, 99L, 100L));
	}
}
