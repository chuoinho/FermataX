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

/**
 * Shared in-process location hub multiplexing a single GNSS request across multiple consumers.
 */
public final class LocationHub {
    public interface Listener {
        void onLocation(@NonNull Location location);
    }

    private static final String TAG = "LocationHub";
    private static volatile LocationHub INSTANCE;

    private final Context context;
    private final LocationManager locationManager;
    private final HandlerThread thread;
    private final Handler handler;
    private final Map<String, Listener> consumers = new LinkedHashMap<>();
    private final LocationListener locationListener;

    private volatile Location latestLocation;
    private boolean registered;

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
        this.locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                latestLocation = location;
                List<Listener> targets;
                synchronized (LocationHub.this) {
                    targets = new ArrayList<>(consumers.values());
                }
                for (Listener l : targets) {
                    try {
                        l.onLocation(location);
                    } catch (Throwable t) {
                        Log.w(TAG, "Consumer threw exception in onLocation", t);
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {}
        };
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
        if (!hasPermission() || !isLocationEnabled() || locationManager == null) {
            return;
        }

        boolean success = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (locationManager.getProvider(LocationManager.FUSED_PROVIDER) != null) {
                    locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,
                            1000L, 0f, locationListener, thread.getLooper());
                    success = true;
                }
            } catch (Throwable t) {
                Log.d(TAG, "FUSED_PROVIDER registration failed, fallback to GPS", t);
            }
        }

        if (!success) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000L, 0f, locationListener, thread.getLooper());
                success = true;
            } catch (Throwable t) {
                Log.w(TAG, "GPS_PROVIDER registration failed", t);
            }
        }
        registered = success;
    }

    private void unregister() {
        if (locationManager != null && registered) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Throwable ignored) {}
        }
        registered = false;
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        if (locationManager == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!locationManager.isLocationEnabled()) return false;
            }
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
