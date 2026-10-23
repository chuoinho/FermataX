package me.aap.fermata.ui.glass.weather;

import java.util.Locale;

/**
 * Pure policy rules for weather forecast refreshing, cache countdown, and coordinate rounding.
 */
public final class WeatherLocationPolicy {
    public static final long REFRESH_MS = 60 * 60_000L;

    private WeatherLocationPolicy() {}

    public static boolean shouldFetch(long nowMs, long lastFetchMs, boolean hasData) {
        return !hasData || lastFetchMs <= 0 || (nowMs - lastFetchMs >= REFRESH_MS);
    }

    /**
     * Gate for every dashboard refresh. On Android Auto only forced calls (start and the 60 minute
     * schedule) fetch; elsewhere the 60 minute rule applies. A request in flight is never duplicated.
     */
    public static boolean shouldRefresh(boolean force, boolean busy, boolean automotive,
                                        long nowMs, long lastFetchMs, boolean hasData) {
        if (force) return true;
        return !busy && !automotive && shouldFetch(nowMs, lastFetchMs, hasData);
    }

    public static long nextFetchDelayMs(long nowMs, long lastFetchMs) {
        if (lastFetchMs <= 0) return 0L;
        return Math.max(0L, lastFetchMs + REFRESH_MS - nowMs);
    }

    public static String roundCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
