package me.aap.fermata.ui.activity;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.service.FermataMediaService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 35, application = FermataApplication.class)
public class ActivityListenerLifecycleTest {
    @Test public void destroyingPhoneActivityDoesNotReportListenerLeaks() {
        checkLifecycle(false);
    }

    @Test public void forgottenActivityListenerIsStillDetected() {
        checkLifecycle(true);
    }

    private void checkLifecycle(boolean forgetListener) {
        FermataApplication app = (FermataApplication) RuntimeEnvironment.getApplication();
        app.getPreferenceStore().applyBooleanPref(MainActivityPrefs.INITIAL_SETUP_COMPLETED, true);
        ServiceController<FermataMediaService> service = Robolectric.buildService(FermataMediaService.class).create();
        Intent intent = new Intent(app, FermataMediaService.class).setAction(FermataMediaService.ACTION_MEDIA_SERVICE);
        IBinder binder = service.get().onBind(intent);
        assertNotNull(binder);
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app, FermataMediaService.class), binder);
        var activity = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        me.aap.utils.pref.PreferenceStore.Listener forgotten = (store, prefs) -> activity.get().isFinishing();
        try {
            ShadowLooper.idleMainLooper();
            assertTrue("delegate initialized", activity.get().getActivityDelegate().isDone());
            assertNotNull(activity.get().getActivityDelegate().peek());
            MainActivityDelegate delegate = activity.get().getActivityDelegate().peek();
            java.util.concurrent.atomic.AtomicReference<me.aap.utils.pref.PreferenceStore.Listener> effects =
                    new java.util.concurrent.atomic.AtomicReference<>();
            me.aap.utils.event.ListenerLeakDetector.hasLeaks((b, l) -> {
                if (b == app.getPreferenceStore() && l instanceof me.aap.fermata.media.audio.AudioEffectsController)
                    effects.set((me.aap.utils.pref.PreferenceStore.Listener) l);
                return false;
            });
            assertNotNull("service effects listener registered", effects.get());
            if (forgetListener) app.getPreferenceStore().addBroadcastListener(forgotten);
            ShadowLog.clear();
            activity.pause().stop().destroy();
            ShadowLooper.idleMainLooper();
            StringBuilder logs = new StringBuilder();
            boolean leaked = false;
            for (var log : ShadowLog.getLogs()) {
                if (log.msg.contains("has not been unregistered")) logs.append(log.msg).append('\n');
                if (log.throwable != null && "Listener leaks detected!".equals(log.throwable.getMessage())) leaked = true;
            }
            // Verify the production predicate even when earlier JVM tests selected another log sink.
            boolean detected = me.aap.utils.event.ListenerLeakDetector.hasLeaks(delegate::isListenerLeak);
            if (forgetListener) assertTrue("forgotten activity listener must remain visible", detected);
            else {
                assertFalse(logs.toString(), leaked);
                assertFalse("no listeners beyond the active service", detected);
                ((FermataMediaService.ServiceBinder) binder).getMediaSessionCallback().close();
                app.getPreferenceStore().addBroadcastListener(effects.get());
                try {
                    assertTrue("closed service listener must not be exempt",
                            delegate.isListenerLeak(app.getPreferenceStore(), effects.get()));
                } finally {
                    app.getPreferenceStore().removeBroadcastListener(effects.get());
                }
            }
        } finally {
            if (forgetListener) app.getPreferenceStore().removeBroadcastListener(forgotten);
            MainActivity.finishForAutoDisconnect();
            service.destroy();
            app.getPreferenceStore().removeBroadcastListener(app.getAddonManager());
            me.aap.fermata.ui.glass.speedo.TripTrackingAutoBinder.resetForTests();
            me.aap.fermata.ui.glass.speedo.TripTrackingService.resetForTests();
            me.aap.fermata.location.LocationHub.resetForTesting();
            me.aap.fermata.diagnostics.android.DiagnosticsRuntimeTestAccess.reset();
        }
    }
}
