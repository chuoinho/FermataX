package me.aap.fermata.ui.glass.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class GlassWeatherRefreshTest {

    private static final long HOUR_MS = 60 * 60_000L;
    private GlassOpenMeteoClient client;

    @Before
    public void setUp() {
        client = new GlassOpenMeteoClient();
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    public void fakeClockCacheExpirationHonorsThreshold() {
        // Explicit times: ShadowSystemClock does not move System.currentTimeMillis() in test code.
        long t0 = 1_000_000_000L;
        GlassOpenMeteoClient.HourlyForecast forecast = new GlassOpenMeteoClient.HourlyForecast(
                22.0f, 0, true, 28.0f, 18.0f, Collections.emptyList(), t0);

        assertFalse(forecast.isExpired(t0));
        assertFalse(forecast.isExpired(t0 + 59 * 60_000L));
        assertTrue("expires at exactly CACHE_DURATION_MS", forecast.isExpired(t0 + HOUR_MS));
    }

    @Test
    public void cacheHitPostsToMainLooper() {
        long now = System.currentTimeMillis();
        GlassOpenMeteoClient.HourlyForecast cached = new GlassOpenMeteoClient.HourlyForecast(
                25.0f, 1, true, 30.0f, 20.0f, Collections.emptyList(), now);
        client.setCachedForecastForTesting(cached, 21.06, 105.90, true);

        AtomicReference<GlassOpenMeteoClient.HourlyForecast> delivered = new AtomicReference<>();
        AtomicBoolean ranOnMainLooper = new AtomicBoolean(false);

        // Calling fetchForecast from background thread or any thread
        Thread bgThread = new Thread(() -> client.fetchForecast(21.06, 105.90, true, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(GlassOpenMeteoClient.HourlyForecast forecast) {
                ranOnMainLooper.set(Looper.myLooper() == Looper.getMainLooper());
                delivered.set(forecast);
            }

            @Override
            public void onError(Throwable error) {}
        }));
        bgThread.start();
        try {
            bgThread.join(1000);
        } catch (InterruptedException ignored) {}

        // Before main looper idles, callback has not run yet because it was posted
        ShadowLooper.idleMainLooper();

        assertNotNull(delivered.get());
        assertEquals(25.0f, delivered.get().getCurrentTemp(), 0.01f);
        assertTrue("Cache hit callback must run on main looper", ranOnMainLooper.get());
    }

    @Test
    public void forceRefreshBypassesValidCache() {
        long now = System.currentTimeMillis();
        GlassOpenMeteoClient.HourlyForecast cached = new GlassOpenMeteoClient.HourlyForecast(
                25.0f, 1, true, 30.0f, 20.0f, Collections.emptyList(), now);
        client.setCachedForecastForTesting(cached, 21.06, 105.90, true);

        // Under forceRefresh = false, cache is returned immediately
        AtomicBoolean cacheHitInvoked = new AtomicBoolean(false);
        client.fetchForecast(21.06, 105.90, true, false, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(GlassOpenMeteoClient.HourlyForecast forecast) {
                cacheHitInvoked.set(true);
            }

            @Override
            public void onError(Throwable error) {}
        });
        ShadowLooper.idleMainLooper();
        assertTrue("Non-forced fetch should hit valid cache", cacheHitInvoked.get());

        // Under forceRefresh = true the cache is bypassed. Shut the executor first so the forced
        // request is rejected instead of opening a real connection to api.open-meteo.com.
        client.shutdown();
        AtomicBoolean forcedHitInvoked = new AtomicBoolean(false);
        client.fetchForecast(21.06, 105.90, true, true, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(GlassOpenMeteoClient.HourlyForecast forecast) {
                forcedHitInvoked.set(true);
            }

            @Override
            public void onError(Throwable error) {}
        });
        ShadowLooper.idleMainLooper();
        assertFalse("Forced fetch must bypass the in-memory cache", forcedHitInvoked.get());
    }

    @Test
    public void scheduledRunIntervalWithNetworkDelayMaintainsSixtyMinuteEffectiveCycle() {
        // Fetch started at T0 and completed 500 ms later; the next run counts from completion.
        long t0 = 1_000_000_000L;
        long lastFetchMs = t0 + 500L;

        assertEquals(HOUR_MS, WeatherLocationPolicy.nextFetchDelayMs(lastFetchMs, lastFetchMs));
        // At T0 + 60 min the cycle still has 500 ms left, so the run lands 60 min after completion, not 120.
        assertEquals(500L, WeatherLocationPolicy.nextFetchDelayMs(t0 + HOUR_MS, lastFetchMs));
        assertEquals(0L, WeatherLocationPolicy.nextFetchDelayMs(lastFetchMs + HOUR_MS, lastFetchMs));
    }
}
