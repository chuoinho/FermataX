package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MainThreadWatchdogStateTest {
	@Test
	public void reportsAfterTwoMissesAndRateLimitsFurtherReports() {
		MainThreadWatchdogState state = new MainThreadWatchdogState(2, 10_000L);
		MainThreadWatchdogState.Tick first = state.onTick(0L, true);
		assertEquals(0, first.getMissedProbes());
		assertFalse(first.shouldReportStall());

		MainThreadWatchdogState.Tick second = state.onTick(2_500L, true);
		assertEquals(1, second.getMissedProbes());
		assertFalse(second.shouldReportStall());

		MainThreadWatchdogState.Tick third = state.onTick(5_000L, true);
		assertEquals(2, third.getMissedProbes());
		assertEquals(5_000L, third.getStalledForMillis());
		assertTrue(third.shouldReportStall());

		MainThreadWatchdogState.Tick fourth = state.onTick(7_500L, true);
		assertFalse(fourth.shouldReportStall());
		state.acknowledge(fourth.getProbeId());
		assertEquals(0, state.onTick(10_000L, true).getMissedProbes());
	}

	@Test
	public void inactiveStateDoesNotCreateAProbe() {
		MainThreadWatchdogState state = new MainThreadWatchdogState(2, 10_000L);
		state.onTick(0L, true);
		MainThreadWatchdogState.Tick inactive = state.onTick(2_500L, false);
		assertEquals(0L, inactive.getProbeId());
		assertFalse(inactive.shouldReportStall());
	}
}
