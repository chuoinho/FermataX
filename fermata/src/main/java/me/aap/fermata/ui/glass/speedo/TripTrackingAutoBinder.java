package me.aap.fermata.ui.glass.speedo;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.diagnostics.android.AndroidDiagnosticsRuntime;
import me.aap.utils.log.Log;

/**
 * Automates starting and stopping {@link TripTrackingService} along automotive connection state changes.
 */
public final class TripTrackingAutoBinder implements AutomotiveConnectionState.Listener {
    private static final String TAG = "TripTrackingAutoBinder";
    private static volatile TripTrackingAutoBinder instance;
    static final BiConsumer<String, Map<String, Object>> DEFAULT_DIAGNOSTICS =
            (name, a) -> AndroidDiagnosticsRuntime.get().recordDetailed("auto", name, a);
    @VisibleForTesting static volatile BiConsumer<String, Map<String, Object>> diagnostics = DEFAULT_DIAGNOSTICS;

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
        ActivityManager.RunningAppProcessInfo process = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(process);
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("state", state.name());
        a.put("importance", process.importance);
        a.put("epoch", AutomotiveConnectionState.get().connectionEpoch());
        diagnostics.accept("auto_state_changed", a);
        // With background location the service may start as soon as the car connects.
        if (state == AutomotiveConnectionState.State.CONNECTED || state == AutomotiveConnectionState.State.APP_VISIBLE) {
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
