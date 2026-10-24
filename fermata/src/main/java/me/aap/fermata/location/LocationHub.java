package me.aap.fermata.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;

/**
 * Shared in-process location hub multiplexing a single GNSS request across multiple consumers.
 */
public final class LocationHub {
    public interface Listener {
        void onLocation(@NonNull Location location);
        default void onLocation(@NonNull Location location, @NonNull LocationSource source) { onLocation(location); }
    }

    private static final String TAG = "LocationHub";
    private static final long INTERVAL_MS = 1_000;
    // A provider can stop delivering without an error (stuck GNSS driver). Register again, but back off:
    // a cold GNSS start can legitimately take longer than the first window.
    private static final long WATCHDOG_CHECK_MS = 5_000;
    private static final long WATCHDOG_FIRST_MS = 20_000;
    private static final long WATCHDOG_MAX_MS = 120_000;
    private static final long GAP_MS = 8_000;
    private static volatile LocationHub INSTANCE;
    static final BiConsumer<String, Map<String, Object>> DEFAULT_DIAGNOSTICS =
            (name, a) -> AndroidDiagnosticsRuntime.get().recordDetailed("location", name, a);
    @VisibleForTesting
    static volatile BiConsumer<String, Map<String, Object>> diagnostics = DEFAULT_DIAGNOSTICS;

    private final Context context;
    private final LocationManager locationManager;
    private final HandlerThread thread;
    private final Handler handler;
    private final Map<String, Listener> consumers = new LinkedHashMap<>();
    @VisibleForTesting static volatile GoogleFusedSource.Factory googleFactory = GoogleFusedSource.DEFAULT;
    private final Map<LocationSource, LocationListener> sourceListeners = new LinkedHashMap<>();
    private GoogleFusedSource googleSource;
    private final LocationSourceSelector selector = new LocationSourceSelector((from, to, reason) ->
            record("location_source_changed", "from", from == null ? "none" : from.id(), "to", to.id(), "reason", reason));

    private volatile Location latestLocation;
    private boolean registered;
    private volatile long lastDeliveryAt;
    private long watchdogMs = WATCHDOG_FIRST_MS;
    private int reRegistrations;
    private boolean inGap;
    private long gapStartedAt;
    private final Runnable watchdog = () -> checkWatchdog(SystemClock.elapsedRealtime());

    public static LocationHub get(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (LocationHub.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocationHub(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    private LocationHub(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.thread = new HandlerThread("LocationHub");
        this.thread.start();
        this.handler = new Handler(this.thread.getLooper());
    }

    private void accept(Location location, LocationSource source) {
        List<Listener> targets;
        synchronized (this) {
            if (consumers.isEmpty() || (source == LocationSource.GPS && !location.hasAccuracy())) return;
            long now = SystemClock.elapsedRealtime();
            long fixTime = location.getElapsedRealtimeNanos() / 1_000_000L;
            if (fixTime > now || now - fixTime > 2000) return;
            if (!selector.offer(source, fixTime, now)) return;
            latestLocation = location;
            lastDeliveryAt = now;
            watchdogMs = WATCHDOG_FIRST_MS;
            if (inGap) {
                inGap = false;
                record("location_gap_ended", "duration_ms", now - gapStartedAt);
            }
            targets = new ArrayList<>(consumers.values());
        }
        for (Listener listener : targets) {
            try { listener.onLocation(location, source); }
            catch (Throwable t) { Log.w(TAG, "Consumer threw exception in onLocation", t); }
        }
    }

    public synchronized void acquire(@NonNull String tag, @NonNull Listener l) {
        consumers.put(tag, l);
        if (!registered) {
            register();
        }
    }

    public synchronized void release(@NonNull String tag) {
        consumers.remove(tag);
        if (consumers.isEmpty() && registered) {
            unregister();
        }
    }

    @Nullable
    public Location latest() {
        return latestLocation;
    }

    /**
     * getLastKnownLocation of FUSED (API>=31, if present) and GPS; newest whose age by
     * elapsedRealtimeNanos is <= maxAgeMs, else null. Requires FINE or COARSE permission.
     */
    @Nullable
    @SuppressLint("MissingPermission")
    public Location freshSystemLocation(long maxAgeMs) {
        if (!hasPermission()) return null;
        if (locationManager == null) return null;

        Location newest = null;
        long nowNanos = SystemClock.elapsedRealtimeNanos();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (locationManager.getProvider(LocationManager.FUSED_PROVIDER) != null) {
                    Location fused = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
                    if (fused != null) {
                        long ageMs = getAgeMs(fused, nowNanos);
                        if (ageMs >= 0 && ageMs <= maxAgeMs) {
                            newest = fused;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        try {
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gps != null) {
                long ageMs = getAgeMs(gps, nowNanos);
                if (ageMs >= 0 && ageMs <= maxAgeMs) {
                    if (newest == null || getAgeNanos(gps, nowNanos) < getAgeNanos(newest, nowNanos)) {
                        newest = gps;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return newest;
    }

    @VisibleForTesting
    public Looper getLooperForTesting() {
        return thread.getLooper();
    }

    @VisibleForTesting
    public synchronized boolean isRegisteredForTesting() {
        return registered;
    }

    @VisibleForTesting
    public synchronized int consumerCountForTesting() {
        return consumers.size();
    }

    @VisibleForTesting
    synchronized void checkWatchdogForTesting(long now) {
        checkWatchdog(now);
    }

    @VisibleForTesting
    synchronized int reRegistrationsForTesting() {
        return reRegistrations;
    }

    /** Fused at high accuracy: the legacy request defaults to balanced power, which often has no Doppler speed. */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    static android.location.LocationRequest highAccuracyRequest() {
        return new android.location.LocationRequest.Builder(INTERVAL_MS)
                .setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY)
                .setMinUpdateDistanceMeters(0f)
                .build();
    }

    private synchronized void checkWatchdog(long now) {
        if (!registered) return;
        if (!inGap && now - lastDeliveryAt > GAP_MS) {
            inGap = true;
            gapStartedAt = lastDeliveryAt;
            record("location_gap_started", "stalled_for_ms", now - lastDeliveryAt);
        }
        if (now - lastDeliveryAt > watchdogMs) {
            Log.w(TAG, "No location for " + (now - lastDeliveryAt) + " ms, registering again");
            record("location_watchdog", "stalled_for_ms", now - lastDeliveryAt, "attempt", reRegistrations + 1);
            unregister();
            register();
            reRegistrations++;
            watchdogMs = Math.min(watchdogMs * 2, WATCHDOG_MAX_MS);
        } else {
            handler.removeCallbacks(watchdog);
            handler.postDelayed(watchdog, WATCHDOG_CHECK_MS);
        }
    }

    @VisibleForTesting
    public static synchronized void resetForTesting() {
        if (INSTANCE != null) {
            synchronized (INSTANCE) {
                INSTANCE.consumers.clear();
                INSTANCE.unregister();
                INSTANCE.latestLocation = null;
                INSTANCE.thread.quitSafely();
            }
            INSTANCE = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void register() {
        if (!hasPermission()) {
            record("location_registered", "reason", "no_permission");
            return;
        }
        if (!isLocationEnabled() || locationManager == null) {
            record("location_registered", "reason", "location_off");
            return;
        }
        registerPlatform(LocationSource.GPS, LocationManager.GPS_PROVIDER);
        try {
            googleSource = googleFactory.create(context, thread.getLooper(), l -> accept(l, LocationSource.GOOGLE_FUSED));
            if (googleSource == null) record("location_registered", "provider", "google_fused", "reason", "no_google_services");
            else {
                googleSource.start();
                registered = true;
                record("location_registered", "provider", "google_fused", "reason", "ok");
            }
        } catch (Throwable t) { googleRegistrationFailed(t); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerPlatform(LocationSource.PLATFORM_FUSED, LocationManager.FUSED_PROVIDER);
        else record("location_registered", "provider", "platform_fused", "reason", "unavailable");
        if (registered) {
            lastDeliveryAt = SystemClock.elapsedRealtime();
            handler.removeCallbacks(watchdog);
            handler.postDelayed(watchdog, WATCHDOG_CHECK_MS);
        }
    }

    @SuppressLint("MissingPermission")
    private void registerPlatform(LocationSource source, String provider) {
        try {
            if (locationManager.getProvider(provider) == null) {
                record("location_registered", "provider", source.id(), "reason", "unavailable");
                return;
            }
            LocationListener listener = location -> accept(location, source);
            sourceListeners.put(source, listener);
            if (source == LocationSource.PLATFORM_FUSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                locationManager.requestLocationUpdates(provider, highAccuracyRequest(), handler::post, listener);
            else locationManager.requestLocationUpdates(provider, INTERVAL_MS, 0f, listener, thread.getLooper());
            registered = true;
            record("location_registered", "provider", source.id(), "reason", "ok");
        } catch (Throwable t) {
            record("location_registered", "provider", source.id(), "reason", "failed", "error_class", t.getClass().getSimpleName());
        }
    }

    static void googleRegistrationFailed(Throwable t) {
        record("location_registered", "provider", "google_fused", "reason", "failed", "error_class", t.getClass().getSimpleName());
    }

    private void unregister() {
        handler.removeCallbacks(watchdog);
        if (registered) record("location_unregistered");
        if (locationManager != null) for (LocationListener listener : sourceListeners.values()) {
            try { locationManager.removeUpdates(listener); } catch (Throwable ignored) {}
        }
        sourceListeners.clear();
        if (googleSource != null) {
            try { googleSource.stop(); } catch (Throwable ignored) {}
            googleSource = null;
        }
        selector.reset();
        registered = false;
    }

    private static void record(String name, Object... keyValues) {
        Map<String, Object> a = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) a.put((String) keyValues[i], keyValues[i + 1]);
        try {
            diagnostics.accept(name, a);
        } catch (Throwable ignored) {
            // Diagnostics must never break location delivery.
        }
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        if (locationManager == null) return false;
        try {
            if (!locationManager.isLocationEnabled()) return false;
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Throwable e) {
            return false;
        }
    }

    private static long getAgeNanos(@NonNull Location loc, long nowNanos) {
        long elapsed = loc.getElapsedRealtimeNanos();
        if (elapsed > 0) {
            return nowNanos - elapsed;
        }
        return Math.max(0, (System.currentTimeMillis() - loc.getTime()) * 1_000_000L);
    }

    private static long getAgeMs(@NonNull Location loc, long nowNanos) {
        return getAgeNanos(loc, nowNanos) / 1_000_000L;
    }
}
