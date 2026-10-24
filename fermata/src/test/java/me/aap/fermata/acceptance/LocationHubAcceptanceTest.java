package me.aap.fermata.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** AC-01: one shared LocationManager request for any number of consumers. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class LocationHubAcceptanceTest {
    private Context ctx;
    private LocationManager lm;

    @Before public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        shadowOf((Application) ctx).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        shadowOf(lm).setLocationEnabled(true);
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        ShadowSystemClock.advanceBy(Duration.ofMinutes(30));
        Api.resetHub();
    }

    @After public void tearDown() {
        Api.resetHub();
    }

    private int requests() {
        return shadowOf(lm).getRequestLocationUpdateListeners().size();
    }

    @Test public void twoConsumersShareOneRequest() {
        Object hub = Api.hub(ctx);
        Api.acquire(hub, "a", l -> {});
        int initialRequests = requests();
        Api.acquire(hub, "b", l -> {});
        Api.idleAll();
        assertEquals("one LocationManager request per source for two consumers", initialRequests, requests());
    }

    @Test public void releasingTheLastConsumerRemovesTheRequest() {
        Object hub = Api.hub(ctx);
        Api.acquire(hub, "a", l -> {});
        int initialRequests = requests();
        Api.acquire(hub, "b", l -> {});
        Api.idleAll();
        Api.release(hub, "a");
        Api.idleAll();
        assertEquals("same requests while a consumer remains", initialRequests, requests());
        Api.release(hub, "b");
        Api.idleAll();
        assertEquals("no request once every consumer released", 0, requests());
    }

    @Test public void throwingConsumerDoesNotStarveOthers() {
        Object hub = Api.hub(ctx);
        List<Location> got = new ArrayList<>();
        Api.acquire(hub, "bad", l -> { throw new IllegalStateException("consumer bug"); });
        Api.acquire(hub, "good", got::add);
        Api.idleAll();
        // The hub uses FUSED or GPS; deliver on both, only the registered one reaches the hub.
        shadowOf(lm).simulateLocation(Api.fix(LocationManager.GPS_PROVIDER, 21.06, 105.89, 0));
        shadowOf(lm).simulateLocation(Api.fix("fused", 21.06, 105.89, 0));
        Api.idleAll();
        assertTrue("good consumer received the fix", !got.isEmpty());
        assertNotNull("hub remembers the latest fix", Api.latest(hub));
        assertEquals(21.06, Api.latest(hub).getLatitude(), 1e-9);
    }

    @Test public void freshSystemLocationHonoursMaxAge() {
        Object hub = Api.hub(ctx);
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, Api.fix(LocationManager.GPS_PROVIDER, 21.0, 105.0, 10_000));
        assertNotNull("10 s old fix is fresh for 30 s", Api.freshSystemLocation(hub, 30_000));
        shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, Api.fix(LocationManager.GPS_PROVIDER, 21.0, 105.0, 60_000));
        assertNull("60 s old fix is too old for 30 s", Api.freshSystemLocation(hub, 30_000));
    }
}
