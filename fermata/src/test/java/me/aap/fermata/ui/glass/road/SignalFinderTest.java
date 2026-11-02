package me.aap.fermata.ui.glass.road;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SignalFinderTest {

    private static final double DEG_TO_M = 111139.0;

    private RoadTile.Way makeWay(long id, long[] nodes, double[] lat, double[] lon, boolean oneway) {
        return new RoadTile.Way(id, nodes, lat, lon, oneway, null, null, "Test Road " + id);
    }

    @Test
    public void findsSignalAhead() {
        // Straight Northbound way of ~200 m length (0.0018 deg lat)
        double endLat = 200.0 / DEG_TO_M;
        RoadTile.Way w = makeWay(1, new long[]{101, 102}, new double[]{0.0, endLat}, new double[]{0.0, 0.0}, false);
        RoadTile.Signal s = new RoadTile.Signal(102, endLat, 0.0, 0);
        RoadTile tile = new RoadTile(Collections.singletonList(w), Collections.singletonList(s));

        // Car is at 20 m along the way, heading forward
        double fixLat = 20.0 / DEG_TO_M;
        RoadMatcher.Match m = new RoadMatcher.Match(w, true, 0);

        float dist = SignalFinder.distanceAhead(m, Collections.singletonList(tile), fixLat, 0.0);
        assertEquals(180f, dist, 2f);
    }

    @Test
    public void ignoresSignalBehind() {
        double endLat = 200.0 / DEG_TO_M;
        RoadTile.Way w = makeWay(1, new long[]{101, 102}, new double[]{0.0, endLat}, new double[]{0.0, 0.0}, false);
        // Signal at node 101 (at 0 m, behind the car)
        RoadTile.Signal s = new RoadTile.Signal(101, 0.0, 0.0, 0);
        RoadTile tile = new RoadTile(Collections.singletonList(w), Collections.singletonList(s));

        double fixLat = 20.0 / DEG_TO_M;
        RoadMatcher.Match m = new RoadMatcher.Match(w, true, 0);

        float dist = SignalFinder.distanceAhead(m, Collections.singletonList(tile), fixLat, 0.0);
        assertEquals(-1f, dist, 0.01f);
    }

    @Test
    public void respectsDirection() {
        double endLat = 200.0 / DEG_TO_M;
        RoadTile.Way w = makeWay(1, new long[]{101, 102}, new double[]{0.0, endLat}, new double[]{0.0, 0.0}, false);
        // Signal with backward direction (-1) on a forward journey opposes travel
        RoadTile.Signal opposingSignal = new RoadTile.Signal(102, endLat, 0.0, -1);
        RoadTile tile1 = new RoadTile(Collections.singletonList(w), Collections.singletonList(opposingSignal));

        double fixLat = 20.0 / DEG_TO_M;
        RoadMatcher.Match m = new RoadMatcher.Match(w, true, 0);

        float dist1 = SignalFinder.distanceAhead(m, Collections.singletonList(tile1), fixLat, 0.0);
        assertEquals(-1f, dist1, 0.01f);

        // Signal with forward direction (1) aligns with forward journey
        RoadTile.Signal alignedSignal = new RoadTile.Signal(102, endLat, 0.0, 1);
        RoadTile tile2 = new RoadTile(Collections.singletonList(w), Collections.singletonList(alignedSignal));

        float dist2 = SignalFinder.distanceAhead(m, Collections.singletonList(tile2), fixLat, 0.0);
        assertEquals(180f, dist2, 2f);
    }

    @Test
    public void continuesIntoSingleNextWay() {
        double lat1 = 0.0;
        double lat2 = 100.0 / DEG_TO_M;
        double lat3 = 210.0 / DEG_TO_M;
        RoadTile.Way w1 = makeWay(1, new long[]{101, 102}, new double[]{lat1, lat2}, new double[]{0.0, 0.0}, false);
        RoadTile.Way w2 = makeWay(2, new long[]{102, 103}, new double[]{lat2, lat3}, new double[]{0.0, 0.0}, false);
        RoadTile.Signal s = new RoadTile.Signal(103, lat3, 0.0, 0);
        RoadTile tile = new RoadTile(Arrays.asList(w1, w2), Collections.singletonList(s));

        // Car is at lat1 (0 m), traversing w1 forward
        RoadMatcher.Match m = new RoadMatcher.Match(w1, true, 0);

        float dist = SignalFinder.distanceAhead(m, Collections.singletonList(tile), lat1, 0.0);
        assertEquals(210f, dist, 2f);
    }

    @Test
    public void stopsAtFork() {
        double lat1 = 0.0;
        double lat2 = 100.0 / DEG_TO_M;
        double lat3 = 210.0 / DEG_TO_M;
        RoadTile.Way w1 = makeWay(1, new long[]{101, 102}, new double[]{lat1, lat2}, new double[]{0.0, 0.0}, false);
        // Fork at node 102: two ways continue (w2 and w3)
        RoadTile.Way w2 = makeWay(2, new long[]{102, 103}, new double[]{lat2, lat3}, new double[]{0.0, 0.0}, false);
        RoadTile.Way w3 = makeWay(3, new long[]{102, 104}, new double[]{lat2, lat2}, new double[]{0.0, 100.0 / DEG_TO_M}, false);
        RoadTile.Signal s = new RoadTile.Signal(103, lat3, 0.0, 0);
        RoadTile tile = new RoadTile(Arrays.asList(w1, w2, w3), Collections.singletonList(s));

        RoadMatcher.Match m = new RoadMatcher.Match(w1, true, 0);

        float dist = SignalFinder.distanceAhead(m, Collections.singletonList(tile), lat1, 0.0);
        assertEquals(-1f, dist, 0.01f);
    }

    @Test
    public void mergesJunctionCluster() {
        double lat1 = 0.0;
        double lat2 = 150.0 / DEG_TO_M;
        double lat3 = 170.0 / DEG_TO_M;
        RoadTile.Way w = makeWay(1, new long[]{101, 102, 103}, new double[]{lat1, lat2, lat3}, new double[]{0.0, 0.0, 0.0}, false);
        RoadTile.Signal s1 = new RoadTile.Signal(102, lat2, 0.0, 0);
        RoadTile.Signal s2 = new RoadTile.Signal(103, lat3, 0.0, 0);
        RoadTile tile = new RoadTile(Collections.singletonList(w), Arrays.asList(s1, s2));

        RoadMatcher.Match m = new RoadMatcher.Match(w, true, 0);

        float dist = SignalFinder.distanceAhead(m, Collections.singletonList(tile), lat1, 0.0);
        // 150 m and 170 m are within 30 m of each other -> nearest kept (150 m)
        assertEquals(150f, dist, 2f);
    }

    @Test
    public void borderSignalCountedOnce() {
        double lat1 = 0.0;
        double lat2 = 150.0 / DEG_TO_M;
        RoadTile.Way w = makeWay(1, new long[]{101, 102}, new double[]{lat1, lat2}, new double[]{0.0, 0.0}, false);
        RoadTile.Signal s1 = new RoadTile.Signal(102, lat2, 0.0, 0);
        // Duplicate signal across two tiles
        RoadTile tile1 = new RoadTile(Collections.singletonList(w), Collections.singletonList(s1));
        RoadTile tile2 = new RoadTile(Collections.singletonList(w), Collections.singletonList(s1));

        RoadMatcher.Match m = new RoadMatcher.Match(w, true, 0);

        float dist = SignalFinder.distanceAhead(m, Arrays.asList(tile1, tile2), lat1, 0.0);
        assertEquals(150f, dist, 2f);
    }

    @Test
    public void showWithin() {
        assertEquals(100f, SignalFinder.showWithinM(0f), 0.01f);
        assertEquals(100f, SignalFinder.showWithinM(5f), 0.01f);
        assertEquals(200f, SignalFinder.showWithinM(20f), 0.01f);
        assertEquals(350f, SignalFinder.showWithinM(35f), 0.01f);
        assertEquals(400f, SignalFinder.showWithinM(40f), 0.01f);
        assertEquals(400f, SignalFinder.showWithinM(60f), 0.01f);
    }
}
