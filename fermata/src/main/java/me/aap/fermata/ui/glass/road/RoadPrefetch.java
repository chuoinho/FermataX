package me.aap.fermata.ui.glass.road;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.location.Location;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.ui.activity.MainActivityPrefs;

public final class RoadPrefetch {

    public static final int JOB_CATCH_UP = 0x52440001;
    public static final int JOB_REFRESH = 0x52440002;

    public static boolean isEnabled(Context c) {
        try {
            return MainActivityPrefs.get().getBooleanPref(MainActivityPrefs.GLASS_ROAD_DATA);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void onTripEnded(Context c, @Nullable Location last) {
        if (!isEnabled(c)) return;
        RoadTileStore store = RoadTileStore.get(c);
        if (last != null) {
            TileId t = TileId.of(last.getLatitude(), last.getLongitude());
            store.recordTripEnd(t);
        }
        scheduleCatchUp(c);
        scheduleRefresh(c);
    }

    public static void scheduleCatchUp(Context c) {
        if (!isEnabled(c)) return;
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo.Builder builder = new JobInfo.Builder(JOB_CATCH_UP,
                new ComponentName(c, RoadPrefetchJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(false);
        js.schedule(builder.build());
    }

    public static void scheduleRefresh(Context c) {
        if (!isEnabled(c)) return;
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo.Builder builder = new JobInfo.Builder(JOB_REFRESH,
                new ComponentName(c, RoadPrefetchJobService.class))
                .setPeriodic(24L * 3600_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setPersisted(false);
        js.schedule(builder.build());
    }

    public static void disable(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            js.cancel(JOB_CATCH_UP);
            js.cancel(JOB_REFRESH);
        }
        RoadTileStore.get(c).clearAll();
    }

    public static List<TileId> catchUpWork(RoadTileStore s) {
        Set<TileId> work = new LinkedHashSet<>();
        for (TileId p : s.pending()) {
            if (s.fetchedAt(p) <= 0) {
                work.add(p);
            }
        }
        for (TileId fp : s.frequentPlaces()) {
            double[] bbox = fp.bbox();
            double cLat = (bbox[0] + bbox[2]) / 2.0;
            double cLon = (bbox[1] + bbox[3]) / 2.0;
            for (TileId t : TileId.within(cLat, cLon, 50.0)) {
                if (s.fetchedAt(t) <= 0) {
                    work.add(t);
                }
            }
        }
        return new ArrayList<>(work);
    }

    public static List<TileId> refreshWork(RoadTileStore s, long now) {
        List<TileId> stale = s.staleTiles(now);
        if (stale.isEmpty()) return Collections.emptyList();
        int count = (int) Math.ceil(stale.size() / 30.0);
        return new ArrayList<>(stale.subList(0, Math.min(count, stale.size())));
    }

    private RoadPrefetch() {}
}
