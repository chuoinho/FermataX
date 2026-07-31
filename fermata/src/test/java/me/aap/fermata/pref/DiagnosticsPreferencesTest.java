package me.aap.fermata.pref;

import static me.aap.fermata.pref.DiagnosticsPreferences.DETAILED_DURATION_MILLIS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticsPreferencesTest {
	@Test
	public void detailedDiagnosticsExpiresAfterFortyEightHours() {
		long start = 1_000_000L;
		long end = start + DETAILED_DURATION_MILLIS;

		assertTrue(DiagnosticsPreferences.evaluate(true, start, end, start));
		assertTrue(DiagnosticsPreferences.evaluate(true, start, end, end - 1L));
		assertFalse(DiagnosticsPreferences.evaluate(true, start, end, end));
	}

	@Test
	public void invalidOrClockShiftedWindowFailsClosed() {
		long start = 1_000_000L;
		long end = start + DETAILED_DURATION_MILLIS;

		assertFalse(DiagnosticsPreferences.evaluate(false, start, end, start));
		assertFalse(DiagnosticsPreferences.evaluate(true, 0L, end, start));
		assertFalse(DiagnosticsPreferences.evaluate(true, start, start, start));
		assertFalse(DiagnosticsPreferences.evaluate(true, start, end, start - 1L));
		assertFalse(DiagnosticsPreferences.evaluate(true, start,
				end + 1L, start));
	}
}
