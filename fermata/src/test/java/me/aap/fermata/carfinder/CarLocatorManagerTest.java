package me.aap.fermata.carfinder;

import static org.junit.Assert.assertEquals;
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

import me.aap.fermata.location.LocationHub;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class CarLocatorManagerTest {
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
        ShadowSystemClock.advanceBy(Duration.ofMinutes(15));
        LocationHub.resetForTesting();
        CarLocatorManager.resetForTesting();
    }

    @After
    public void tearDown() {
        CarLocatorManager.resetForTesting();
        LocationHub.resetForTesting();
    }

    private Location createFix(String provider, double lat, double lon, long ageMs) {
        Location l = new Location(provider);
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAccuracy(5f);
        l.setSpeed(15f);
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
    public void chooseParkingInputLogic() {
        long now = SystemClock.elapsedRealtime();

        // 1. Fresh buffer (10s old) + fresh system (5s old) -> buffer wins
        Location bufFresh = createFix("gps", 10.0, 20.0, 10_000L);
        Location sysFresh = createFix("gps", 30.0, 40.0, 5_000L);
        assertSame(bufFresh, CarLocatorManager.chooseParkingInput(bufFresh, sysFresh, now));

        // 2. Stale buffer (40s old) + fresh system (5s old) -> system wins
        Location bufStale = createFix("gps", 10.0, 20.0, 40_000L);
        assertSame(sysFresh, CarLocatorManager.chooseParkingInput(bufStale, sysFresh, now));

        // 3. Null buffer + fresh system -> system wins
        assertSame(sysFresh, CarLocatorManager.chooseParkingInput(null, sysFresh, now));

        // 4. Stale buffer (40s old) + stale system (35s old) -> buffer returned
        Location sysStale = createFix("gps", 30.0, 40.0, 35_000L);
        assertSame(bufStale, CarLocatorManager.chooseParkingInput(bufStale, sysStale, now));

        // 5. Null buffer + stale system -> null returned
        assertNull(CarLocatorManager.chooseParkingInput(null, sysStale, now));
    }

    @Test
    public void queryBestKnownUserLocationHonorsPrecedenceAndStaleness() {
        CarLocatorManager mgr = CarLocatorManager.get();

        // Initially no location anywhere
        assertNull(mgr.queryBestKnownUserLocation(ctx));

        // 1. hub.latest() fresh <= 2 min takes highest precedence
        LocationHub hub = LocationHub.get(ctx);
        hub.acquire("test", l -> {});
        Location fixHub = createFix(LocationManager.GPS_PROVIDER, 21.01, 105.01, 0);
        shadowOf(lm).simulateLocation(fixHub);
        idleAll();

        Location best = mgr.queryBestKnownUserLocation(ctx);
        assertNotNull(best);
        assertEquals(21.01, best.getLatitude(), 1e-6);

        // 2. When hub.latest() is > 2 min old, freshSystemLocation(10 min) is preferred
        ShadowSystemClock.advanceBy(Duration.ofMinutes(3));
        Location fixSys = createFix(LocationManager.GPS_PROVIDER, 21.02, 105.02, 10_000L);
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, fixSys);

        best = mgr.queryBestKnownUserLocation(ctx);
        assertNotNull(best);
        assertEquals(21.02, best.getLatitude(), 1e-6);

        // 3. When system fix is also older than 10 min, check currentUserLocation (must be <= 2 min)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(15));
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, null);
        assertNull(mgr.queryBestKnownUserLocation(ctx));
    }

    @Test
    public void trackingWithLocationHubDispatchesToListeners() {
        CarLocatorManager mgr = CarLocatorManager.get();
        List<Location> received = new ArrayList<>();
        CarLocatorManager.UserLocationListener listener = received::add;

        mgr.startTrackingUserLocation(ctx, listener);
        idleAll();
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());

        Location fix = createFix(LocationManager.GPS_PROVIDER, 21.05, 105.05, 0);
        shadowOf(lm).simulateLocation(fix);
        idleAll();

        assertEquals(1, received.size());
        assertEquals(21.05, received.get(0).getLatitude(), 1e-6);
        assertEquals(21.05, mgr.getCurrentUserLocation().getLatitude(), 1e-6);

        mgr.stopTrackingUserLocation(listener);
        idleAll();
        assertEquals(0, shadowOf(lm).getRequestLocationUpdateListeners().size());
    }

    @Test
    public void resetForTestingRecreatesSingletonAndCleansState() {
        CarLocatorManager mgr1 = CarLocatorManager.get();
        mgr1.startTrackingUserLocation(ctx, l -> {});
        idleAll();
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());

        CarLocatorManager.resetForTesting();
        assertEquals(0, shadowOf(lm).getRequestLocationUpdateListeners().size());

        CarLocatorManager mgr2 = CarLocatorManager.get();
        assertTrue(mgr1 != mgr2);
        assertNull(mgr2.getCurrentUserLocation());
    }

    @Test
    public void userLocationListenerRunsOnMainLooper() {
        CarLocatorManager mgr = CarLocatorManager.get();
        java.util.concurrent.atomic.AtomicBoolean ranOnMain = new java.util.concurrent.atomic.AtomicBoolean(false);
        CarLocatorManager.UserLocationListener listener = loc -> {
            ranOnMain.set(android.os.Looper.myLooper() == android.os.Looper.getMainLooper());
        };

        mgr.startTrackingUserLocation(ctx, listener);
        idleAll();

        Location fix = createFix(LocationManager.GPS_PROVIDER, 21.05, 105.05, 0);
        shadowOf(lm).simulateLocation(fix);
        idleAll();

        assertTrue("UserLocationListener must run on the main looper", ranOnMain.get());
        mgr.stopTrackingUserLocation(listener);
        idleAll();
    }

    @Test
    public void trackingStartsAfterPermissionIsGrantedOnFirstOpen() {
        shadowOf((Application) ctx).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        CarLocatorManager mgr = CarLocatorManager.get();
        List<Location> got = new ArrayList<>();
        CarLocatorManager.UserLocationListener listener = got::add;
        mgr.startTrackingUserLocation(ctx, listener);
        idleAll();
        assertEquals(0, shadowOf(lm).getRequestLocationUpdateListeners().size());

        // CarFinderFragment calls startTrackingUserLocation again from the permission callback.
        shadowOf((Application) ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        mgr.startTrackingUserLocation(ctx, listener);
        idleAll();
        assertEquals(1, shadowOf(lm).getRequestLocationUpdateListeners().size());

        shadowOf(lm).simulateLocation(createFix(LocationManager.GPS_PROVIDER, 21.0, 105.0, 0));
        shadowOf(lm).simulateLocation(createFix("fused", 21.0, 105.0, 0));
        idleAll();
        assertTrue("fix delivered after the late grant", !got.isEmpty());
        mgr.stopTrackingUserLocation(listener);
        idleAll();
    }
}
