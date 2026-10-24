package me.aap.fermata.ui.glass.speedo;

import java.util.Locale;

/**
 * Single-threaded trip computer facade. All times are milliseconds since boot, never wall time.
 * Distance lives in {@link Odometer}, display speed in {@link SpeedPresenter}, time in {@link TripClock}.
 */
public final class TripEngine {
    public static final long FRESH_MS = Odometer.FRESH_MS;
    /** A suspension this long starts a new trip on resume; the old one becomes {@link #previousTrip()}. */
    public static final long AUTO_NEW_TRIP_MS = 4 * 60 * 60_000L;
    private static final double EARTH_METERS = 6_371_000;
    private static final double MAX_ESTIMATED_SHARE = 0.10;
    /** Below this displayed speed a still IMU wins over Doppler noise. */
    static final float STILL_MAX_KMH = 5f;

    public enum Signal { WAITING, TRACKING, STALE, GPS_OFF, PERMISSION, PAUSED, ERROR, BACKGROUND_BLOCKED }

    /** What happened to a fix passed to {@link #accept}; diagnostics only. */
    public enum Outcome {
        ACCEPTED, NOT_RUNNING, INVALID_TIME, INVALID_ACCURACY, INVALID_POSITION, DUPLICATE, REJECTED,
        SIGNAL_ONLY, RECOVERING
    }

    public record Fix(long timeMs, double latitude, double longitude, double accuracy,
                      double speedMps, double speedAccuracy) {
        public boolean positionTrusted() {
            return accuracy > 0 && accuracy <= FixNormalizer.TRUSTED_ACCURACY;
        }

        double distanceTo(Fix other) {
            double p1 = Math.toRadians(latitude), p2 = Math.toRadians(other.latitude);
            double dp = p2 - p1, dl = Math.toRadians(other.longitude - longitude);
            double a = Math.pow(Math.sin(dp / 2), 2)
                    + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dl / 2), 2);
            return 2 * EARTH_METERS * Math.asin(Math.sqrt(Math.min(1, a)));
        }
    }

    /** Immutable UI/checkpoint snapshot; consumers cannot mutate the accumulator. */
    public record State(double distanceMeters, double estimatedMeters, double uncertaintyMeters,
                        double gapMeters, long totalTimeMs, long movingTimeMs, float maxSpeedKmh,
                        float speedKmh, Signal signal, boolean incomplete, boolean active,
                        boolean speedEstimated, boolean suspended) {
        public State(double distanceMeters, double estimatedMeters, double uncertaintyMeters,
                     double gapMeters, long totalTimeMs, long movingTimeMs, float maxSpeedKmh,
                     float speedKmh, Signal signal, boolean incomplete, boolean active) {
            this(distanceMeters, estimatedMeters, uncertaintyMeters, gapMeters, totalTimeMs, movingTimeMs,
                    maxSpeedKmh, speedKmh, signal, incomplete, active, false, false);
        }
        public State(double distanceMeters, double estimatedMeters, double uncertaintyMeters,
                     long totalTimeMs, long movingTimeMs, float maxSpeedKmh, float speedKmh,
                     Signal signal, boolean incomplete, boolean active) {
            this(distanceMeters, estimatedMeters, uncertaintyMeters, 0.0, totalTimeMs, movingTimeMs,
                    maxSpeedKmh, speedKmh, signal, incomplete, active);
        }
        public State(double distanceMeters, double estimatedMeters, long totalTimeMs,
                     long movingTimeMs, float maxSpeedKmh, float speedKmh, Signal signal,
                     boolean incomplete, boolean active) {
            this(distanceMeters, estimatedMeters, 0.0, 0.0, totalTimeMs, movingTimeMs,
                    maxSpeedKmh, speedKmh, signal, incomplete, active);
        }
        /** The in-gap dead-reckoning estimate is only a bridge prior and is never displayed. */
        public float getDistanceKm() { return (float) (distanceMeters / 1000); }
        public float getDistanceMiles() { return getDistanceKm() * 0.621371f; }
        public float getAverageSpeedKmh() {
            return totalTimeMs > 0 ? (float) (distanceMeters * 3600 / totalTimeMs) : 0;
        }
        public float getAverageSpeedMph() { return getAverageSpeedKmh() * 0.621371f; }
        public boolean isMostlyEstimated() {
            return distanceMeters > 0 && estimatedMeters / distanceMeters > MAX_ESTIMATED_SHARE;
        }
        public float getMovingAverageSpeedKmh() {
            // Estimated stretches cannot tell us how much of them was spent moving.
            return incomplete || isMostlyEstimated() ? -1f
                    : movingTimeMs > 0 ? (float) (distanceMeters * 3600 / movingTimeMs) : 0;
        }
        public String getFormattedDriveTime() {
            long minutes = totalTimeMs / 60_000;
            return String.format(Locale.US, "%02dh %02dm", minutes / 60, minutes % 60);
        }
        /** Explicit user pause; a system suspension is not shown as paused. */
        public boolean isPaused() { return signal == Signal.PAUSED && !suspended; }
    }

    private final TripClock clock = new TripClock();
    private final Odometer odo = new Odometer();
    private final SpeedPresenter speed = new SpeedPresenter();
    private Signal signal = Signal.PAUSED;
    private Outcome lastOutcome = Outcome.NOT_RUNNING;
    private long now, lastFixAt = -1;
    private State previous;

    public State state() {
        boolean running = clock.running();
        return new State(odo.distance(), odo.estimated(), odo.uncertainty(), odo.gapMeters(),
                clock.totalMs(), odo.movingMs(), speed.maxKmh(), running ? speed.speedKmh(now) : -1,
                signal, odo.incomplete() || odo.inGap(), running, running && speed.estimated(now),
                clock.suspended());
    }

    /** The trip that was closed automatically after a long suspension, or null. */
    public State previousTrip() { return previous; }

    public void restore(State saved) { restore(saved, 0); }

    /** Restores totals only; a trip that was running when the process died is marked incomplete. */
    public void restore(State saved, long suspendedForMs) {
        double distance = nonnegative(saved.distanceMeters);
        long total = Math.max(0, saved.totalTimeMs);
        odo.restore(distance, nonnegative(saved.estimatedMeters), nonnegative(saved.uncertaintyMeters),
                Math.min(total, Math.max(0, saved.movingTimeMs)), saved.incomplete || saved.active);
        clock.restore(total, total > 0 || distance > 0, saved.active || saved.suspended, suspendedForMs);
        speed.reset();
        speed.restoreMax((float) nonnegative(saved.maxSpeedKmh));
        signal = Signal.PAUSED;
        lastFixAt = -1;
    }

    public void start(long now) {
        if (clock.running()) return;
        if (clock.suspended() && clock.suspendedFor(now) >= AUTO_NEW_TRIP_MS
                && (odo.distance() > 0 || clock.totalMs() > 0)) {
            previous = state();
            resetTotals(now);
        }
        clock.start(now);
        this.now = Math.max(this.now, now);
        signal = Signal.WAITING;
        clearAnchors();
    }

    public void pause(long now) { stop(now, false); }

    /** System stop (car disconnected, dashboard closed): time stops and no position is bridged across. */
    public void suspend(long now) { stop(now, true); }

    private void stop(long now, boolean system) {
        tick(now);
        if (odo.inGap()) odo.markIncomplete();
        if (system) clock.suspend(now);
        else clock.pause(now);
        signal = Signal.PAUSED;
        clearAnchors();
    }

    public void reset(long now) {
        resetTotals(now);
        signal = clock.running() ? Signal.WAITING : Signal.PAUSED;
    }

    private void resetTotals(long now) {
        odo.reset();
        speed.reset();
        clock.reset(now);
        lastFixAt = -1;
    }

    private void clearAnchors() {
        odo.clearAnchors();
        speed.clear();
        lastFixAt = -1;
    }

    public void tick(long now) {
        this.now = Math.max(this.now, now);
        if (!clock.running()) return;
        clock.tick(now);
        odo.tick(now);
        Fix last = odo.last();
        long age = now - (last == null ? clock.startedAt() : last.timeMs());
        if (age > FRESH_MS && (signal == Signal.TRACKING || signal == Signal.WAITING)) signal = Signal.STALE;
    }

    /** Called approximately 1Hz during signal loss. vehicleSpeedMps is NaN if no OBD/vehicle source. */
    public void motion(long now, boolean stationary, double vehicleSpeedMps) {
        this.now = Math.max(this.now, now);
        if (!clock.running()) return;
        double v = odo.motion(now, stationary, vehicleSpeedMps);
        if (Double.isFinite(v) && (Double.isFinite(vehicleSpeedMps) || stationary)) {
            speed.observe(v, now, false);
        }
    }

    /**
     * The IMU reports the car as still while fixes keep arriving. Only a low displayed speed is
     * zeroed: at cruising speed a quiet IMU is more likely a smooth road than a stop.
     */
    public void stillAtLowSpeed(long now) {
        this.now = Math.max(this.now, now);
        if (!clock.running()) return;
        float kmh = speed.speedKmh(now);
        if (kmh >= 0 && kmh < STILL_MAX_KMH) speed.observe(0, now, false);
    }

    public void unavailable(Signal reason, long now) {
        tick(now);
        signal = reason;
        odo.unavailable();
        speed.clear();
    }

    /** Why the last {@link #accept} call used or dropped its fix. */
    public Outcome lastOutcome() { return lastOutcome; }

    public boolean accept(Fix fix, long now) {
        tick(now);
        if (!clock.running()) return outcome(Outcome.NOT_RUNNING, false);
        Outcome invalid = FixNormalizer.invalidReason(fix, now);
        if (invalid != null) return outcome(invalid, false);
        if (fix.timeMs() < clock.startedAt()) return outcome(Outcome.INVALID_TIME, false);
        if (fix.timeMs() <= lastFixAt) return outcome(Outcome.DUPLICATE, false);
        double v = FixNormalizer.trustedSpeed(fix);
        Odometer.Result result = odo.accept(fix, v);
        if (result == Odometer.Result.REJECTED) {
            speed.breakDopplerChain();
            return outcome(Outcome.REJECTED, false);
        }
        clock.firstFix(fix.timeMs(), now);
        lastFixAt = fix.timeMs();
        speed.onFix(fix.timeMs());
        if (result == Odometer.Result.SIGNAL_ONLY) return outcome(Outcome.SIGNAL_ONLY, true);
        if (result == Odometer.Result.RECOVERING) {
            // The display may trust a good Doppler at once; distance waits for the bridge.
            if (Double.isFinite(v) && fix.positionTrusted()) speed.observe(v, fix.timeMs(), true);
            return outcome(Outcome.RECOVERING, false);
        }
        lastOutcome = Outcome.ACCEPTED;
        if (Double.isFinite(v)) {
            speed.onDoppler(v, fix.timeMs());
            speed.observe(v, fix.timeMs(), true);
        } else {
            speed.breakDopplerChain();
            speed.observe(odo.observedSpeed(), fix.timeMs(), false);
        }
        signal = Signal.TRACKING;
        return true;
    }

    private boolean outcome(Outcome o, boolean result) {
        lastOutcome = o;
        return result;
    }

    private static double nonnegative(double n) { return Double.isFinite(n) ? Math.max(0, n) : 0; }
}
