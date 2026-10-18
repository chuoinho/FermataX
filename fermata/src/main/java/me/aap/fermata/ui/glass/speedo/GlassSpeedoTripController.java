package me.aap.fermata.ui.glass.speedo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Controller and state engine for automotive GPS speedometer and trip computer.
 */
public class GlassSpeedoTripController {

    private static final float SPEED_THRESHOLD_KMH = 3.0f;
    private static final float MAX_ACCURACY_METERS = 35.0f;
    private static final float KM_TO_MILES = 0.621371f;

    public interface Listener {
        void onSpeedUpdated(float speedKmh, TripState state);
    }

    public static class TripState {
        private float tripDistanceMeters;
        private long driveTimeSeconds;
        private float maxSpeedKmh;

        public TripState() {
            this(0f, 0, 0f);
        }

        public TripState(float tripDistanceMeters, long driveTimeSeconds, float maxSpeedKmh) {
            this.tripDistanceMeters = tripDistanceMeters;
            this.driveTimeSeconds = driveTimeSeconds;
            this.maxSpeedKmh = maxSpeedKmh;
        }

        public synchronized void addDistance(float meters, long deltaSeconds) {
            if (meters > 0) {
                this.tripDistanceMeters += meters;
            }
            if (deltaSeconds > 0) {
                this.driveTimeSeconds += deltaSeconds;
            }
        }

        public synchronized void updateMaxSpeed(float speedKmh) {
            if (speedKmh > this.maxSpeedKmh) {
                this.maxSpeedKmh = speedKmh;
            }
        }

        public synchronized float getDistanceMeters() {
            return tripDistanceMeters;
        }

        public synchronized float getDistanceKm() {
            return tripDistanceMeters / 1000f;
        }

        public synchronized float getDistanceMiles() {
            return getDistanceKm() * KM_TO_MILES;
        }

        public synchronized long getDriveTimeSeconds() {
            return driveTimeSeconds;
        }

        public synchronized float getAverageSpeedKmh() {
            if (driveTimeSeconds <= 0) return 0f;
            float hours = driveTimeSeconds / 3600f;
            return getDistanceKm() / hours;
        }

        public synchronized float getAverageSpeedMph() {
            return getAverageSpeedKmh() * KM_TO_MILES;
        }

        public synchronized float getMaxSpeedKmh() {
            return maxSpeedKmh;
        }

        public synchronized String getFormattedDriveTime() {
            long hours = driveTimeSeconds / 3600;
            long minutes = (driveTimeSeconds % 3600) / 60;
            return String.format(Locale.US, "%02dh %02dm", hours, minutes);
        }

        public synchronized void reset() {
            tripDistanceMeters = 0f;
            driveTimeSeconds = 0;
            maxSpeedKmh = 0f;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TripState tripState;

    private Location lastLocation;
    private long lastLocationTimestampMs;
    private boolean running = false;
    private float currentSpeedKmh = -1f;
    private LocationManager locationManager;
    private LocationListener locationListener;

    public GlassSpeedoTripController(@NonNull Context context, @Nullable Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.tripState = new TripState();
    }

    @SuppressLint("MissingPermission")
    public synchronized void start() {
        if (running) return;
        running = true;

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                onLocationReceived(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                if (LocationManager.GPS_PROVIDER.equals(provider)) {
                    currentSpeedKmh = -1f;
                    notifyListener(currentSpeedKmh, tripState);
                }
            }
        };

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        1.0f,
                        locationListener,
                        Looper.getMainLooper()
                );
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // Permission might be denied or provider missing in emulator/DHU
        }

        // Notify initial state: -1f indicates no GPS fix yet
        notifyListener(currentSpeedKmh, tripState);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {}
        }
        currentSpeedKmh = -1f;
    }

    public synchronized void resetTrip() {
        tripState.reset();
        notifyListener(currentSpeedKmh, tripState);
    }

    public TripState getTripState() {
        return tripState;
    }

    public void onLocationReceived(@NonNull Location location) {
        float rawSpeedKmh = location.hasSpeed() ? (location.getSpeed() * 3.6f) : 0f;
        float filteredSpeedKmh = (rawSpeedKmh >= SPEED_THRESHOLD_KMH) ? rawSpeedKmh : 0f;
        currentSpeedKmh = filteredSpeedKmh;

        boolean accurate = !location.hasAccuracy() || (location.getAccuracy() <= MAX_ACCURACY_METERS);
        long now = location.getTime();

        if (accurate && filteredSpeedKmh > 0 && lastLocation != null) {
            float distMeters = lastLocation.distanceTo(location);
            long dtSeconds = (now - lastLocationTimestampMs) / 1000L;
            if (dtSeconds > 0 && dtSeconds <= 30) {
                tripState.addDistance(distMeters, dtSeconds);
            }
        }

        if (accurate) {
            lastLocation = location;
            lastLocationTimestampMs = now;
        }

        tripState.updateMaxSpeed(filteredSpeedKmh);

        notifyListener(filteredSpeedKmh, tripState);
    }

    private void notifyListener(float speedKmh, TripState state) {
        if (listener == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onSpeedUpdated(speedKmh, state);
        } else {
            mainHandler.post(() -> listener.onSpeedUpdated(speedKmh, state));
        }
    }

    public static String formatSpeed(float speedKmh, boolean isMetric) {
        if (isMetric) {
            return String.format(Locale.US, "%.0f km/h", speedKmh);
        } else {
            return String.format(Locale.US, "%.0f mph", speedKmh * KM_TO_MILES);
        }
    }

    public static String formatDistance(float distanceMeters, boolean isMetric) {
        float km = distanceMeters / 1000f;
        if (isMetric) {
            return String.format(Locale.US, "%.1f km", km);
        } else {
            return String.format(Locale.US, "%.1f mi", km * KM_TO_MILES);
        }
    }
}
