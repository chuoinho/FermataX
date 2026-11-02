package me.aap.fermata.ui.glass.road;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.location.LocationHub;

public final class GlassLimitsSignalsController {

    private final Context context;
    private final GlassLimitsSignalsView view;
    private final RoadDataSource dataSource;
    private final RoadMatcher matcher = new RoadMatcher();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean active = false;
    private float initialSignalDistance = -1f;

    public GlassLimitsSignalsController(@NonNull Context context,
                                        @NonNull GlassLimitsSignalsView view,
                                        @NonNull RoadDataSource dataSource) {
        this.context = context;
        this.view = view;
        this.dataSource = dataSource;
    }

    public synchronized void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        LocationHub hub = LocationHub.get(context);
        if (active) {
            hub.acquire("road_card", this::onLocation);
        } else {
            hub.release("road_card");
            initialSignalDistance = -1f;
            postToMain(view::showWaiting);
        }
    }

    public void onLocation(@NonNull Location loc) {
        if (!active) return;
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        float bearing = loc.hasBearing() ? loc.getBearing() : Float.NaN;
        float speed = loc.getSpeed();
        float accuracy = loc.hasAccuracy() ? loc.getAccuracy() : 10f;

        List<RoadTile> tiles = dataSource.tilesAround(lat, lon, bearing, true);
        RoadMatcher.Match match = matcher.update(tiles, lat, lon, accuracy, bearing, speed, System.currentTimeMillis());

        SpeedLimit limit = match != null ? match.limit() : null;
        String roadName = match == null ? null : match.way.name != null ? match.way.name : "";

        float signalDistanceM = -1f;
        float progress = 0f;

        if (match != null) {
            float rawDist = SignalFinder.distanceAhead(match, tiles, lat, lon);
            float showWithin = SignalFinder.showWithinM(speed);
            if (rawDist >= 0f && rawDist <= showWithin) {
                signalDistanceM = rawDist;
                if (initialSignalDistance <= 0f || rawDist > initialSignalDistance) {
                    initialSignalDistance = rawDist;
                }
                progress = initialSignalDistance > 0f ? Math.min(1.0f, rawDist / initialSignalDistance) : 0f;
            } else {
                initialSignalDistance = -1f;
            }
        } else {
            initialSignalDistance = -1f;
        }

        final float finalSigDist = signalDistanceM;
        final float finalProg = progress;
        postToMain(() -> {
            if (active) {
                view.show(limit, roadName, finalSigDist, finalProg);
            }
        });
    }

    private void postToMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }
}
