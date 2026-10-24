package me.aap.fermata.ui.glass.speedo;

/**
 * Display speed and max speed. Independent from distance accounting: it may show the first
 * good Doppler after a tunnel immediately, but it never shows a held value for long.
 */
final class SpeedPresenter {
    static final long HOLD_MS = 3_000;
    static final long ESTIMATE_MS = Odometer.FRESH_MS;
    static final long MAX_PAIR_MS = 2_000;
    private static final double ZERO_KMH = 1.5;
    private static final double ALPHA = 0.5;

    private double value = Double.NaN, prevDoppler = Double.NaN;
    private long observedAt = -1, lastFixAt = -1, prevDopplerAt = -1;
    private float maxKmh;
    private boolean sourceTransition;

    float maxKmh() { return maxKmh; }

    void restoreMax(float maxKmh) { this.maxKmh = Math.max(0, maxKmh); }

    void reset() {
        clear();
        maxKmh = 0;
    }

    void clear() {
        sourceTransition = false;
        value = prevDoppler = Double.NaN;
        observedAt = lastFixAt = prevDopplerAt = -1;
    }

    /** Any non-rejected fix, with or without speed. */
    void onFix(long time) { lastFixAt = Math.max(lastFixAt, time); }

    /** Doppler is smoothed lightly; other evidence (window, stop, vehicle) is shown as is. */
    void observe(double mps, long time, boolean doppler) {
        if (!Double.isFinite(mps)) return;
        boolean fresh = Double.isFinite(value) && time - observedAt <= HOLD_MS;
        value = (fresh && ((doppler && mps > 0) || sourceTransition))
                ? ALPHA * mps + (1 - ALPHA) * value : mps;
        if (Math.abs(value - mps) < 0.1) sourceTransition = false;
        observedAt = Math.max(observedAt, time);
    }

    /** Max speed only counts a value confirmed by two consecutive Doppler readings. */
    void onDoppler(double mps, long time) {
        if (Double.isFinite(prevDoppler) && time > prevDopplerAt && time - prevDopplerAt <= MAX_PAIR_MS) {
            maxKmh = (float) Math.max(maxKmh, Math.min(mps, prevDoppler) * 3.6);
        }
        prevDoppler = mps;
        prevDopplerAt = time;
    }

    void breakDopplerChain() { prevDoppler = Double.NaN; }

    void sourceChanged() {
        sourceTransition = true;
        breakDopplerChain();
    }

    float speedKmh(long now) {
        if (!Double.isFinite(value) || observedAt < 0) return -1;
        long age = now - observedAt;
        if (age <= HOLD_MS) return display();
        // Fixes keep arriving without speed: be honest instead of extrapolating.
        if (lastFixAt > observedAt) return -1;
        return age <= ESTIMATE_MS ? display() : -1;
    }

    boolean estimated(long now) {
        if (!Double.isFinite(value) || observedAt < 0) return false;
        long age = now - observedAt;
        return age > HOLD_MS && age <= ESTIMATE_MS && lastFixAt <= observedAt;
    }

    private float display() {
        double kmh = value * 3.6;
        return kmh < ZERO_KMH ? 0f : (float) kmh;
    }
}
