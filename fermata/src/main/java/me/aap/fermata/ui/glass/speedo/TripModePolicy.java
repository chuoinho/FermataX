package me.aap.fermata.ui.glass.speedo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import me.aap.fermata.BuildConfig;

/** The location service follows location permission and app support, not the legacy trip toggle. */
public final class TripModePolicy {
    public enum Mode { OFF, ON, NEEDS_LOCATION_PERMISSION, NEEDS_BACKGROUND_PERMISSION }
    public enum Access { NOT_GRANTED, WHILE_USING, ALWAYS }

    private TripModePolicy() {}

    static Mode evaluate(boolean hasForeground, boolean hasBackground, boolean supportedInBuild) {
        if (!supportedInBuild) return Mode.OFF;
        if (!hasForeground) return Mode.NEEDS_LOCATION_PERMISSION;
        return hasBackground ? Mode.ON : Mode.NEEDS_BACKGROUND_PERMISSION;
    }

    public static Access access(boolean hasForeground, boolean hasBackground) {
        return !hasForeground ? Access.NOT_GRANTED : hasBackground ? Access.ALWAYS : Access.WHILE_USING;
    }

    public static boolean shouldOfferBackgroundPrompt(boolean hasForeground, boolean hasBackground,
                                               boolean alreadyAsked, boolean supportedInBuild) {
        return supportedInBuild && hasForeground && !hasBackground && !alreadyAsked;
    }

    @NonNull
    public static Mode current(@NonNull Context context) {
        return evaluate(hasForegroundLocation(context),
                hasBackgroundLocation(context), BuildConfig.TRIP_BACKGROUND_SUPPORTED);
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
