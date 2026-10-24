package me.aap.fermata.ui.glass.speedo;

/**
 * Distance accounting. Every interval between anchors is either counted once (Doppler,
 * position window or tunnel bridge) or explicitly declared missing via {@link #incomplete()}.
 */
final class Odometer {
    static final long FRESH_MS = 8_000;
    static final long INTERP_MS = 3_000;
    static final long STOP_WINDOW_MS = 5_000;
    static final long WINDOW_MAX_MS = 10_000;
    static final long MAX_BRIDGE_MS = 10 * 60_000;
    static final long MAX_GAP_ESTIMATE_MS = 120_000;
    private static final double CURVE_PRIOR = 1.05;      // uncalibrated prior, tune with real logs
    private static final double GAP_REL_SIGMA = 0.07;    // uncalibrated prior
    private static final double MAX_BRIDGE_U = 30;
    private static final double MAX_PARK_U = 100;
    private static final double MOVING_MPS = 0.3;

    enum Result { REJECTED, ACCEPTED, SIGNAL_ONLY, RECOVERING }

    private double distance, estimated, uncertaintySq, gapMeters, gapSpeed = Double.NaN;
    private double pendingDistance, observed = Double.NaN;
    private long movingMs, pendingMovingMs, gapTick, rejectedAt;
    private boolean incomplete, gap, moving;
    // last: newest anchoring fix. seg: start of the interval that has not been counted yet.
    private TripEngine.Fix last, seg, recovery;
    private double lastSpeed = Double.NaN, segSpeed = Double.NaN, rejectedSpeed = Double.NaN;
    private int recoveryCount, movingCount, stoppedCount;
    // seg was produced by a position window: we were moving even though no Doppler exists.
    private boolean segFromWindow;

    double distance() { return distance; }
    double estimated() { return estimated; }
    double uncertainty() { return Math.sqrt(uncertaintySq); }
    double gapMeters() { return gapMeters; }
    long movingMs() { return movingMs; }
    boolean incomplete() { return incomplete; }
    boolean inGap() { return gap; }
    TripEngine.Fix last() { return last; }
    /** Speed evidence produced by the last accepted fix (Doppler, window or stop), NaN if none. */
    double observedSpeed() { return observed; }

    void markIncomplete() { incomplete = true; }

    void restore(double distance, double estimated, double uncertainty, long movingMs, boolean incomplete) {
        this.distance = distance;
        this.estimated = Math.min(distance, estimated);
        this.uncertaintySq = uncertainty * uncertainty;
        this.movingMs = movingMs;
        this.incomplete = incomplete;
        clearAnchors();
    }

    void reset() {
        distance = estimated = uncertaintySq = 0;
        movingMs = 0;
        incomplete = false;
        clearAnchors();
    }

    void clearAnchors() {
        last = seg = recovery = null;
        lastSpeed = segSpeed = gapSpeed = rejectedSpeed = observed = Double.NaN;
        recoveryCount = movingCount = stoppedCount = 0;
        pendingDistance = pendingMovingMs = 0;
        moving = gap = segFromWindow = false;
        gapMeters = 0;
        gapTick = 0;
    }

    void tick(long now) {
        if (last != null && now - last.timeMs() > FRESH_MS) gap = true;
    }

    void unavailable() {
        gapSpeed = Double.NaN;
        gapTick = 0;
        if (last != null) gap = true;
        recovery = null;
        recoveryCount = 0;
    }

    /** Dead reckoning while no fix arrives; returns the speed used, or NaN when nothing changed. */
    double motion(long now, boolean stationary, double vehicleSpeedMps) {
        if (last == null || now - last.timeMs() <= 2_000) return Double.NaN;
        boolean initial = gapTick == 0;
        long dt = initial ? now - last.timeMs() : now - gapTick;
        gapTick = now;
        if (dt <= 0 || dt > (initial ? 3_500 : 1_500)) return Double.NaN;
        if (Double.isNaN(gapSpeed)) gapSpeed = Double.isFinite(lastSpeed) ? lastSpeed : 0;
        double v = Double.isFinite(vehicleSpeedMps) ? vehicleSpeedMps : stationary ? 0 : gapSpeed;
        gapSpeed = v;
        // Only a prior for the bridge; bounded so a parked car cannot inflate it forever.
        if (now - last.timeMs() <= MAX_GAP_ESTIMATE_MS) gapMeters += v * dt / 1000.0;
        gap = true;
        return v;
    }

    Result accept(TripEngine.Fix fix, double v) {
        observed = Double.NaN;
        if (!FixNormalizer.anchors(fix, v)) return Result.SIGNAL_ONLY;
        if (last == null) {
            last = seg = fix;
            lastSpeed = segSpeed = v;
            segFromWindow = false;
            observed = v;
            return Result.ACCEPTED;
        }
        if (fix.timeMs() <= last.timeMs()) return Result.REJECTED;
        long dt = fix.timeMs() - last.timeMs();
        if (last.source() != null && fix.source() != null && last.source() != fix.source()) {
            // Independent providers have different position offsets: only Doppler can span a switch.
            if (dt <= INTERP_MS && Double.isFinite(lastSpeed) && Double.isFinite(v))
                accountMotion((lastSpeed + v) * 0.5 * dt / 1000.0, dt, v);
            clearAnchors();
            last = seg = fix;
            lastSpeed = segSpeed = observed = v;
            return Result.ACCEPTED;
        }
        if (gap && dt <= FRESH_MS) clearGap();
        if (gap || dt > FRESH_MS) return recover(fix, v);
        if (!plausible(last, fix)) return Result.REJECTED;

        if (Double.isFinite(v)) {
            long span = fix.timeMs() - seg.timeMs();
            if (Double.isFinite(segSpeed) && Math.abs(v - segSpeed) > 8 * span / 1000.0 + 2) {
                // Two consistent readings against one anchor mean the anchor speed was the outlier.
                boolean confirmed = Double.isFinite(rejectedSpeed) && fix.timeMs() > rejectedAt
                        && Math.abs(v - rejectedSpeed) <= 8 * (fix.timeMs() - rejectedAt) / 1000.0 + 2;
                rejectedSpeed = v;
                rejectedAt = fix.timeMs();
                if (!confirmed) return Result.REJECTED;
                segSpeed = Double.NaN;
            }
            rejectedSpeed = Double.NaN;
            integrate(fix, v, span);
            seg = fix;
            segSpeed = v;
            segFromWindow = false;
            observed = v;
            lastSpeed = v;
        } else {
            observed = window(fix);
            lastSpeed = observed == 0 ? 0 : Double.NaN;
        }
        last = fix;
        return Result.ACCEPTED;
    }

    private void integrate(TripEngine.Fix fix, double v, long span) {
        if (span <= INTERP_MS) {
            // One-sided Doppler is still far better than dropping the interval.
            double v0 = Double.isFinite(segSpeed) ? segSpeed : v;
            accountMotion((v0 + v) * 0.5 * span / 1000.0, span, v);
        } else if (seg.positionTrusted() && fix.positionTrusted()) {
            double chord = seg.distanceTo(fix);
            commitEstimate(chord > 2 * sigma(seg, fix) ? chord : 0, span, v);
        } else if (Double.isFinite(segSpeed)) {
            commitEstimate((segSpeed + v) * 0.5 * span / 1000.0, span, v);
        } else {
            incomplete = true;
        }
    }

    private void commitEstimate(double meters, long span, double v) {
        distance += meters;
        estimated += meters;
        if (meters > 0) {
            movingMs += span;
            moving = true;
            movingCount = Math.max(movingCount, 3);
        }
        stoppedCount = v < MOVING_MPS ? stoppedCount + 1 : 0;
    }

    /** Fix with a trusted position but no Doppler: count displacement over a 3-10 s window. */
    private double window(TripEngine.Fix fix) {
        long window = fix.timeMs() - seg.timeMs();
        double meters = seg.distanceTo(fix);
        double sigma = sigma(seg, fix);
        boolean segMoving = (Double.isFinite(segSpeed) && segSpeed >= MOVING_MPS) || segFromWindow;
        // A short window at speed proves nothing about stopping; only a long one or a stopped anchor does.
        boolean still = meters <= 2 * sigma && (!segMoving || window >= STOP_WINDOW_MS)
                && (Double.isFinite(segSpeed) || seg.positionTrusted());
        if (still) {
            pendingDistance = pendingMovingMs = 0;
            movingCount = 0;
            stoppedCount++;
            if (stoppedCount >= 3) moving = false;
            if (window > WINDOW_MAX_MS) {
                seg = fix;
                segSpeed = 0;
                segFromWindow = false;
            }
            return 0;
        }
        if (window >= INTERP_MS && window <= WINDOW_MAX_MS && meters > 2 * sigma && seg.positionTrusted()) {
            double calculated = meters * 1000 / window;
            if (calculated <= FixNormalizer.MAX_MPS) {
                // Movement is now proven, so Doppler intervals still held back by hysteresis count too.
                distance += meters + pendingDistance;
                estimated += meters;
                movingMs += window + pendingMovingMs;
                pendingDistance = pendingMovingMs = 0;
                moving = true;
                movingCount = 3;
                stoppedCount = 0;
                seg = fix;
                segSpeed = Double.NaN; // a window estimate must not be integrated a second time
                segFromWindow = true;
                return calculated;
            }
        }
        if (window > WINDOW_MAX_MS) {
            if (segMoving) incomplete = true;
            seg = fix;
            segSpeed = Double.NaN;
            segFromWindow = false;
        }
        return Double.NaN;
    }

    private void accountMotion(double meters, long dt, double speed) {
        if (speed >= MOVING_MPS) {
            stoppedCount = 0;
            movingCount++;
        } else {
            movingCount = 0;
            stoppedCount++;
        }
        if (moving) {
            distance += meters;
            if (meters > 0) movingMs += dt;
            if (stoppedCount >= 3) moving = false;
        } else if (speed >= MOVING_MPS) {
            pendingDistance += meters;
            pendingMovingMs += dt;
            if (movingCount >= 3) {
                distance += pendingDistance;
                movingMs += pendingMovingMs;
                pendingDistance = pendingMovingMs = 0;
                moving = true;
            }
        } else {
            pendingDistance = pendingMovingMs = 0;
        }
    }

    private Result recover(TripEngine.Fix fix, double speed) {
        gap = true;
        if (recovery == null) {
            recovery = fix;
            recoveryCount = 1;
            return Result.RECOVERING;
        }
        long step = fix.timeMs() - recovery.timeMs();
        if (step <= 0) return Result.REJECTED;
        if (step > FRESH_MS || !plausible(recovery, fix)) {
            recovery = fix;
            recoveryCount = 1;
            return Result.RECOVERING;
        }
        // Keep the gap open until three consecutive fresh, mutually plausible fixes arrive.
        if (++recoveryCount < 3) {
            recovery = fix;
            return Result.RECOVERING;
        }
        long dt = fix.timeMs() - last.timeMs();
        double chord = last.distanceTo(fix);
        double u = Math.hypot(last.accuracy(), fix.accuracy());
        double maxBridgeU = Math.max(MAX_BRIDGE_U, 0.05 * chord);

        if (stoppedCount >= 3 && chord <= 2 * u && u <= MAX_PARK_U) {
            // Parked in place throughout the loss: no distance, jitter goes into the uncertainty.
            uncertaintySq += chord * chord;
        } else if (dt <= MAX_BRIDGE_MS && u <= maxBridgeU && chord > 2 * u && plausible(last, fix)) {
            double lo = Math.max(0, chord - u);
            double hi = Math.min(FixNormalizer.MAX_MPS * dt / 1000.0, chord * 1.5 + u);
            if (Double.isFinite(lastSpeed) && Double.isFinite(speed) && Math.max(lastSpeed, speed) > 0) {
                double vBound = 1.3 * Math.max(lastSpeed, speed) * dt / 1000.0;
                if (vBound >= chord) hi = Math.min(hi, vBound);
            }
            hi = Math.max(lo, hi);
            double est = gapMeters > 0 ? gapMeters : chord * CURVE_PRIOR;
            est = Math.min(hi, Math.max(lo, est));
            distance += est;
            estimated += est;
            double relative = GAP_REL_SIGMA * est;
            uncertaintySq += u * u + relative * relative;
        } else {
            incomplete = true;
        }
        clearGap();
        recovery = null;
        recoveryCount = 0;
        moving = false;
        movingCount = stoppedCount = 0;
        pendingDistance = pendingMovingMs = 0;
        last = seg = fix;
        lastSpeed = segSpeed = speed;
        segFromWindow = false;
        rejectedSpeed = Double.NaN;
        observed = speed;
        return Result.ACCEPTED;
    }

    private void clearGap() {
        gap = false;
        gapMeters = 0;
        gapSpeed = Double.NaN;
        gapTick = 0;
    }

    private static double sigma(TripEngine.Fix a, TripEngine.Fix b) {
        return Math.hypot(a.accuracy(), b.accuracy());
    }

    private static boolean plausible(TripEngine.Fix a, TripEngine.Fix b) {
        double maxMeters = FixNormalizer.MAX_MPS * (b.timeMs() - a.timeMs()) / 1000.0;
        double aAcc = a.accuracy() >= 80 ? Math.max(a.accuracy(), 500.0) : a.accuracy();
        double bAcc = b.accuracy() >= 80 ? Math.max(b.accuracy(), 500.0) : b.accuracy();
        return b.timeMs() > a.timeMs() && a.distanceTo(b) <= maxMeters + aAcc + bAcc;
    }
}
