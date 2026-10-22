package me.aap.fermata.ui.glass.speedo;

import android.content.Context;
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
import java.util.Date;
import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.utils.log.Log;

/**
 * Debug-only raw trip log for replay tests (see TripReplay in the unit tests). Files stay in the
 * app's external files dir ({@code trip_logs/}) and are only retrieved manually with adb pull.
 * Must be used from a single thread (the tracking worker).
 */
final class TripLogRecorder {
    static final String PREF_ENABLED = "log_trips";
    private final File dir;
    @Nullable private BufferedWriter out;
    private int unflushed;

    private TripLogRecorder(File dir) {
        this.dir = dir;
    }

    /** Null unless this is a debug build or the hidden {@code log_trips} preference is set. */
    @Nullable
    static TripLogRecorder create(Context context) {
        boolean enabled = BuildConfig.DEBUG || context.getSharedPreferences("speedo_trip_v2",
                Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false);
        File base = enabled ? context.getExternalFilesDir("trip_logs") : null;
        return base == null ? null : new TripLogRecorder(base);
    }

    void event(String name, long elapsedMs) {
        if ("start".equals(name)) open();
        write("E," + elapsedMs + "," + name);
        if (!"start".equals(name)) flush();
        if ("stop".equals(name)) close();
    }

    void location(Location l) {
        long t = l.getElapsedRealtimeNanos() / 1_000_000;
        boolean speedAcc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && l.hasSpeedAccuracy();
        boolean mock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? l.isMock() : l.isFromMockProvider();
        write(String.format(Locale.ROOT, "L,%d,%s,%.7f,%.7f,%s,%s,%s,%d", t, l.getProvider(),
                l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : "",
                l.hasSpeed() ? l.getSpeed() : "", speedAcc ? l.getSpeedAccuracyMetersPerSecond() : "",
                mock ? 1 : 0));
    }

    void imu(long elapsedMs, boolean quiet) {
        write("I," + elapsedMs + "," + (quiet ? 1 : 0));
    }

    private void open() {
        close();
        String name = "trip-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".csv";
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, name)), StandardCharsets.UTF_8));
            out.write("type,elapsedMs,provider,lat,lon,acc,speed,speedAcc,mock\n");
        } catch (IOException e) {
            Log.w(e, "Unable to open trip log");
            out = null;
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
