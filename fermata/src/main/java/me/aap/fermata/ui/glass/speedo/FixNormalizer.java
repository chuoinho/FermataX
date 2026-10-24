package me.aap.fermata.ui.glass.speedo;

/** Pure rules that decide how much a single location fix can be trusted. */
final class FixNormalizer {
    static final double MAX_MPS = 70;
    static final double MAX_ACCURACY = 300;
    static final double TRUSTED_ACCURACY = 20;
    // Doppler is kept unless its own sigma is clearly worse than the value; uncalibrated, tune with trip logs.
    static final double MIN_SPEED_SIGMA = 3.0;
    static final double REL_SPEED_SIGMA = 0.5;

    private FixNormalizer() {}

    /** Builds a fix from raw platform values; {@code timeMs} must be elapsed realtime of the fix itself. */
    static TripEngine.Fix of(long timeMs, double latitude, double longitude, boolean hasAccuracy,
                             float accuracy, boolean hasSpeed, float speed, boolean hasSpeedAccuracy,
                             float speedAccuracy) {
        return new TripEngine.Fix(timeMs, latitude, longitude,
                hasAccuracy ? accuracy : Double.NaN, hasSpeed ? speed : Double.NaN,
                hasSpeedAccuracy ? speedAccuracy : Double.NaN);
    }

    enum SpeedQuality { OK, STOP, MISSING, OUT_OF_RANGE, LOW_CONFIDENCE }

    static boolean valid(TripEngine.Fix f, long now) {
        return invalidReason(f, now) == null;
    }

    /** Why a fix cannot be used at all, or null when it is valid. */
    static TripEngine.Outcome invalidReason(TripEngine.Fix f, long now) {
        if (f.timeMs() < 0 || f.timeMs() > now + 200 || now - f.timeMs() > Odometer.FRESH_MS) {
            return TripEngine.Outcome.INVALID_TIME;
        }
        if (!Double.isFinite(f.latitude()) || Math.abs(f.latitude()) > 90
                || !Double.isFinite(f.longitude()) || Math.abs(f.longitude()) > 180) {
            return TripEngine.Outcome.INVALID_POSITION;
        }
        if (!Double.isFinite(f.accuracy()) || f.accuracy() <= 0 || f.accuracy() > MAX_ACCURACY) {
            return TripEngine.Outcome.INVALID_ACCURACY;
        }
        return null;
    }

    /** Doppler speed in m/s, 0 for a confident stop, or NaN when the fix carries no usable speed. */
    static double trustedSpeed(TripEngine.Fix f) {
        return switch (speedQuality(f)) {
            case OK -> f.speedMps();
            case STOP -> 0;
            default -> Double.NaN;
        };
    }

    static SpeedQuality speedQuality(TripEngine.Fix f) {
        double v = f.speedMps();
        if (!Double.isFinite(v)) return SpeedQuality.MISSING;
        if (v < 0 || v > MAX_MPS) return SpeedQuality.OUT_OF_RANGE;
        if (v < 0.15) return SpeedQuality.STOP;
        double sigma = f.speedAccuracy();
        if (Double.isFinite(sigma)) {
            if (sigma < 0) return SpeedQuality.LOW_CONFIDENCE;
            if (v <= sigma && v <= 1.5) return SpeedQuality.STOP;
            if (sigma > Math.max(MIN_SPEED_SIGMA, REL_SPEED_SIGMA * v)) return SpeedQuality.LOW_CONFIDENCE;
        }
        return SpeedQuality.OK;
    }

    /**
     * A fix without Doppler and without a trusted position only proves that a signal exists.
     * Letting it become an anchor would hide a GNSS gap and prevent the tunnel bridge.
     */
    static boolean anchors(TripEngine.Fix f, double trustedSpeed) {
        return Double.isFinite(trustedSpeed) || f.positionTrusted();
    }
}
