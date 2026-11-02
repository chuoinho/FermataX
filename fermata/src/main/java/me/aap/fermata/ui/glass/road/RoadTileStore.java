package me.aap.fermata.ui.glass.road;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class RoadTileStore {
    public static final long FRESH_MS = 30L * 24 * 3600_000L;
    public static final int FREQUENT_TRIP_ENDS = 2;

    private static final String PREF_PENDING = "pending_tiles";
    private static final String PREF_TRIP_END_PREFIX = "trip_end_";
    private static volatile RoadTileStore instance;

    private final File dir;
    private final SharedPreferences prefs;

    public static RoadTileStore get(Context context) {
        if (instance == null) {
            synchronized (RoadTileStore.class) {
                if (instance == null) {
                    File d = new File(context.getNoBackupFilesDir(), "road_tiles");
                    SharedPreferences p = context.getSharedPreferences("road_tiles", Context.MODE_PRIVATE);
                    instance = new RoadTileStore(d, p);
                }
            }
        }
        return instance;
    }

    public RoadTileStore(File dir, SharedPreferences prefs) {
        this.dir = dir;
        this.prefs = prefs;
    }

    private File fileFor(TileId t) {
        return new File(dir, t.key() + ".json.gz");
    }

    public boolean put(TileId t, String json, long nowMs) {
        if (dir.isFile()) return false;
        if (!dir.exists() && !dir.mkdirs()) return false;

        File tempFile = null;
        try {
            tempFile = File.createTempFile(t.key() + "_", ".tmp", dir);
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
                gzos.write(json.getBytes(StandardCharsets.UTF_8));
            }

            File target = fileFor(t);
            if (target.exists()) target.delete();
            if (!tempFile.renameTo(target)) {
                tempFile.delete();
                return false;
            }
            target.setLastModified(nowMs);
            return true;
        } catch (IOException e) {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
            return false;
        }
    }

    @Nullable
    public String read(TileId t) {
        File f = fileFor(t);
        if (!f.isFile()) return null;

        try (FileInputStream fis = new FileInputStream(f);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = gzis.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public long fetchedAt(TileId t) {
        File f = fileFor(t);
        return f.isFile() ? f.lastModified() : -1L;
    }

    public boolean isFresh(TileId t, long nowMs) {
        long mtime = fetchedAt(t);
        return mtime > 0 && (nowMs - mtime) < FRESH_MS;
    }

    public void addPending(TileId t) {
        synchronized (prefs) {
            Set<String> set = new HashSet<>(prefs.getStringSet(PREF_PENDING, Collections.emptySet()));
            set.add(t.key());
            prefs.edit().putStringSet(PREF_PENDING, set).commit();
        }
    }

    public Set<TileId> pending() {
        Set<String> set;
        synchronized (prefs) {
            set = prefs.getStringSet(PREF_PENDING, Collections.emptySet());
        }
        Set<TileId> result = new HashSet<>();
        for (String key : set) {
            TileId t = TileId.fromKey(key);
            if (t != null) result.add(t);
        }
        return result;
    }

    public void removePending(TileId t) {
        synchronized (prefs) {
            Set<String> set = new HashSet<>(prefs.getStringSet(PREF_PENDING, Collections.emptySet()));
            if (set.remove(t.key())) {
                prefs.edit().putStringSet(PREF_PENDING, set).commit();
            }
        }
    }

    public int recordTripEnd(TileId t) {
        String k = PREF_TRIP_END_PREFIX + t.key();
        synchronized (prefs) {
            int count = prefs.getInt(k, 0) + 1;
            prefs.edit().putInt(k, count).commit();
            return count;
        }
    }

    public Set<TileId> frequentPlaces() {
        Set<TileId> result = new HashSet<>();
        Map<String, ?> all;
        synchronized (prefs) {
            all = prefs.getAll();
        }
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey().startsWith(PREF_TRIP_END_PREFIX) && e.getValue() instanceof Integer) {
                int count = (Integer) e.getValue();
                if (count >= FREQUENT_TRIP_ENDS) {
                    TileId t = TileId.fromKey(e.getKey().substring(PREF_TRIP_END_PREFIX.length()));
                    if (t != null) result.add(t);
                }
            }
        }
        return result;
    }

    public List<TileId> staleTiles(long nowMs) {
        if (!dir.isDirectory()) return Collections.emptyList();
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return Collections.emptyList();

        List<TileId> stale = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (name.endsWith(".json.gz")) {
                String key = name.substring(0, name.length() - ".json.gz".length());
                TileId t = TileId.fromKey(key);
                if (t != null && !isFresh(t, nowMs)) {
                    stale.add(t);
                }
            }
        }
        Collections.sort(stale, new Comparator<TileId>() {
            @Override
            public int compare(TileId o1, TileId o2) {
                return Long.compare(fetchedAt(o1), fetchedAt(o2));
            }
        });
        return stale;
    }

    public void clearAll() {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }
        synchronized (prefs) {
            prefs.edit().clear().apply();
        }
    }
}
