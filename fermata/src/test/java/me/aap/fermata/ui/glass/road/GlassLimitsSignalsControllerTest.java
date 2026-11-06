package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import android.content.Context;
import android.location.Location;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.location.LocationHub;

@RunWith(RobolectricTestRunner.class)
public class GlassLimitsSignalsControllerTest {

    private Context context;
    private GlassLimitsSignalsView view;
    private RoadDataSource fakeDataSource;
    private GlassLimitsSignalsController controller;

    @Before
    public void setUp() {
        LocationHub.resetForTesting();
        context = RuntimeEnvironment.getApplication();
        view = new GlassLimitsSignalsView(context);

        // Synthetic tile with a Northbound way with 50 km/h limit
        long[] nodes = new long[]{101, 102};
        double[] lat = new double[]{0.0, 0.002};
        double[] lon = new double[]{0.0, 0.0};
        RoadTile.Way way = new RoadTile.Way(1L, nodes, lat, lon, false, SpeedLimit.kmh(50), SpeedLimit.kmh(50), "Main Avenue");
        RoadTile tile = new RoadTile(Collections.singletonList(way), Collections.emptyList());

        fakeDataSource = new RoadDataSource(null, null, Runnable::run, () -> 1000L) {
            @Override
            public List<RoadTile> tilesAround(double lat, double lon, float bearingDeg, boolean allowLive) {
                return Collections.singletonList(tile);
            }
        };

        controller = new GlassLimitsSignalsController(context, view, fakeDataSource);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.setActive(false);
        }
        LocationHub.resetForTesting();
    }

    @Test
    public void activeAcquiresAndInactiveReleases() {
        LocationHub hub = LocationHub.get(context);
        int base = hub.consumerCountForTesting();

        controller.setActive(true);
        assertEquals(base + 1, hub.consumerCountForTesting());

        controller.setActive(false);
        assertEquals(base, hub.consumerCountForTesting());
    }

    @Test
    public void phoneAndCarCardsDoNotTakeEachOthersUpdates() {
        LocationHub hub = LocationHub.get(context);
        int base = hub.consumerCountForTesting();
        GlassLimitsSignalsController other = new GlassLimitsSignalsController(
                context, new GlassLimitsSignalsView(context), fakeDataSource);

        controller.setActive(true);
        other.setActive(true);
        assertEquals(base + 2, hub.consumerCountForTesting());

        other.setActive(false);
        assertEquals(base + 1, hub.consumerCountForTesting());
    }

    @Test
    public void fixUpdatesView() {
        controller.setActive(true);

        Location loc = new Location("gps");
        loc.setLatitude(0.0005);
        loc.setLongitude(0.0);
        loc.setAccuracy(5f);
        loc.setBearing(0f);
        loc.setSpeed(10f); // 10 m/s = 36 km/h

        controller.onLocation(loc);

        assertEquals(context.getString(R.string.glass_speed_limit_desc, 50), view.getSignContentDescription());
        assertEquals("Main Avenue", view.getChipText());
    }
}
