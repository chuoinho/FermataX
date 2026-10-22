package me.aap.fermata.ui.glass.speedo;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.utils.log.Log;

/**
 * Dedicated location foreground service for trip recording and speedometer telemetry.
 * Operates during vehicular / Android Auto sessions independently from media playback.
 */
public class TripTrackingService extends Service {
    private static final String TAG = "FermataTripService";

    public static final String ACTION_START = "me.aap.fermata.action.TRIP_START";
    public static final String ACTION_STOP = "me.aap.fermata.action.TRIP_STOP";
    public static final String ACTION_TOGGLE = "me.aap.fermata.action.TRIP_TOGGLE";

    public static final String NOTIF_CHANNEL_ID = "trip_tracking";
    public static final int NOTIF_ID = 2;

    private static volatile TripTrackingRegistry registryInstance;
    private static final Object REGISTRY_LOCK = new Object();

    private TripTrackingRegistry.Listener tripListener;
    private long lastNotificationUpdate;
    private boolean isForeground, holdsController;

    public static void init(@NonNull Context context) {
        if (registryInstance == null) {
            synchronized (REGISTRY_LOCK) {
                if (registryInstance == null) {
                    registryInstance = new TripTrackingRegistry(context.getApplicationContext());
                }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    public static void resetForTests() {
        synchronized (REGISTRY_LOCK) {
            if (registryInstance != null) {
                registryInstance.destroyForTesting();
                registryInstance = null;
            }
        }
    }

    @NonNull
    public static TripTrackingRegistry registry() {
        TripTrackingRegistry reg = registryInstance;
        if (reg == null) {
            synchronized (REGISTRY_LOCK) {
                reg = registryInstance;
                if (reg == null) {
                    Context ctx = FermataApplication.get();
                    if (ctx == null) {
                        throw new IllegalStateException("Context not available for TripTrackingRegistry. Call TripTrackingService.init(context) in tests.");
                    }
                    reg = new TripTrackingRegistry(ctx);
                    registryInstance = reg;
                }
            }
        }
        return reg;
    }

    public static void start(@NonNull Context context) {
        init(context);
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Cannot start TripTrackingService: location permission not granted");
            registry().reportSignal(TripEngine.Signal.PERMISSION);
            return;
        }
        try {
            Intent intent = new Intent(context, TripTrackingService.class).setAction(ACTION_START);
            ContextCompat.startForegroundService(context, intent);
        } catch (SecurityException | IllegalStateException e) {
            Log.w(TAG, "Failed to start TripTrackingService", e);
            reportBlocked();
        }
    }

    /**
     * Android may refuse a location FGS while only Android Auto shows the app. A projected app is
     * still bound by the car host with foreground location capability, so a dashboard that is
     * already recording must not be told to "tap to enable".
     */
    private static void reportBlocked() {
        if (!registry().getController().isTracking()) {
            registry().reportSignal(TripEngine.Signal.BACKGROUND_BLOCKED);
        }
    }

    public static void stop(@NonNull Context context) {
        try {
            Intent intent = new Intent(context, TripTrackingService.class).setAction(ACTION_STOP);
            context.stopService(intent);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to stop TripTrackingService", t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registry().setServiceRunning(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TOGGLE.equals(action)) {
            registry().toggleTrip();
            updateNotification(registry().getController().getTripState());
            return START_NOT_STICKY;
        }

        if (!hasLocationPermission(this)) {
            Log.w(TAG, "Location permission missing; stopping TripTrackingService");
            registry().reportSignal(TripEngine.Signal.PERMISSION);
            if (!isForeground) {
                try {
                    Notification notif = buildNotification(registry().getController().getTripState());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                    } else {
                        ServiceCompat.startForeground(this, NOTIF_ID, notif, 0);
                    }
                    isForeground = true;
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to promote foreground before stopping", t);
                }
            }
            stopForeground(true);
            isForeground = false;
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isForeground) {
            Notification notif = buildNotification(registry().getController().getTripState());
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(this, NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                } else {
                    ServiceCompat.startForeground(this, NOTIF_ID, notif, 0);
                }
                isForeground = true;
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "ForegroundService start rejected", e);
                reportBlocked();
                stopSelf();
                return START_NOT_STICKY;
            }

            registry().getController().start();
            holdsController = true;

            if (tripListener == null) {
                tripListener = (speedKmh, state) -> {
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastNotificationUpdate >= 10_000L) {
                        updateNotification(state);
                    }
                };
                registry().addListener(tripListener);
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        registry().setServiceRunning(false);
        if (tripListener != null) {
            registry().removeListener(tripListener);
            tripListener = null;
        }
        if (holdsController) {
            holdsController = false;
            registry().getController().stop();
        }
        stopForeground(true);
        isForeground = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean hasLocationPermission(@NonNull Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIF_CHANNEL_ID,
                        getString(R.string.speedo_recording_title),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.speedo_recording_text));
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(@Nullable TripEngine.State state) {
        createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(getString(R.string.speedo_recording_title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        String text;
        if (state != null && state.totalTimeMs() > 0) {
            boolean metric = true;
            try {
                int unit = me.aap.fermata.ui.activity.MainActivityPrefs.get().getIntPref(
                        me.aap.fermata.ui.activity.MainActivityPrefs.GLASS_SPEED_UNIT);
                metric = (unit == 0);
            } catch (Throwable ignored) {}
            String distStr = GlassSpeedoTripController.formatDistance((float) (state.distanceMeters() + state.gapMeters()), metric);
            String timeStr = state.getFormattedDriveTime();
            text = distStr + " • " + timeStr;
        } else {
            text = getString(R.string.speedo_recording_text);
        }
        builder.setContentText(text);

        boolean isPaused = (state == null || state.signal() == TripEngine.Signal.PAUSED || !state.active());
        int actionIcon = R.drawable.notification;
        int actionTitle = isPaused ? R.string.speedo_resume_trip : R.string.speedo_pause_trip;
        Intent toggleIntent = new Intent(this, TripTrackingService.class).setAction(ACTION_TOGGLE);
        PendingIntent togglePi = PendingIntent.getService(this, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(actionIcon, getString(actionTitle), togglePi);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        return builder.build();
    }

    @SuppressLint("MissingPermission")
    private void updateNotification(@Nullable TripEngine.State state) {
        if (!isForeground) return;
        lastNotificationUpdate = SystemClock.elapsedRealtime();
        Notification notif = buildNotification(state);
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notif);
        } catch (SecurityException e) {
            Log.w(TAG, "Failed to update notification due to missing permission", e);
        }
    }
}
