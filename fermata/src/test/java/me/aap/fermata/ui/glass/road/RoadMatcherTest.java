package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RoadMatcherTest {

    private RoadTile.Way makeWay(long id, double lat1, double lon1, double lat2, double lon2,
                                 boolean oneway, SpeedLimit fwd, SpeedLimit bwd, String name) {
        long[] nodes = new long[]{id * 10 + 1, id * 10 + 2};
        double[] lat = new double[]{lat1, lat2};
        double[] lon = new double[]{lon1, lon2};
        return new RoadTile.Way(id, nodes, lat, lon, oneway, fwd, bwd, name);
    }

    @Test
    public void matchesNearestAlignedWay() {
        // Way 1: Northbound (bearing ~0 deg)
        RoadTile.Way w1 = makeWay(1, 0.0, 0.0, 0.001, 0.0, true, SpeedLimit.kmh(50), null, "Main St");
        // Way 2: Southbound (bearing ~180 deg), 15 m east (~0.000135 lon)
        RoadTile.Way w2 = makeWay(2, 0.001, 0.000135, 0.0, 0.000135, true, SpeedLimit.kmh(30), null, "Side St");

        RoadTile tile = new RoadTile(Arrays.asList(w1, w2), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        // Fix between w1 and w2, heading North (0 deg), speed 10 m/s, accuracy 5 m
        RoadMatcher.Match m = matcher.update(Collections.singletonList(tile), 0.0005, 0.00005, 5f, 0f, 10f, 1000L);
        assertNotNull(m);
        assertEquals(1L, m.way.id);
        assertTrue(m.forward);
        assertEquals(SpeedLimit.kmh(50), m.limit());
    }

    @Test
    public void respectsOneway() {
        // Way 2: Southbound only (bearing 180 deg)
        RoadTile.Way w2 = makeWay(2, 0.001, 0.0, 0.0, 0.0, true, SpeedLimit.kmh(30), null, "One Way South");
        RoadTile tile = new RoadTile(Collections.singletonList(w2), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        // Car heading North (0 deg) cannot match a Southbound one-way
        RoadMatcher.Match m = matcher.update(Collections.singletonList(tile), 0.0005, 0.0, 5f, 0f, 10f, 1000L);
        assertNull(m);
    }

    @Test
    public void keepsMatchWhenSlowOrNoBearing() {
        RoadTile.Way w1 = makeWay(1, 0.0, 0.0, 0.001, 0.0, true, SpeedLimit.kmh(50), null, "Main St");
        RoadTile tile = new RoadTile(Collections.singletonList(w1), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        RoadMatcher.Match m1 = matcher.update(Collections.singletonList(tile), 0.0005, 0.0, 5f, 0f, 10f, 1000L);
        assertNotNull(m1);
        assertEquals(1L, m1.way.id);

        // Slow (< 1.5 m/s) -> keeps previous match
        RoadMatcher.Match m2 = matcher.update(Collections.singletonList(tile), 0.0006, 0.0, 5f, 90f, 0.5f, 2000L);
        assertSame(m1, m2);

        // NaN bearing -> keeps previous match
        RoadMatcher.Match m3 = matcher.update(Collections.singletonList(tile), 0.0006, 0.0, 5f, Float.NaN, 10f, 3000L);
        assertSame(m1, m3);
    }

    @Test
    public void inaccurateFixDoesNotSwitch() {
        RoadTile.Way w1 = makeWay(1, 0.0, 0.0, 0.001, 0.0, true, SpeedLimit.kmh(50), null, "Way 1");
        RoadTile.Way w2 = makeWay(2, 0.0005, 0.0, 0.0005, 0.001, true, SpeedLimit.kmh(30), null, "Way 2");
        RoadTile tile = new RoadTile(Arrays.asList(w1, w2), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        RoadMatcher.Match m1 = matcher.update(Collections.singletonList(tile), 0.0001, 0.0, 5f, 0f, 10f, 1000L);
        assertNotNull(m1);
        assertEquals(1L, m1.way.id);

        // Accuracy > 50 m -> keeps previous match even near w2
        RoadMatcher.Match m2 = matcher.update(Collections.singletonList(tile), 0.0005, 0.0005, 60f, 90f, 10f, 2000L);
        assertSame(m1, m2);
    }

    @Test
    public void holdsAtJunctionThenSwitches() {
        RoadTile.Way w1 = makeWay(1, 0.0, 0.0, 0.002, 0.0, true, SpeedLimit.kmh(50), null, "North St");
        RoadTile.Way w2 = makeWay(2, 0.001, 0.0, 0.001, 0.002, true, SpeedLimit.kmh(30), null, "East St");
        RoadTile tile = new RoadTile(Arrays.asList(w1, w2), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        RoadMatcher.Match m1 = matcher.update(Collections.singletonList(tile), 0.0005, 0.0, 5f, 0f, 10f, 1000L);
        assertEquals(1L, m1.way.id);

        // At junction, turning East. Still within 25m of w1, heading 90 deg aligns with w2
        // At 3000 ms (< 4000 ms hold), stays on w1
        RoadMatcher.Match m2 = matcher.update(Collections.singletonList(tile), 0.001, 0.0001, 5f, 90f, 10f, 4000L);
        assertEquals(1L, m2.way.id);

        // At 4100 ms hold, switches to w2
        RoadMatcher.Match m3 = matcher.update(Collections.singletonList(tile), 0.001, 0.0001, 5f, 90f, 10f, 5100L);
        assertEquals(2L, m3.way.id);
    }

    @Test
    public void sameWayInTwoTilesIsOneCandidate() {
        RoadTile.Way w1 = makeWay(1, 0.0, 0.0, 0.001, 0.0, true, SpeedLimit.kmh(50), null, "Main St");
        RoadTile t1 = new RoadTile(Collections.singletonList(w1), Collections.emptyList());
        RoadTile t2 = new RoadTile(Collections.singletonList(w1), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        RoadMatcher.Match m = matcher.update(Arrays.asList(t1, t2), 0.0005, 0.0, 5f, 0f, 10f, 1000L);
        assertNotNull(m);
        assertEquals(1L, m.way.id);
    }

    @Test
    public void dropsToNullAfterHold() {
        RoadTile.Way w1 = makeWay(1, 0.0, 0.0, 0.001, 0.0, true, SpeedLimit.kmh(50), null, "Main St");
        RoadTile tile = new RoadTile(Collections.singletonList(w1), Collections.emptyList());
        RoadMatcher matcher = new RoadMatcher();

        RoadMatcher.Match m1 = matcher.update(Collections.singletonList(tile), 0.0005, 0.0, 5f, 0f, 10f, 1000L);
        assertNotNull(m1);

        // Move far away into a field (> 25 m from all ways)
        // At 2000 ms (< 4000 ms hold), keeps previous match
        RoadMatcher.Match m2 = matcher.update(Collections.singletonList(tile), 0.05, 0.05, 5f, 0f, 10f, 3000L);
        assertSame(m1, m2);

        // At 4100 ms (> 4000 ms hold), drops to null
        RoadMatcher.Match m3 = matcher.update(Collections.singletonList(tile), 0.05, 0.05, 5f, 0f, 10f, 5100L);
        assertNull(m3);
    }
}
