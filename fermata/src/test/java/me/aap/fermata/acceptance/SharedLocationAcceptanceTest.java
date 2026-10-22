package me.aap.fermata.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

import java.lang.reflect.Method;
import java.time.Duration;

import me.aap.fermata.carfinder.CarLocatorManager;
import me.aap.fermata.ui.glass.speedo.GlassSpeedoTripController;

/** AC-02 and AC-03: speedo and CarFinder consume the hub; no stale remembered location. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class SharedLocationAcceptanceTest {
    private Context ctx;
    private LocationManager lm;

    @Before public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        ctx.getSharedPreferences("speedo_trip_v2", Context.MODE_PRIVATE).edit().clear().commit();
        shadowOf((Application) ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        shadowOf(lm).setLocationEnabled(true);
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        ShadowSystemClock.advanceBy(Duration.ofMinutes(30));
        Api.resetHub();
        Api.resetCarLocator();
    }

    @After public void tearDown() {
        Api.resetCarLocator();
        Api.resetHub();
    }

    @Test public void speedoAndCarFinderTogetherUseOneRequest() {
        GlassSpeedoTripController speedo = new GlassSpeedoTripController(ctx, (v, s) -> {});
        speedo.start();
        CarLocatorManager.UserLocationListener finder = l -> {};
        CarLocatorManager.get().startTrackingUserLocation(ctx, finder);
        Api.idleAll();
        assertEquals("speedo + CarFinder share one LocationManager request", 1,
                shadowOf(lm).getRequestLocationUpdateListeners().size());
        CarLocatorManager.get().stopTrackingUserLocation(finder);
        speedo.stop();
        Api.idleAll();
    }

    @Test public void staleRememberedLocationIsNotReturnedWhenHubHasNewerFix() {
        CarLocatorManager.UserLocationListener finder = l -> {};
        CarLocatorManager.get().startTrackingUserLocation(ctx, finder);
        Api.idleAll();
        shadowOf(lm).simulateLocation(Api.fix(LocationManager.GPS_PROVIDER, 21.000, 105.000, 0));
        shadowOf(lm).simulateLocation(Api.fix("fused", 21.000, 105.000, 0));
        Api.idleAll();
        CarLocatorManager.get().stopTrackingUserLocation(finder);
        Api.idleAll();

        ShadowSystemClock.advanceBy(Duration.ofMinutes(5));
        Object hub = Api.hub(ctx);
        Api.acquire(hub, "other", l -> {});
        Api.idleAll();
        shadowOf(lm).simulateLocation(Api.fix(LocationManager.GPS_PROVIDER, 21.500, 105.500, 0));
        shadowOf(lm).simulateLocation(Api.fix("fused", 21.500, 105.500, 0));
        Api.idleAll();

        Location best = CarLocatorManager.get().queryBestKnownUserLocation(ctx);
        assertNotNull(best);
        assertEquals("newest hub fix, not the 5 minute old remembered one", 21.5, best.getLatitude(), 1e-6);
        Api.release(hub, "other");
    }

    private Location choose(Location buffer, Location system) {
        Method m = Api.method(CarLocatorManager.class, "chooseParkingInput", Location.class, Location.class, long.class);
        return (Location) Api.call(null, m, buffer, system, android.os.SystemClock.elapsedRealtime());
    }

    @Test public void parkingInputKeepsAFreshBuffer() {
        Location buffer = Api.fix(LocationManager.GPS_PROVIDER, 1, 1, 10_000);
        Location system = Api.fix(LocationManager.GPS_PROVIDER, 2, 2, 5_000);
        assertEquals(1, choose(buffer, system).getLatitude(), 0);
    }

    @Test public void parkingInputUsesFreshSystemFixWhenBufferIsStale() {
        Location buffer = Api.fix(LocationManager.GPS_PROVIDER, 1, 1, 60_000);
        Location system = Api.fix(LocationManager.GPS_PROVIDER, 2, 2, 5_000);
        assertEquals(2, choose(buffer, system).getLatitude(), 0);
        assertEquals(2, choose(null, system).getLatitude(), 0);
    }

    @Test public void parkingInputIgnoresAStaleSystemFix() {
        Location buffer = Api.fix(LocationManager.GPS_PROVIDER, 1, 1, 60_000);
        Location system = Api.fix(LocationManager.GPS_PROVIDER, 2, 2, 45_000);
        assertEquals(1, choose(buffer, system).getLatitude(), 0);
        assertNull(choose(null, system));
    }
}
