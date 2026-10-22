package me.aap.fermata.ui.glass.speedo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * In-process automotive GPS speedometer and trip computer controller.
 * Feeds a single GNSS provider (FUSED on API 31+, otherwise GPS) and IMU stillness to TripEngine.
 */
public final class GlassSpeedoTripController {
    public interface Listener { void onSpeedUpdated(float speedKmh, TripEngine.State state); }

    private static final String TAG = "FermataTrip";
    private final Context context;
    private final Listener listener;
    private final Runnable deliver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TripEngine engine = new TripEngine();
    private final TripStore store;
    private final StationaryDetector stationaryDetector = new StationaryDetector();

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor linearAccSensor, gyroSensor;

    private final HandlerThread thread;
    private final Handler worker;

    private volatile TripEngine.State snapshot;
    private boolean attached, registered, imuRegistered;
    private volatile boolean running;
    // Dashboard and TripTrackingService may both hold the controller; it only stops when both let go.
    private int holders;
    private long lastCallback, lastProgress, lastSave;
    private volatile long nextRetry;
    private volatile long retryDelay = 5_000;
    @Nullable private final TripLogRecorder tripLog;

    public GlassSpeedoTripController(@NonNull Context context, @Nullable Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.deliver = () -> {
            if (attached && this.listener != null && snapshot != null) {
                this.listener.onSpeedUpdated(snapshot.speedKmh(), snapshot);
            }
        };
        this.store = new TripStore(this.context);
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
        this.sensorManager = (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
        this.thread = new HandlerThread("GlassSpeedo");
        this.thread.start();
        this.worker = new Handler(this.thread.getLooper());
        this.tripLog = TripLogRecorder.create(this.context);
        engine.restore(store.load(), store.suspendedForMs());
        snapshot = engine.state();
    }

    public synchronized void start() {
        if (++holders > 1 && attached) {
            mainHandler.removeCallbacks(deliver);
            mainHandler.post(deliver);
            return;
        }
        attached = true;
        if (!store.isUserPaused()) {
            startTrip();
        } else {
            showUnavailable(TripEngine.Signal.PAUSED);
        }
    }

    public synchronized void startTrip() {
        if (!attached) return;
        if (!hasPermission()) {
            showUnavailable(TripEngine.Signal.PERMISSION);
            return;
        }
        if (!isLocationEnabled()) {
            showUnavailable(TripEngine.Signal.GPS_OFF);
            return;
        }
        store.setUserPaused(false);
        worker.post(() -> {
            running = true;
            Location seed = queryRecentSeedLocation();
            long nowTime = now();
            long seedElapsed = (seed != null && seed.getElapsedRealtimeNanos() > 0)
                    ? seed.getElapsedRealtimeNanos() / 1_000_000
                    : nowTime;
            boolean seedValid = (seed != null && Math.abs(nowTime - seedElapsed) <= 2_000L);
            engine.start(nowTime);
            if (tripLog != null) tripLog.event("start", nowTime);
            lastCallback = lastProgress = nowTime;
            retryDelay = 5_000;
            nextRetry = 0;
            registerLocation();
            if (seedValid) handleLocation(seed);
            checkpoint();
            worker.removeCallbacks(heartbeat);
            worker.post(heartbeat);
            publish();
        });
    }

    public synchronized void stop() {
        if (holders > 0 && --holders > 0) return;
        attached = false;
        worker.post(() -> {
            running = false;
            worker.removeCallbacks(heartbeat);
            unregisterLocation();
            unregisterImu();
            // A system stop: the trip clock stops and no position is bridged across it.
            engine.suspend(now());
            if (tripLog != null) tripLog.event("stop", now());
            checkpoint();
            publish();
        });
    }

    public synchronized void onHostResumed() {
        if (attached) {
            retryDelay = 5_000;
            nextRetry = 0;
            TripEngine.State s = snapshot;
            if (s != null && (s.signal() == TripEngine.Signal.PERMISSION
                    || s.signal() == TripEngine.Signal.GPS_OFF || s.signal() == TripEngine.Signal.ERROR)) {
                startTrip();
            } else {
                worker.post(() -> {
                    if (running && (!registered || now() - lastProgress > 8_000)) {
                        registerLocation();
                    }
                });
            }
        }
    }

    public synchronized void toggleTrip() {
        if (snapshot != null && snapshot.active()) {
            pauseTrip();
        } else {
            startTrip();
        }
    }

    public synchronized void pauseTrip() {
        store.setUserPaused(true);
        worker.post(() -> {
            running = false;
            worker.removeCallbacks(heartbeat);
            unregisterLocation();
            unregisterImu();
            engine.pause(now());
            if (tripLog != null) tripLog.event("pause", now());
            checkpoint();
            publish();
        });
    }

    public synchronized void resetTrip() {
        worker.post(() -> {
            engine.reset(now());
            if (tripLog != null) tripLog.event("reset", now());
            checkpoint();
            publish();
        });
    }

    public TripEngine.State getTripState() { return snapshot; }

    /** True while location is actually being recorded, whoever started it. */
    public boolean isTracking() { return running; }

    private void handleLocation(Location location) {
        if (!running || location == null) return;
        if (!store.isAllowMock() && isMock(location)) return;
        long nowTime = now();
        lastCallback = nowTime;
        if (tripLog != null) tripLog.location(location);
        long fixTime = location.getElapsedRealtimeNanos() > 0
                ? location.getElapsedRealtimeNanos() / 1_000_000 : nowTime;
        boolean speedAcc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy();
        TripEngine.Fix fix = FixNormalizer.of(fixTime, location.getLatitude(), location.getLongitude(),
                location.hasAccuracy(), location.getAccuracy(), location.hasSpeed(), location.getSpeed(),
                speedAcc, speedAcc ? location.getSpeedAccuracyMetersPerSecond() : 0f);
        if (engine.accept(fix, nowTime)) {
            lastProgress = nowTime;
            retryDelay = 5_000;
        }
        publish();
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) { handleLocation(location); }
        @Override public void onProviderEnabled(String provider) {
            if (running) { retryDelay = 5_000; nextRetry = 0; registerLocation(); }
        }
        @Override public void onProviderDisabled(String provider) {
            if (!isLocationEnabled()) {
                engine.unavailable(TripEngine.Signal.GPS_OFF, now());
                publish();
            }
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    private final LocationListener fusedListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) { handleLocation(location); }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    private final Runnable heartbeat = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override public void run() {
            if (!running) return;
            long time = now();
            updateImuRegistration(time);
            boolean stationary = imuRegistered && stationaryDetector.update();
            if (tripLog != null && imuRegistered) tripLog.imu(time, stationary);
            engine.tick(time);
            engine.motion(time, stationary, Double.NaN);
            if (!hasPermission()) {
                unregisterLocation();
                unregisterImu();
                engine.unavailable(TripEngine.Signal.PERMISSION, time);
            } else if (!isLocationEnabled()) {
                unregisterImu();
                engine.unavailable(TripEngine.Signal.GPS_OFF, time);
            } else {
                if (time - lastCallback > 8_000 && retryDelay > 15_000) {
                    retryDelay = 5_000;
                    if (nextRetry > time + 5_000) {
                        nextRetry = time + 5_000;
                    }
                }
                // Re-registering does not help inside a tunnel; only a dead or silent provider warrants it.
                if (time >= nextRetry && (!registered || time - lastCallback > 60_000)) {
                    Log.d(TAG, "Watchdog retry location registration; callbackAgeMs=" + (time - lastCallback));
                    registerLocation();
                }
            }
            if (time - lastSave >= 10_000) checkpoint();
            publish();
            worker.postDelayed(this, 1_000);
        }
    };

    @SuppressLint("MissingPermission")
    private Location queryRecentSeedLocation() {
        if (locationManager == null || !hasPermission()) return null;
        Location best = null;
        String[] providers = {
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };
        for (String p : providers) {
            try {
                Location cand = locationManager.getLastKnownLocation(p);
                if (cand != null) {
                    if (best == null || cand.getTime() > best.getTime()
                            || (cand.getTime() >= best.getTime() - 10_000L && cand.getAccuracy() < best.getAccuracy())) {
                        best = cand;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Location cand = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
                if (cand != null) {
                    if (best == null || cand.getTime() > best.getTime()
                            || (cand.getTime() >= best.getTime() - 10_000L && cand.getAccuracy() < best.getAccuracy())) {
                        best = cand;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (best != null) {
            long ageMs = System.currentTimeMillis() - best.getTime();
            if (ageMs <= 60_000) { // Valid seed if within last 60 seconds
                Log.d(TAG, "Found recent seed location from " + best.getProvider() + " (age=" + ageMs + "ms)");
                return best;
            }
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    private void registerLocation() {
        unregisterLocation();
        long time = now();
        nextRetry = time + retryDelay;
        retryDelay = Math.min(60_000, retryDelay * 2);
        if (!hasPermission() || !isLocationEnabled() || locationManager == null) return;

        boolean registeredAny = false;
        Looper looper = thread.getLooper();
        // F-4: Single provider owns measurement stream.
        // API >= 31 and FUSED_PROVIDER available -> use FUSED only. Otherwise use GPS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (locationManager.getProvider(LocationManager.FUSED_PROVIDER) != null) {
                    locationManager.requestLocationUpdates(LocationManager.FUSED_PROVIDER,
                            1_000L, 0f, fusedListener, looper);
                    registeredAny = true;
                }
            } catch (Throwable error) {
                Log.d(TAG, "FUSED_PROVIDER unavailable", error);
            }
        }
        if (!registeredAny) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                            1_000L, 0f, locationListener, looper);
                    registeredAny = true;
                }
            } catch (Throwable error) {
                Log.d(TAG, "GPS_PROVIDER registration failed", error);
            }
        }
        registered = registeredAny;
        Log.d(TAG, "Registered GNSS single-source; success=" + registeredAny + ", retryDelayMs=" + retryDelay);
    }

    private void unregisterLocation() {
        if (locationManager != null && registered) {
            try { locationManager.removeUpdates(locationListener); } catch (Throwable ignored) {}
            try { locationManager.removeUpdates(fusedListener); } catch (Throwable ignored) {}
        }
        registered = false;
    }

    private void updateImuRegistration(long now) {
        if (now - lastCallback > 2_000) {
            if (!imuRegistered && sensorManager != null) {
                stationaryDetector.reset();
                if (linearAccSensor != null) sensorManager.registerListener(
                        stationaryDetector, linearAccSensor, SensorManager.SENSOR_DELAY_NORMAL, worker);
                if (gyroSensor != null) sensorManager.registerListener(
                        stationaryDetector, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL, worker);
                imuRegistered = true;
            }
        } else {
            unregisterImu();
        }
    }

    private void unregisterImu() {
        if (imuRegistered && sensorManager != null) {
            sensorManager.unregisterListener(stationaryDetector);
            imuRegistered = false;
            stationaryDetector.reset();
        }
    }

    private void checkpoint() {
        lastSave = now();
        if (!store.save(engine.state(), engine.previousTrip())) Log.w(TAG, "Trip checkpoint write failed");
    }

    private void publish() {
        snapshot = engine.state();
        mainHandler.removeCallbacks(deliver);
        mainHandler.post(deliver);
    }

    public void showUnavailable(TripEngine.Signal reason) {
        TripEngine.State current = snapshot != null ? snapshot : store.load();
        snapshot = new TripEngine.State(current.distanceMeters(), current.estimatedMeters(),
                current.uncertaintyMeters(), current.gapMeters(), current.totalTimeMs(),
                current.movingTimeMs(), current.maxSpeedKmh(), -1, reason,
                current.incomplete(), false);
        mainHandler.removeCallbacks(deliver);
        mainHandler.post(deliver);
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
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

    private static boolean isMock(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock();
        } else {
            return location.isFromMockProvider();
        }
    }

    private static long now() { return SystemClock.elapsedRealtime(); }

    Handler getWorkerForTesting() { return worker; }
    HandlerThread getThreadForTesting() { return thread; }
    boolean isRunningForTesting() { return running; }

    @androidx.annotation.VisibleForTesting
    public void destroyForTesting() {
        stop();
        thread.quitSafely();
    }

    final class StationaryDetector implements SensorEventListener {
        private double accSum, accSq, gyroMax;
        private int n;
        private int quietWindows;

        @Override public void onSensorChanged(SensorEvent e) {
            if (e.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                onAcceleration(e.values[0], e.values[1], e.values[2]);
            } else if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                onGyroscope(e.values[0], e.values[1], e.values[2]);
            }
        }

        void onAcceleration(double x, double y, double z) {
            double m = Math.sqrt(x * x + y * y + z * z);
            accSum += m;
            accSq += m * m;
            n++;
        }

        void onGyroscope(double x, double y, double z) {
            double g = Math.sqrt(x * x + y * y + z * z);
            gyroMax = Math.max(gyroMax, g);
        }

        boolean update() {
            double var = n > 1 ? (accSq / n) - Math.pow(accSum / n, 2) : Double.MAX_VALUE;
            quietWindows = (var < 0.01 && gyroMax < 0.03) ? quietWindows + 1 : 0;
            accSum = accSq = gyroMax = 0;
            n = 0;
            return quietWindows >= 2;
        }

        void reset() {
            accSum = accSq = gyroMax = 0;
            n = 0;
            quietWindows = 0;
        }

        @Override public void onAccuracyChanged(Sensor s, int a) {}
    }

    public static String formatSpeed(float speedKmh, boolean metric) {
        if (!Float.isFinite(speedKmh) || speedKmh < 0) return "—";
        return String.format(Locale.US, metric ? "%.0f km/h" : "%.0f mph",
                metric ? speedKmh : speedKmh * 0.621371f);
    }

    public static String formatDistance(float meters, boolean metric) {
        return String.format(Locale.US, metric ? "%.1f km" : "%.1f mi",
                meters / 1000f * (metric ? 1 : 0.621371f));
    }

    long getRetryDelay() { return retryDelay; }
    void setRetryDelayForTesting(long delay) { this.retryDelay = delay; }
    long getNextRetry() { return nextRetry; }
    void setNextRetryForTesting(long next) { this.nextRetry = next; }
    void setAttachedForTesting(boolean attached) { this.attached = attached; }
    void setRunningForTesting(boolean running) { this.running = running; }
    void setLastCallbackForTesting(long lastCallback) { this.lastCallback = lastCallback; }
    void setLastProgressForTesting(long lastProgress) { this.lastProgress = lastProgress; }
    void triggerHeartbeatForTesting() { heartbeat.run(); }
    void setImuRegisteredForTesting(boolean registered) { this.imuRegistered = registered; }
    StationaryDetector getStationaryDetectorForTesting() { return this.stationaryDetector; }
    TripEngine getEngineForTesting() { return this.engine; }
    void handleLocationForTesting(Location loc) { handleLocation(loc); }
    boolean isRegisteredForTesting() { return this.registered; }
    void registerLocationForTesting() { registerLocation(); }
}
