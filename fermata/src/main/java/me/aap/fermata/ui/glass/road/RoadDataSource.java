package me.aap.fermata.ui.glass.road;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class RoadDataSource {

    private static volatile RoadDataSource instance;

    private final RoadTileStore store;
    private final OverpassClient client;
    private final Executor io;
    private final LongSupplier clock;
    private final Random random = new Random();

    private final Map<TileId, RoadTile> lru = new LinkedHashMap<TileId, RoadTile>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<TileId, RoadTile> eldest) {
            return size() > 16;
        }
    };

    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private int consecutiveFailures = 0;
    private long nextAllowedFetchTime = 0L;

    public static RoadDataSource get(Context context) {
        if (instance == null) {
            synchronized (RoadDataSource.class) {
                if (instance == null) {
                    RoadTileStore store = RoadTileStore.get(context);
                    OverpassClient client = new OverpassClient(null, null, "FermataX-RoadCard/1.0 (Android)");
                    Executor io = Executors.newSingleThreadExecutor();
                    instance = new RoadDataSource(store, client, io, System::currentTimeMillis);
                }
            }
        }
        return instance;
    }

    public RoadDataSource(RoadTileStore store, OverpassClient client, Executor io, LongSupplier clock) {
        this.store = store;
        this.client = client;
        this.io = io;
        this.clock = clock;
    }

    @NonNull
    public List<RoadTile> tilesAround(double lat, double lon, float bearingDeg, boolean allowLive) {
        TileId center = TileId.of(lat, lon);
        List<TileId> around = center.around();
        List<RoadTile> result = new ArrayList<>();

        for (TileId t : around) {
            RoadTile rt;
            synchronized (lru) {
                rt = lru.get(t);
            }
            if (rt != null) {
                result.add(rt);
            } else {
                String json = store.read(t);
                if (json != null) {
                    try {
                        rt = RoadTile.parse(json);
                        synchronized (lru) {
                            lru.put(t, rt);
                        }
                        result.add(rt);
                    } catch (JSONException ignored) {
                    }
                }
            }
        }

        if (allowLive) {
            TileId ahead = Float.isNaN(bearingDeg) ? null : center.ahead(bearingDeg);
            boolean centerMissing = store.read(center) == null;
            boolean aheadMissing = ahead != null && !center.equals(ahead) && store.read(ahead) == null;

            if (centerMissing || aheadMissing) {
                triggerLiveFetch(center, ahead, centerMissing, aheadMissing);
            }
        }

        return Collections.unmodifiableList(result);
    }

    private void triggerLiveFetch(TileId center, @Nullable TileId ahead, boolean centerMissing, boolean aheadMissing) {
        long now = clock.getAsLong();
        if (now < nextAllowedFetchTime) {
            return;
        }

        if (!inFlight.compareAndSet(false, true)) {
            return;
        }

        io.execute(() -> {
            try {
                if (clock.getAsLong() < nextAllowedFetchTime) {
                    return;
                }

                if (centerMissing && store.read(center) == null) {
                    boolean ok = download(center);
                    if (!ok) {
                        recordFailure();
                        return;
                    } else {
                        recordSuccess();
                    }
                }

                if (aheadMissing && ahead != null && !center.equals(ahead) && store.read(ahead) == null) {
                    boolean ok = download(ahead);
                    if (!ok) {
                        recordFailure();
                    } else {
                        recordSuccess();
                    }
                }
            } finally {
                inFlight.set(false);
            }
        });
    }

    private synchronized void recordFailure() {
        consecutiveFailures++;
        long backoff = OverpassClient.backoffMs(consecutiveFailures - 1, random);
        nextAllowedFetchTime = clock.getAsLong() + backoff;
    }

    private synchronized void recordSuccess() {
        consecutiveFailures = 0;
        nextAllowedFetchTime = 0L;
    }

    public boolean download(TileId t) {
        try {
            String json = client.fetch(t);
            RoadTile parsed = RoadTile.parse(json);
            long now = clock.getAsLong();
            boolean stored = store.put(t, json, now);
            if (stored) {
                synchronized (lru) {
                    lru.put(t, parsed);
                }
                store.removePending(t);
                return true;
            } else {
                store.addPending(t);
                return false;
            }
        } catch (Exception e) {
            store.addPending(t);
            return false;
        }
    }
}
