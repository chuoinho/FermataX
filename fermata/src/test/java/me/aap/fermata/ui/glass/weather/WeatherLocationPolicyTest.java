package me.aap.fermata.ui.glass.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherLocationPolicyTest {
    private static final long HOUR_MS = 60 * 60_000L;

    @Test
    public void shouldFetchHonorsDataPresenceAndInterval() {
        long now = 1_000_000_000L;

        // No data yet -> always fetch
        assertTrue(WeatherLocationPolicy.shouldFetch(now, 0, false));
        assertTrue(WeatherLocationPolicy.shouldFetch(now, now - 1000L, false));

        // Has data but lastFetchMs <= 0 -> fetch
        assertTrue(WeatherLocationPolicy.shouldFetch(now, 0, true));
        assertTrue(WeatherLocationPolicy.shouldFetch(now, -5L, true));

        // Has data, 30 minutes old -> don't fetch
        assertFalse(WeatherLocationPolicy.shouldFetch(now, now - 30 * 60_000L, true));

        // Has data, 59 minutes old -> don't fetch
        assertFalse(WeatherLocationPolicy.shouldFetch(now, now - 59 * 60_000L, true));

        // Has data, exactly 60 minutes old -> fetch
        assertTrue(WeatherLocationPolicy.shouldFetch(now, now - HOUR_MS, true));

        // Has data, 90 minutes old -> fetch
        assertTrue(WeatherLocationPolicy.shouldFetch(now, now - 90 * 60_000L, true));
    }

    @Test
    public void nextFetchDelayMsCalculatesRemainingDuration() {
        long now = 1_000_000_000L;

        // Initial state (lastFetch <= 0) -> delay 0
        assertEquals(0L, WeatherLocationPolicy.nextFetchDelayMs(now, 0));
        assertEquals(0L, WeatherLocationPolicy.nextFetchDelayMs(now, -100L));

        // Fetched 20 minutes ago -> 40 minutes remaining
        assertEquals(40 * 60_000L, WeatherLocationPolicy.nextFetchDelayMs(now, now - 20 * 60_000L));

        // Fetched 60 minutes ago -> 0 ms
        assertEquals(0L, WeatherLocationPolicy.nextFetchDelayMs(now, now - HOUR_MS));

        // Fetched 80 minutes ago -> 0 ms (not negative)
        assertEquals(0L, WeatherLocationPolicy.nextFetchDelayMs(now, now - 80 * 60_000L));
    }

    @Test
    public void roundCoordinateProducesTwoDecimalsInUsLocale() {
        assertEquals("0.00", WeatherLocationPolicy.roundCoordinate(0.0));
        assertEquals("21.06", WeatherLocationPolicy.roundCoordinate(21.062395));
        assertEquals("105.90", WeatherLocationPolicy.roundCoordinate(105.897525));
        assertEquals("-33.87", WeatherLocationPolicy.roundCoordinate(-33.8688));
        assertEquals("-0.50", WeatherLocationPolicy.roundCoordinate(-0.5));
    }

    @Test
    public void androidAutoFetchesOnlyOnForcedCalls() {
        long now = 10 * HOUR_MS;
        // Constructor / onResume on Android Auto: no fetch even with no data.
        assertFalse(WeatherLocationPolicy.shouldRefresh(false, false, true, now, 0, false));
        // start() and the 60 minute schedule pass force.
        assertTrue(WeatherLocationPolicy.shouldRefresh(true, false, true, now, 0, false));
        assertTrue(WeatherLocationPolicy.shouldRefresh(true, true, true, now, now - 5_000, true));
    }

    @Test
    public void phoneFollowsSixtyMinuteRuleAndNeverDuplicatesARequestInFlight() {
        long now = 10 * HOUR_MS;
        assertTrue(WeatherLocationPolicy.shouldRefresh(false, false, false, now, 0, false));
        // onResume right after start(): the first request is still in flight.
        assertFalse(WeatherLocationPolicy.shouldRefresh(false, true, false, now, 0, false));
        assertFalse(WeatherLocationPolicy.shouldRefresh(false, false, false, now, now - 59 * 60_000L, true));
        assertTrue(WeatherLocationPolicy.shouldRefresh(false, false, false, now, now - HOUR_MS, true));
    }
}
