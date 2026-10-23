package me.aap.fermata.ui.glass.speedo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Only totals are persisted; old coordinates must never bridge across a process restart, and
 * time while the process was dead is never added to the trip.
 */
final class TripStore {
    private final SharedPreferences prefs;

    TripStore(Context context) {
        prefs = context.getSharedPreferences("speedo_trip_v2", Context.MODE_PRIVATE);
    }

    /** A trip that was running or suspended comes back suspended; the engine decides on resume. */
    TripEngine.State load() {
        return read("");
    }

    /** Wall-clock time since the trip stopped being observed; used only for the auto new trip rule. */
    long suspendedForMs() {
        boolean active = prefs.getBoolean("active", false);
        boolean suspended = prefs.getBoolean("suspended", false);
        if (!active && !suspended) return 0;
        long since = prefs.getLong(suspended ? "suspended_at_wall" : "saved_at_wall", 0);
        long now = System.currentTimeMillis();
        return (since > 0 && now > since) ? now - since : 0;
    }

    TripEngine.State loadPrevious() {
        return prefs.contains("prev_total_ms") ? read("prev_") : null;
    }

    boolean isUserPaused() { return prefs.getBoolean("user_paused", false); }
    void setUserPaused(boolean paused) { prefs.edit().putBoolean("user_paused", paused).apply(); }

    boolean isTripStatsEnabled() { return prefs.getBoolean("trip_stats_enabled", false); }
    void setTripStatsEnabled(boolean enabled) { prefs.edit().putBoolean("trip_stats_enabled", enabled).apply(); }

    boolean isAllowMock() { return prefs.getBoolean("allow_mock", false); }
    void setAllowMock(boolean allow) { prefs.edit().putBoolean("allow_mock", allow).apply(); }

    boolean save(TripEngine.State state, TripEngine.State previous) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = prefs.edit();
        write(e, "", state);
        if (state.suspended()) {
            if (!prefs.getBoolean("suspended", false)) e.putLong("suspended_at_wall", now);
        } else {
            e.remove("suspended_at_wall");
        }
        e.putBoolean("suspended", state.suspended()).putLong("saved_at_wall", now);
        if (previous != null) write(e, "prev_", previous);
        // Called on the tracking worker; commit orders reset/pause with periodic checkpoints.
        return e.commit();
    }

    private TripEngine.State read(String p) {
        boolean active = prefs.getBoolean(p + "active", false);
        boolean suspended = prefs.getBoolean(p + "suspended", false);
        return new TripEngine.State(
                Double.longBitsToDouble(prefs.getLong(p + "distance", 0)),
                Double.longBitsToDouble(prefs.getLong(p + "estimated", 0)),
                Double.longBitsToDouble(prefs.getLong(p + "uncertainty", 0)), 0.0,
                prefs.getLong(p + "total_ms", 0), prefs.getLong(p + "moving_ms", 0),
                prefs.getFloat(p + "max", 0), -1, TripEngine.Signal.PAUSED,
                prefs.getBoolean(p + "incomplete", false), active, false, suspended || active);
    }

    private static void write(SharedPreferences.Editor e, String p, TripEngine.State s) {
        e.putLong(p + "distance", Double.doubleToLongBits(s.distanceMeters()))
                .putLong(p + "estimated", Double.doubleToLongBits(s.estimatedMeters()))
                .putLong(p + "uncertainty", Double.doubleToLongBits(s.uncertaintyMeters()))
                .putLong(p + "total_ms", s.totalTimeMs()).putLong(p + "moving_ms", s.movingTimeMs())
                .putFloat(p + "max", s.maxSpeedKmh()).putBoolean(p + "incomplete", s.incomplete())
                .putBoolean(p + "active", s.active());
    }
}
