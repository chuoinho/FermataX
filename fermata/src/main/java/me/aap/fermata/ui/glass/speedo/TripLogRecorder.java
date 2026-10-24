package me.aap.fermata.ui.glass.speedo;

import android.content.Context;
import me.aap.fermata.location.LocationSource;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.utils.log.Log;

/**
 * Raw trip log for replay tests (see TripReplay in the unit tests) and diagnostic reports. Written
 * while detailed diagnostics are on (always in debug builds or with the hidden {@code log_trips}
 * preference). Holds no coordinates: a fix row carries only the distance to the previous fix, so a
 * route cannot be rebuilt from it. Files stay in {@code trip_logs/}; the newest five are kept.
 * Must be used from a single thread (the tracking worker).
 */
final class TripLogRecorder {
    static final String PREF_ENABLED = "log_trips";
    static final String HEADER = "#fermatax-trip-v2";
    private static final int MAX_FILES = 5;
    private final File dir;
    private final BooleanSupplier enabled;
    @Nullable private BufferedWriter out;
    @Nullable private Location prev;
    private LocationSource prevSource;
    private boolean started;
    private int unflushed;

    TripLogRecorder(File dir, BooleanSupplier enabled) {
        this.dir = dir;
        this.enabled = enabled;
    }

    /** Null when there is no external files dir. */
    @Nullable
    static TripLogRecorder create(Context context) {
        File base = context.getExternalFilesDir("trip_logs");
        if (base == null) return null;
        SharedPreferences prefs = context.getSharedPreferences("speedo_trip_v2", Context.MODE_PRIVATE);
        return new TripLogRecorder(base, () -> BuildConfig.DEBUG || prefs.getBoolean(PREF_ENABLED, false)
                || AndroidDiagnosticsRuntime.get().isDetailedEnabled());
    }

    void event(String name, long elapsedMs) {
        if ("start".equals(name)) {
            close();
            started = true;
        }
        if (active()) {
            write("E," + elapsedMs + "," + name);
            if (!"start".equals(name)) flush();
        }
        if ("stop".equals(name)) {
            close();
            started = false;
        }
    }

    void location(Location l) { location(l, null); }

    void location(Location l, LocationSource source) {
        if (!active()) return;
        long t = l.getElapsedRealtimeNanos() / 1_000_000;
        boolean speedAcc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && l.hasSpeedAccuracy();
        boolean mock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? l.isMock() : l.isFromMockProvider();
        String step = (prev == null || (prevSource != null && source != null && prevSource != source)) ? "" : String.valueOf(Math.round(prev.distanceTo(l)));
        prev = l;
        prevSource = source;
        write(String.format(Locale.ROOT, "L,%d,%s,%s,%s,%s,%s,%d,%s", t, l.getProvider(), step,
                l.hasAccuracy() ? l.getAccuracy() : "", l.hasSpeed() ? l.getSpeed() : "",
                speedAcc ? l.getSpeedAccuracyMetersPerSecond() : "", mock ? 1 : 0, source == null ? "" : source.id()));
        // About one small write a second, so an export during the trip sees the latest fixes.
        flush();
    }

    void imu(long elapsedMs, boolean quiet) {
        if (active()) write("I," + elapsedMs + "," + (quiet ? 1 : 0));
    }

    /** Opens a file when logging was switched on during a trip, closes it when switched off. */
    private boolean active() {
        boolean on = started && enabled.getAsBoolean();
        if (on && out == null) open();
        else if (!on && out != null) close();
        return out != null;
    }

    private void open() {
        close();
        prune();
        String name = "trip-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date()) + ".csv";
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, name)), StandardCharsets.UTF_8));
            out.write(HEADER + "\n");
            out.write("type,elapsedMs,provider,stepM,acc,speed,speedAcc,mock,source\n");
        } catch (IOException e) {
            Log.w(e, "Unable to open trip log");
            out = null;
        }
        prev = null;
        prevSource = null;
    }

    /** Keeps room for one new file within {@link #MAX_FILES}. */
    private void prune() {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".csv"));
        if (files == null || files.length < MAX_FILES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i <= files.length - MAX_FILES; i++) {
            if (!files[i].delete()) Log.w("Unable to delete old trip log");
        }
    }

    private void write(String line) {
        BufferedWriter w = out;
        if (w == null) return;
        try {
            w.write(line);
            w.write('\n');
            if (++unflushed >= 30) flush();
        } catch (IOException e) {
            Log.w(e, "Trip log write failed");
            close();
        }
    }

    private void flush() {
        BufferedWriter w = out;
        if (w == null) return;
        try {
            w.flush();
            unflushed = 0;
        } catch (IOException ignored) {
        }
    }

    void close() {
        BufferedWriter w = out;
        out = null;
        if (w == null) return;
        try {
            w.close();
        } catch (IOException ignored) {
        }
    }
}
