package me.aap.fermata.ui.glass.road;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SignalFinder {

    private static final float MAX_LOOKAHEAD_M = 400.0f;

    static float showWithinM(float speedMps) {
        return Math.max(100f, Math.min(400f, speedMps * 10f));
    }

    static float distanceAhead(@Nullable RoadMatcher.Match m, @Nullable Collection<RoadTile> tiles,
                               double lat, double lon) {
        if (m == null || m.way == null || tiles == null || tiles.isEmpty()) {
            return -1f;
        }

        Map<Long, RoadTile.Signal> signalMap = new HashMap<>();
        Map<Long, RoadTile.Way> wayMap = new HashMap<>();
        Map<Long, List<RoadTile.Way>> nodeWaysMap = new HashMap<>();

        for (RoadTile tile : tiles) {
            if (tile.signals != null) {
                for (RoadTile.Signal s : tile.signals) {
                    if (!signalMap.containsKey(s.node)) {
                        signalMap.put(s.node, s);
                    }
                }
            }
            if (tile.ways != null) {
                for (RoadTile.Way w : tile.ways) {
                    if (!wayMap.containsKey(w.id)) {
                        wayMap.put(w.id, w);
                        if (w.nodes != null) {
                            for (long nid : w.nodes) {
                                List<RoadTile.Way> list = nodeWaysMap.get(nid);
                                if (list == null) {
                                    list = new ArrayList<>();
                                    nodeWaysMap.put(nid, list);
                                }
                                list.add(w);
                            }
                        }
                    }
                }
            }
        }

        RoadTile.Way currentWay = m.way;
        boolean forward = m.forward;
        int seg = m.segment;

        if (currentWay.lat == null || currentWay.lon == null || currentWay.nodes == null) {
            return -1f;
        }
        int len = currentWay.nodes.length;
        if (len < 2 || seg < 0 || seg >= len - 1) {
            return -1f;
        }

        double cosLat = Math.cos(Math.toRadians(lat));
        double scaleX = cosLat * 111319.5;
        double scaleY = 111139.0;

        double x0 = (currentWay.lon[seg] - lon) * scaleX;
        double y0 = (currentWay.lat[seg] - lat) * scaleY;
        double x1 = (currentWay.lon[seg + 1] - lon) * scaleX;
        double y1 = (currentWay.lat[seg + 1] - lat) * scaleY;

        double dx = x1 - x0;
        double dy = y1 - y0;
        double segLen = Math.sqrt(dx * dx + dy * dy);
        double t = 0.0;
        if (segLen > 1e-6) {
            t = -(x0 * dx + y0 * dy) / (segLen * segLen);
            t = Math.max(0.0, Math.min(1.0, t));
        }

        List<Float> signalDistances = new ArrayList<>();
        Set<Long> visitedWays = new HashSet<>();
        Set<Long> visitedSignals = new HashSet<>();
        visitedWays.add(currentWay.id);

        double accumulatedDistance = 0.0;

        if (forward) {
            accumulatedDistance = (1.0 - t) * segLen;
            for (int i = seg + 1; i < len; i++) {
                if (accumulatedDistance > MAX_LOOKAHEAD_M) {
                    break;
                }
                long nid = currentWay.nodes[i];
                RoadTile.Signal s = signalMap.get(nid);
                if (s != null && s.direction != -1 && visitedSignals.add(s.node)) {
                    signalDistances.add((float) accumulatedDistance);
                }
                if (i < len - 1) {
                    accumulatedDistance += haversineM(
                            currentWay.lat[i], currentWay.lon[i],
                            currentWay.lat[i + 1], currentWay.lon[i + 1]
                    );
                }
            }
        } else {
            accumulatedDistance = t * segLen;
            for (int i = seg; i >= 0; i--) {
                if (accumulatedDistance > MAX_LOOKAHEAD_M) {
                    break;
                }
                long nid = currentWay.nodes[i];
                RoadTile.Signal s = signalMap.get(nid);
                if (s != null && s.direction != 1 && visitedSignals.add(s.node)) {
                    signalDistances.add((float) accumulatedDistance);
                }
                if (i > 0) {
                    accumulatedDistance += haversineM(
                            currentWay.lat[i], currentWay.lon[i],
                            currentWay.lat[i - 1], currentWay.lon[i - 1]
                    );
                }
            }
        }

        while (accumulatedDistance <= MAX_LOOKAHEAD_M) {
            long endNode = forward ? currentWay.nodes[currentWay.nodes.length - 1] : currentWay.nodes[0];
            List<RoadTile.Way> connected = nodeWaysMap.get(endNode);
            if (connected == null) {
                break;
            }

            RoadTile.Way nextWay = null;
            int otherCount = 0;
            for (RoadTile.Way cw : connected) {
                if (cw.id != currentWay.id) {
                    otherCount++;
                    nextWay = cw;
                }
            }

            if (otherCount != 1 || nextWay == null || visitedWays.contains(nextWay.id)) {
                break;
            }

            visitedWays.add(nextWay.id);
            int nextLen = nextWay.nodes.length;
            if (nextLen < 2) {
                break;
            }

            boolean nextForward;
            if (nextWay.nodes[0] == endNode) {
                nextForward = true;
            } else if (nextWay.nodes[nextLen - 1] == endNode) {
                if (nextWay.oneway) {
                    break;
                }
                nextForward = false;
            } else {
                break;
            }

            currentWay = nextWay;
            forward = nextForward;

            if (forward) {
                for (int i = 1; i < nextLen; i++) {
                    accumulatedDistance += haversineM(
                            currentWay.lat[i - 1], currentWay.lon[i - 1],
                            currentWay.lat[i], currentWay.lon[i]
                    );
                    if (accumulatedDistance > MAX_LOOKAHEAD_M) {
                        break;
                    }
                    long nid = currentWay.nodes[i];
                    RoadTile.Signal s = signalMap.get(nid);
                    if (s != null && s.direction != -1 && visitedSignals.add(s.node)) {
                        signalDistances.add((float) accumulatedDistance);
                    }
                }
            } else {
                for (int i = nextLen - 2; i >= 0; i--) {
                    accumulatedDistance += haversineM(
                            currentWay.lat[i + 1], currentWay.lon[i + 1],
                            currentWay.lat[i], currentWay.lon[i]
                    );
                    if (accumulatedDistance > MAX_LOOKAHEAD_M) {
                        break;
                    }
                    long nid = currentWay.nodes[i];
                    RoadTile.Signal s = signalMap.get(nid);
                    if (s != null && s.direction != 1 && visitedSignals.add(s.node)) {
                        signalDistances.add((float) accumulatedDistance);
                    }
                }
            }
        }

        if (signalDistances.isEmpty()) {
            return -1f;
        }

        return signalDistances.get(0);
    }

    private static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }
}
