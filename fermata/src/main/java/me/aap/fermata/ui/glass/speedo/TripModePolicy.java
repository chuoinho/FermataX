package me.aap.fermata.ui.glass.speedo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import me.aap.fermata.BuildConfig;

/**
 * Background recording is opt-in: only with "Allow all the time" may the location service run while
 * another app covers the car screen. It feeds both the trip statistics and CarFinder's parking fix.
 */
public final class TripModePolicy {
    public enum Mode { OFF, ON, NEEDS_LOCATION_PERMISSION, NEEDS_BACKGROUND_PERMISSION }

    private TripModePolicy() {}

    static Mode evaluate(boolean userEnabled, boolean hasForeground, boolean hasBackground,
                         boolean supportedInBuild) {
        if (!supportedInBuild || !userEnabled) return Mode.OFF;
        if (!hasForeground) return Mode.NEEDS_LOCATION_PERMISSION;
        return hasBackground ? Mode.ON : Mode.NEEDS_BACKGROUND_PERMISSION;
    }

    @NonNull
    public static Mode current(@NonNull Context context) {
        return evaluate(isEnabled(context), hasForegroundLocation(context),
                hasBackgroundLocation(context), BuildConfig.TRIP_BACKGROUND_SUPPORTED);
    }

    public static boolean isEnabled(@NonNull Context context) {
        return new TripStore(context).isTripStatsEnabled();
    }

    public static void setEnabled(@NonNull Context context, boolean enabled) {
        new TripStore(context).setTripStatsEnabled(enabled);
    }

    public static boolean hasForegroundLocation(@NonNull Context context) {
        return granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static boolean hasBackgroundLocation(@NonNull Context context) {
        // Before Android 10 the foreground grant already covers background access.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
