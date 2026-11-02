package me.aap.fermata.ui.glass.road;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RoadTile {

    public static final class Way {
        public final long id;
        public final long[] nodes;
        public final double[] lat;
        public final double[] lon;
        public final boolean oneway;
        @Nullable
        public final SpeedLimit forward;
        @Nullable
        public final SpeedLimit backward;
        @Nullable
        public final String name;

        public Way(long id, long[] nodes, double[] lat, double[] lon, boolean oneway,
                   @Nullable SpeedLimit forward, @Nullable SpeedLimit backward, @Nullable String name) {
            this.id = id;
            this.nodes = nodes;
            this.lat = lat;
            this.lon = lon;
            this.oneway = oneway;
            this.forward = forward;
            this.backward = backward;
            this.name = name;
        }
    }

    public static final class Signal {
        public final long node;
        public final double lat;
        public final double lon;
        public final int direction; // 0 both, 1 forward, -1 backward

        public Signal(long node, double lat, double lon, int direction) {
            this.node = node;
            this.lat = lat;
            this.lon = lon;
            this.direction = direction;
        }
    }

    public final List<Way> ways;
    public final List<Signal> signals;

    public RoadTile(List<Way> ways, List<Signal> signals) {
        this.ways = Collections.unmodifiableList(ways);
        this.signals = Collections.unmodifiableList(signals);
    }

    @NonNull
    public static RoadTile parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) {
            throw new JSONException("Empty json");
        }
        JSONObject root = new JSONObject(json);
        if (!root.has("elements")) {
            throw new JSONException("Missing elements array");
        }
        JSONArray elements = root.getJSONArray("elements");
        List<Way> ways = new ArrayList<>();
        List<Signal> signals = new ArrayList<>();

        int numElements = elements.length();
        for (int i = 0; i < numElements; i++) {
            JSONObject elem = elements.getJSONObject(i);
            String type = elem.optString("type", "");

            if ("way".equals(type)) {
                long id = elem.getLong("id");
                JSONArray geomArr = elem.optJSONArray("geometry");
                JSONArray nodesArr = elem.optJSONArray("nodes");

                int len = geomArr != null ? geomArr.length() : (nodesArr != null ? nodesArr.length() : 0);
                double[] lat = new double[len];
                double[] lon = new double[len];
                long[] nodes = new long[len];

                if (geomArr != null) {
                    for (int j = 0; j < len; j++) {
                        JSONObject pt = geomArr.getJSONObject(j);
                        lat[j] = pt.getDouble("lat");
                        lon[j] = pt.getDouble("lon");
                    }
                }
                if (nodesArr != null) {
                    int nLen = Math.min(len, nodesArr.length());
                    for (int j = 0; j < nLen; j++) {
                        nodes[j] = nodesArr.getLong(j);
                    }
                }

                JSONObject tags = elem.optJSONObject("tags");
                String name = null;
                boolean oneway = false;
                boolean onewayReverse = false;
                SpeedLimit forward = null;
                SpeedLimit backward = null;

                if (tags != null) {
                    if (tags.has("name")) {
                        name = tags.optString("name", null);
                    }
                    String onewayTag = tags.optString("oneway", "no");
                    if ("yes".equalsIgnoreCase(onewayTag) || "1".equals(onewayTag) || "true".equalsIgnoreCase(onewayTag)) {
                        oneway = true;
                    } else if ("-1".equals(onewayTag)) {
                        oneway = true;
                        onewayReverse = true;
                    }

                    SpeedLimit plainLimit = tags.has("maxspeed") ? MaxspeedParser.parse(tags.optString("maxspeed", null)) : null;
                    SpeedLimit fwdLimit = tags.has("maxspeed:forward") ? MaxspeedParser.parse(tags.optString("maxspeed:forward", null)) : plainLimit;
                    SpeedLimit bwdLimit = tags.has("maxspeed:backward") ? MaxspeedParser.parse(tags.optString("maxspeed:backward", null)) : plainLimit;

                    if (onewayReverse) {
                        forward = bwdLimit;
                        backward = null;
                    } else if (oneway) {
                        forward = fwdLimit;
                        backward = null;
                    } else {
                        forward = fwdLimit;
                        backward = bwdLimit;
                    }
                }

                if (onewayReverse && len > 1) {
                    for (int j = 0; j < len / 2; j++) {
                        int opp = len - 1 - j;
                        double tLat = lat[j]; lat[j] = lat[opp]; lat[opp] = tLat;
                        double tLon = lon[j]; lon[j] = lon[opp]; lon[opp] = tLon;
                        long tNode = nodes[j]; nodes[j] = nodes[opp]; nodes[opp] = tNode;
                    }
                }

                ways.add(new Way(id, nodes, lat, lon, oneway, forward, backward, name));
            } else if ("node".equals(type)) {
                JSONObject tags = elem.optJSONObject("tags");
                if (tags != null && "traffic_signals".equals(tags.optString("highway"))) {
                    long id = elem.getLong("id");
                    double lat = elem.getDouble("lat");
                    double lon = elem.getDouble("lon");
                    String dirTag = tags.optString("traffic_signals:direction", "");
                    int dir = 0;
                    if ("forward".equalsIgnoreCase(dirTag)) {
                        dir = 1;
                    } else if ("backward".equalsIgnoreCase(dirTag)) {
                        dir = -1;
                    }
                    signals.add(new Signal(id, lat, lon, dir));
                }
            }
        }

        return new RoadTile(ways, signals);
    }
}
