package me.aap.fermata.ui.glass.road;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class RoadMatcher {

    public static final class Match {
        public final RoadTile.Way way;
        public final boolean forward;
        public final int segment;

        public Match(RoadTile.Way way, boolean forward, int segment) {
            this.way = way;
            this.forward = forward;
            this.segment = segment;
        }

        @Nullable
        public SpeedLimit limit() {
            return forward ? way.forward : way.backward;
        }
    }

    private static class Candidate {
        final RoadTile.Way way;
        final boolean forward;
        final int segment;
        final double distance;

        Candidate(RoadTile.Way way, boolean forward, int segment, double distance) {
            this.way = way;
            this.forward = forward;
            this.segment = segment;
            this.distance = distance;
        }
    }

    private Match currentMatch = null;
    private long lastMatchValidTime = -1L;

    @Nullable
    public Match update(Collection<RoadTile> tiles, double lat, double lon, float accuracyM,
                        float bearingDeg, float speedMps, long nowMs) {
        if (speedMps < 1.5f || Float.isNaN(bearingDeg) || accuracyM > 50f) {
            return currentMatch;
        }

        double cosLat = Math.cos(Math.toRadians(lat));
        double scaleX = cosLat * 111319.5;
        double scaleY = 111139.0;

        Map<Long, Candidate> candidates = new HashMap<>();
        Map<Long, Double> wayMinDistances = new HashMap<>();

        if (tiles != null) {
            for (RoadTile tile : tiles) {
                if (tile == null || tile.ways == null) continue;
                for (RoadTile.Way way : tile.ways) {
                    if (way.lat == null || way.lat.length < 2) continue;

                    int numSegments = way.lat.length - 1;
                    for (int s = 0; s < numSegments; s++) {
                        double x1 = (way.lon[s] - lon) * scaleX;
                        double y1 = (way.lat[s] - lat) * scaleY;
                        double x2 = (way.lon[s + 1] - lon) * scaleX;
                        double y2 = (way.lat[s + 1] - lat) * scaleY;

                        double dx = x2 - x1;
                        double dy = y2 - y1;
                        double lenSq = dx * dx + dy * dy;

                        double t = 0;
                        if (lenSq > 1e-10) {
                            t = (-x1 * dx - y1 * dy) / lenSq;
                            if (t < 0) t = 0;
                            else if (t > 1) t = 1;
                        }

                        double cx = x1 + t * dx;
                        double cy = y1 + t * dy;
                        double dist = Math.sqrt(cx * cx + cy * cy);

                        Double prevMin = wayMinDistances.get(way.id);
                        if (prevMin == null || dist < prevMin) {
                            wayMinDistances.put(way.id, dist);
                        }

                        if (dist <= 25.0) {
                            double segBearing = Math.toDegrees(Math.atan2(dx, dy));
                            if (segBearing < 0) segBearing += 360.0;

                            double bDeg = (bearingDeg % 360.0 + 360.0) % 360.0;
                            double diffFwd = Math.abs(bDeg - segBearing);
                            if (diffFwd > 180.0) diffFwd = 360.0 - diffFwd;

                            double diffBwd = Math.abs(bDeg - ((segBearing + 180.0) % 360.0));
                            if (diffBwd > 180.0) diffBwd = 360.0 - diffBwd;

                            boolean forward;
                            boolean compatible;
                            if (diffFwd <= 30.0) {
                                forward = true;
                                compatible = true;
                            } else if (!way.oneway && diffBwd <= 30.0) {
                                forward = false;
                                compatible = true;
                            } else {
                                compatible = false;
                                forward = true;
                            }

                            if (compatible) {
                                Candidate existing = candidates.get(way.id);
                                if (existing == null || dist < existing.distance) {
                                    candidates.put(way.id, new Candidate(way, forward, s, dist));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            if (currentMatch != null) {
                if (nowMs - lastMatchValidTime >= 4000L) {
                    currentMatch = null;
                }
            }
            return currentMatch;
        }

        Candidate best = null;
        for (Candidate c : candidates.values()) {
            if (best == null || c.distance < best.distance) {
                best = c;
            }
        }

        Match bestMatch = new Match(best.way, best.forward, best.segment);

        if (currentMatch == null) {
            currentMatch = bestMatch;
            lastMatchValidTime = nowMs;
            return currentMatch;
        }

        if (currentMatch.way.id == best.way.id) {
            currentMatch = bestMatch;
            lastMatchValidTime = nowMs;
            return currentMatch;
        }

        Double prevWayDist = wayMinDistances.get(currentMatch.way.id);
        boolean prevWayNearby = (prevWayDist != null && prevWayDist <= 25.0);

        if (!prevWayNearby) {
            currentMatch = bestMatch;
            lastMatchValidTime = nowMs;
            return currentMatch;
        }

        if (nowMs - lastMatchValidTime >= 4000L) {
            currentMatch = bestMatch;
            lastMatchValidTime = nowMs;
        }

        return currentMatch;
    }
}
