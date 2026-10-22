package me.aap.fermata.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** AC-04: 60 minute weather refresh and cache, 2 decimal coordinates, no CarFinder dependency. */
public class WeatherAcceptanceTest {
    private static final String POLICY = "me.aap.fermata.ui.glass.weather.WeatherLocationPolicy";
    private static final String CLIENT = "me.aap.fermata.ui.glass.weather.GlassOpenMeteoClient";
    private static final long MIN = 60_000L;
    private static final long NOW = 10 * 24 * 60 * MIN;

    private static boolean shouldFetch(long now, long last, boolean hasData) {
        return (Boolean) Api.staticCall(POLICY, "shouldFetch", new Class<?>[]{long.class, long.class, boolean.class}, now, last, hasData);
    }

    private static long nextDelay(long now, long last) {
        return (Long) Api.staticCall(POLICY, "nextFetchDelayMs", new Class<?>[]{long.class, long.class}, now, last);
    }

    private static String round(double v) {
        return (String) Api.staticCall(POLICY, "roundCoordinate", new Class<?>[]{double.class}, v);
    }

    @Test public void refreshesEverySixtyMinutes() {
        assertTrue("no data yet", shouldFetch(NOW, 0, false));
        assertTrue("data missing even if fetched recently", shouldFetch(NOW, NOW - 10 * MIN, false));
        assertFalse("59 minutes old is still fresh", shouldFetch(NOW, NOW - 59 * MIN, true));
        assertTrue("60 minutes old refreshes", shouldFetch(NOW, NOW - 60 * MIN, true));
    }

    @Test public void nextFetchDelayCountsDownFromSixtyMinutes() {
        assertEquals(45 * MIN, nextDelay(NOW, NOW - 15 * MIN));
        assertEquals(0, nextDelay(NOW, 0));
        assertEquals(0, nextDelay(NOW, NOW - 120 * MIN));
    }

    @Test public void coordinatesAreRoundedToTwoDecimals() {
        assertEquals("21.06", round(21.062395));
        assertEquals("105.90", round(105.897525));
        assertEquals("-33.87", round(-33.8688));
    }

    @Test public void forecastUrlCarriesRoundedCoordinatesOnly() {
        String url = (String) Api.staticCall(CLIENT, "buildUrl",
                new Class<?>[]{double.class, double.class, boolean.class}, 21.062395, 105.897525, true);
        assertTrue(url, url.contains("latitude=21.06&longitude=105.90"));
        assertFalse(url, url.contains("21.0624"));
        assertTrue(url, url.startsWith("https://api.open-meteo.com/"));
    }

    @Test public void forecastCacheLastsSixtyMinutes() throws Exception {
        Field f = Api.cls(CLIENT).getDeclaredField("CACHE_DURATION_MS");
        f.setAccessible(true);
        assertEquals(60 * MIN, f.getLong(null));
    }

    @Test public void dashboardNoLongerDependsOnCarFinderForWeather() throws Exception {
        Path p = Paths.get("src/main/java/me/aap/fermata/ui/glass/dashboard/GlassDashboardController.java");
        if (!Files.exists(p)) p = Paths.get("fermata").resolve(p);
        String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        assertFalse("GlassDashboardController must not use CarLocatorManager", src.contains("CarLocatorManager"));
        assertTrue("GlassDashboardController uses the shared hub", src.contains("LocationHub"));
    }
}
