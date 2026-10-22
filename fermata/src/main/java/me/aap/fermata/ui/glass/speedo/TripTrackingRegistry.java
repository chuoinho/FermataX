package me.aap.fermata.ui.glass.speedo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.utils.log.Log;

/**
 * Process-wide singleton registry for trip tracking.
 * Owns the single {@link GlassSpeedoTripController} instance and multiplexes its
 * observation stream to any active dashboard or background consumer.
 */
public final class TripTrackingRegistry {
    private static final String TAG = "TripTrackingRegistry";

    public interface Listener {
        void onTripUpdated(float speedKmh, @Nullable TripEngine.State tripState);
    }

    private final Context appContext;
    private final GlassSpeedoTripController controller;
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean serviceRunning;

    TripTrackingRegistry(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.controller = new GlassSpeedoTripController(this.appContext, this::dispatchUpdate);
    }

    @NonNull
    public GlassSpeedoTripController getController() {
        return controller;
    }

    public void addListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
        Runnable initialSnapshot = () -> {
            TripEngine.State snapshot = controller.getTripState();
            float speed = (snapshot != null) ? snapshot.speedKmh() : -1f;
            listener.onTripUpdated(speed, snapshot);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            initialSnapshot.run();
        } else {
            mainHandler.post(initialSnapshot);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void toggleTrip() {
        controller.toggleTrip();
        AutomotiveConnectionState aaState = AutomotiveConnectionState.get();
        if ((aaState.isProjectionConnected() || aaState.state() == AutomotiveConnectionState.State.APP_VISIBLE) && !isServiceRunning()) {
            TripStore store = new TripStore(appContext);
            if (!store.isUserPaused()) {
                TripTrackingService.start(appContext);
            }
        }
    }

    public void resetTrip() {
        controller.resetTrip();
    }

    public void reportSignal(@NonNull TripEngine.Signal signal) {
        controller.showUnavailable(signal);
    }

    public boolean isServiceRunning() {
        return serviceRunning;
    }

    @androidx.annotation.VisibleForTesting
    void destroyForTesting() {
        controller.destroyForTesting();
    }

    void setServiceRunning(boolean running) {
        this.serviceRunning = running;
    }

    private void dispatchUpdate(float speedKmh, @Nullable TripEngine.State state) {
        Runnable r = () -> {
            List<Listener> copy;
            synchronized (listeners) {
                copy = new ArrayList<>(listeners);
            }
            for (Listener l : copy) {
                try {
                    l.onTripUpdated(speedKmh, state);
                } catch (Throwable t) {
                    Log.e(TAG, "Error dispatching trip update", t);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }
}
