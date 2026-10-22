package me.aap.fermata.ui.glass.speedo;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.utils.log.Log;

/**
 * Automates starting and stopping {@link TripTrackingService} along automotive connection state changes.
 */
public final class TripTrackingAutoBinder implements AutomotiveConnectionState.Listener {
    private static final String TAG = "TripTrackingAutoBinder";
    private static volatile TripTrackingAutoBinder instance;

    public interface ServiceStarter {
        void startService(Context context);
        void stopService(Context context);
        default boolean isServiceRunning() {
            return TripTrackingService.registry().isServiceRunning();
        }
    }

    private final Context context;
    private final ServiceStarter starter;
    private final TripStore store;

    public TripTrackingAutoBinder(@NonNull Context context) {
        this(context, new ServiceStarter() {
            @Override
            public void startService(Context ctx) {
                TripTrackingService.start(ctx);
            }

            @Override
            public void stopService(Context ctx) {
                TripTrackingService.stop(ctx);
            }
        }, new TripStore(context));
    }

    @VisibleForTesting
    public TripTrackingAutoBinder(@NonNull Context context, @NonNull ServiceStarter starter, @NonNull TripStore store) {
        this.context = context.getApplicationContext();
        this.starter = starter;
        this.store = store;
    }

    public static synchronized void install(@NonNull Context context) {
        if (instance == null) {
            instance = new TripTrackingAutoBinder(context);
            AutomotiveConnectionState.get().addListener(instance);
        }
    }

    public static synchronized void resetForTests() {
        if (instance != null) {
            AutomotiveConnectionState.get().removeListener(instance);
            instance = null;
        }
    }

    @Override
    public void onStateChanged(AutomotiveConnectionState.State state) {
        Log.d(TAG, "Automotive connection state: " + state);
        if (state == AutomotiveConnectionState.State.APP_VISIBLE) {
            if (!store.isUserPaused() && !starter.isServiceRunning()) {
                starter.startService(context);
            }
        } else if (state == AutomotiveConnectionState.State.DISCONNECTED) {
            if (starter.isServiceRunning()) {
                starter.stopService(context);
            }
        }
    }
}
