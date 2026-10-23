package me.aap.fermata.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class LocationHubTest {
    private Context ctx;
    private LocationManager lm;

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        shadowOf((Application) ctx).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        shadowOf(lm).setLocationEnabled(true);
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        ShadowSystemClock.advanceBy(Duration.ofMinutes(10));
        LocationHub.resetForTesting();
    }

    @After
    public void tearDown() {
        LocationHub.resetForTesting();
    }

    private Location createFix(String provider, double lat, double lon, long ageMs) {
        Location l = new Location(provider);
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAccuracy(5f);
        l.setSpeed(10f);
        l.setTime(System.currentTimeMillis() - ageMs);
        l.setElapsedRealtimeNanos((SystemClock.elapsedRealtime() - ageMs) * 1_000_000L);
        return l;
    }

    private void idleAll() {
        for (int i = 0; i < 3; i++) {
            ShadowLooper.idleMainLooper();
            for (android.os.Looper l : new ArrayList<>(ShadowLooper.getAllLoopers())) {
                if (l != android.os.Looper.getMainLooper() && l.getThread().isAlive()) {
                    shadowOf(l).idle();
                }
            }
        }
    }

    @Test
    public void singletonReturnsSameInstanceUntilReset() {
        LocationHub h1 = LocationHub.get(ctx);
        LocationHub h2 = LocationHub.get(ctx);
        assertSame(h1, h2);
        assertNotNull(h1.getLooperForTesting());
        assertTrue(h1.getLooperForTesting().getThread().isAlive());

        LocationHub.resetForTesting();
        LocationHub h3 = LocationHub.get(ctx);
        assertTrue(h1 != h3);
    }

    @Test
    public void acquireAndReleaseManageSingleLocationManagerRequest() {
        LocationHub hub = LocationHub.get(ctx);
        assertEquals(0, shadowOf(lm).getRequestLocationUpdateListeners().size());

        hub.acquire("consumer1", loc -> {});
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertEquals(1, hub.consumerCountForTesting());
        assertTrue(hub.isRegisteredForTesting());

        // Same tag replaces listener without extra requests
        hub.acquire("consumer1", loc -> {});
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertEquals(1, hub.consumerCountForTesting());

        // Second consumer shares request
        hub.acquire("consumer2", loc -> {});
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertEquals(2, hub.consumerCountForTesting());

        // Release first consumer
        hub.release("consumer1");
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertEquals(1, hub.consumerCountForTesting());

        // Release second consumer clears request
        hub.release("consumer2");
        assertEquals(0, shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertEquals(0, hub.consumerCountForTesting());
        assertFalse(hub.isRegisteredForTesting());
    }

    @Test
    public void dispatchDeliversToAllConsumersAndIsolatesErrors() {
        LocationHub hub = LocationHub.get(ctx);
        List<Location> receivedGood = new ArrayList<>();
        AtomicInteger badCallCount = new AtomicInteger();

        hub.acquire("bad", loc -> {
            badCallCount.incrementAndGet();
            throw new RuntimeException("Simulated error in consumer");
        });
        hub.acquire("good", receivedGood::add);

        Location fix = createFix(LocationManager.GPS_PROVIDER, 21.05, 105.85, 0);
        shadowOf(lm).simulateLocation(fix);
        idleAll();

        assertEquals(1, badCallCount.get());
        assertEquals(1, receivedGood.size());
        assertEquals(21.05, receivedGood.get(0).getLatitude(), 1e-6);
        assertNotNull(hub.latest());
        assertEquals(21.05, hub.latest().getLatitude(), 1e-6);
    }

    @Test
    public void freshSystemLocationReturnsNewestFixWithinAge() {
        LocationHub hub = LocationHub.get(ctx);

        Location freshGps = createFix(LocationManager.GPS_PROVIDER, 21.1, 105.1, 5_000L);
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, freshGps);

        Location res = hub.freshSystemLocation(10_000L);
        assertNotNull(res);
        assertEquals(21.1, res.getLatitude(), 1e-6);

        // Advance beyond max age
        ShadowSystemClock.advanceBy(Duration.ofSeconds(15));
        assertNull(hub.freshSystemLocation(10_000L));
    }

    @Test
    public void missingPermissionDoesNotCrashAndDoesNotRegister() {
        shadowOf((Application) ctx).denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );
        LocationHub hub = LocationHub.get(ctx);
        hub.acquire("consumer", loc -> {});
        assertFalse(hub.isRegisteredForTesting());
        assertEquals(0, shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertNull(hub.freshSystemLocation(60_000L));

        // Regranting and re-acquiring registers
        shadowOf((Application) ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        hub.acquire("consumer", loc -> {});
        assertTrue(hub.isRegisteredForTesting());
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());
    }
}
