package me.aap.fermata.ui.glass.weather;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure rules deciding the single most severe road hazard from Open-Meteo samples.
 * Missing numeric values are passed as NaN and a missing weather code as -1.
 */
public final class RoadHazardPolicy {
    public static final float ICE_MIN_C = -3f;
    public static final float ICE_MAX_C = 3f;
    public static final float FOG_VISIBILITY_M = 500f;
    public static final float WIND_GUST_KMH = 60f;

    /** Ordered from least to most severe. */
    public enum Type { NONE, WIND, HEAVY_PRECIP, FOG, ICE }

    public static final class Hazard {
        public static final Hazard NONE = new Hazard(Type.NONE, Float.NaN);

        private final Type type;
        private final float gustKmh;

        Hazard(@NonNull Type type, float gustKmh) {
            this.type = type;
            this.gustKmh = gustKmh;
        }

        @NonNull
        public Type getType() {
            return type;
        }

        /** Gust speed that raised a WIND hazard, NaN otherwise. */
        public float getGustKmh() {
            return gustKmh;
        }
    }

    private RoadHazardPolicy() {}

    @NonNull
    public static Hazard of(float tempC, int code, float gustKmh, float visibilityM) {
        if (!Float.isNaN(tempC) && tempC >= ICE_MIN_C && tempC <= ICE_MAX_C && isWetCode(code)) {
            return new Hazard(Type.ICE, Float.NaN);
        }
        if (code == 45 || code == 48 || (!Float.isNaN(visibilityM) && visibilityM < FOG_VISIBILITY_M)) {
            return new Hazard(Type.FOG, Float.NaN);
        }
        if (isHeavyCode(code)) return new Hazard(Type.HEAVY_PRECIP, Float.NaN);
        if (!Float.isNaN(gustKmh) && gustKmh >= WIND_GUST_KMH) return new Hazard(Type.WIND, gustKmh);
        return Hazard.NONE;
    }

    @NonNull
    public static Hazard worst(@NonNull Hazard a, @NonNull Hazard b) {
        int cmp = a.type.compareTo(b.type);
        if (cmp != 0) return cmp > 0 ? a : b;
        return (a.type == Type.WIND && b.gustKmh > a.gustKmh) ? b : a;
    }

    // Drizzle, rain, freezing rain, snow and showers.
    static boolean isWetCode(int code) {
        return (code >= 51 && code <= 57) || (code >= 61 && code <= 67)
                || (code >= 71 && code <= 77) || (code >= 80 && code <= 86);
    }

    static boolean isHeavyCode(int code) {
        return code == 65 || code == 67 || code == 75 || code == 82 || code == 86
                || (code >= 95 && code <= 99);
    }

    /** Open-Meteo reports the unit it used, e.g. "°F". */
    public static float toCelsius(float value, @Nullable String unit) {
        return (unit != null && unit.contains("F")) ? (value - 32f) * 5f / 9f : value;
    }

    /** Open-Meteo reports "mp/h", "km/h", "m/s" or "kn". */
    public static float toKmh(float value, @Nullable String unit) {
        if (unit == null) return value;
        if (unit.startsWith("mp")) return value * 1.609344f;
        if (unit.equals("m/s")) return value * 3.6f;
        if (unit.startsWith("kn")) return value * 1.852f;
        return value;
    }

    public static float toMeters(float value, @Nullable String unit) {
        return (unit != null && unit.contains("ft")) ? value * 0.3048f : value;
    }
}
