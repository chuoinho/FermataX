package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.content.ContextWrapper;
import android.content.ComponentName;
import android.content.Intent;
import android.os.HandlerThread;
import android.location.LocationManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.location.LocationHub;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class GlassSpeedoTripControllerTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("speedo_trip_v2", Context.MODE_PRIVATE).edit().clear().commit();
        TripTrackingService.resetForTests();
        LocationHub.resetForTesting();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("GlassSpeedo") && t instanceof HandlerThread ht) {
                ht.quitSafely();
                try { ht.join(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    @org.junit.After public void tearDown() {
        TripTrackingService.resetForTests();
        LocationHub.resetForTesting();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("GlassSpeedo") && t instanceof HandlerThread ht) {
                ht.quitSafely();
                try { ht.join(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    @Test public void metricImperialAndUnknownFormatting() {
        assertEquals("100 km/h", GlassSpeedoTripController.formatSpeed(100, true));
        assertEquals("62 mph", GlassSpeedoTripController.formatSpeed(100, false));
        assertEquals("—", GlassSpeedoTripController.formatSpeed(-1, true));
        assertEquals("—", GlassSpeedoTripController.formatSpeed(Float.NaN, true));
        assertEquals("10.0 km", GlassSpeedoTripController.formatDistance(10_000, true));
        assertEquals("6.2 mi", GlassSpeedoTripController.formatDistance(10_000, false));
    }

    @Test public void missingPrecisePermissionReportsSignalPermission() {
        Shadows.shadowOf((Application) context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        AtomicReference<TripEngine.State> reported = new AtomicReference<>();
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> reported.set(s));
        c.start();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertNotNull(reported.get());
        assertEquals(TripEngine.Signal.PERMISSION, reported.get().signal());
        assertEquals(-1, reported.get().speedKmh(), 0);
        c.stop();
    }

    @Test public void gpsDisabledReportsSignalGpsOff() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(false);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        AtomicReference<TripEngine.State> reported = new AtomicReference<>();
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> reported.set(s));
        c.start();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertNotNull(reported.get());
        assertEquals(TripEngine.Signal.GPS_OFF, reported.get().signal());
        c.stop();
    }

    @Test public void userPauseSurvivesDashboardRecreation() {
        new TripStore(context).setUserPaused(true);
        AtomicReference<TripEngine.State> reported = new AtomicReference<>();
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> reported.set(s));
        c.start();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertNotNull(reported.get());
        assertEquals(TripEngine.Signal.PAUSED, reported.get().signal());
        c.stop();
    }

    @Test public void checkpointRetainsEstimatesWithoutCountingOfflineTime() {
        TripStore store = new TripStore(context);
        TripEngine.State saved = new TripEngine.State(1_000.25, 200.5, 25.0, 60_000, 40_000,
                70, 20, TripEngine.Signal.TRACKING, false, true);
        assertTrue(store.save(saved, null));
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(5));
        TripEngine.State restored = store.load();
        assertEquals(1_000.25, restored.distanceMeters(), 0);
        assertEquals(200.5, restored.estimatedMeters(), 0);
        assertEquals(25.0, restored.uncertaintyMeters(), 0);
        assertEquals("time while the process was dead is never added", 60_000, restored.totalTimeMs());
        assertTrue(restored.suspended());
        assertEquals(-1, restored.speedKmh(), 0);
        TripEngine engine = new TripEngine();
        engine.restore(restored, store.suspendedForMs());
        assertTrue("a trip that was running when the process died is incomplete", engine.state().incomplete());
        assertEquals(60_000, engine.state().totalTimeMs());
    }

    @Test public void suspendedTripAndPreviousTripRoundTrip() {
        TripStore store = new TripStore(context);
        TripEngine.State suspended = new TripEngine.State(500, 0, 0, 0, 30_000, 20_000, 50, -1,
                TripEngine.Signal.PAUSED, false, false, false, true);
        TripEngine.State previous = new TripEngine.State(9_000, 0, 600_000, 600_000, 80, -1,
                TripEngine.Signal.PAUSED, false, false);
        assertTrue(store.save(suspended, previous));
        assertTrue(store.load().suspended());
        assertFalse(store.load().incomplete());
        assertEquals(9_000, store.loadPrevious().distanceMeters(), 0);
        assertTrue(store.suspendedForMs() >= 0);
    }

    @Test public void rebootDoesNotSubtractIncompatibleMonotonicClocks() {
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 10);
        new TripStore(context).save(new TripEngine.State(100, 0, 10_000, 8_000, 40,
                10, TripEngine.Signal.TRACKING, false, true), null);
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 11);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(30));
        assertEquals(10_000, new TripStore(context).load().totalTimeMs());
    }

    @Test public void viewExplainsEstimatesAndNeverShowsStaleSpeedAsZero() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        TripEngine.State state = new TripEngine.State(12_000, 800, 720_000, 600_000,
                70, -1, TripEngine.Signal.STALE, true, true);
        view.updateSpeed(-1, state, true);
        String text = text(view);
        assertTrue(text.contains("—"));
        assertTrue(text.contains("12.0 km"));
        assertFalse(text.contains("≈"));
        assertFalse(text.contains("Missing data"));
        assertFalse(text.contains("estimated"));
        assertTrue(text.contains("GPS signal lost"));
        view.updateSpeed(65, state, false);
        assertTrue(text(view).contains("7.5 mi"));
        assertFalse(text(view).contains("≈"));
        view.refreshAppearance();
    }

    @Test public void viewDisplaysCleanSpeedAndDistanceDuringGapMotion() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        TripEngine.State gapState = new TripEngine.State(12_000, 0, 10.0, 500.0, 720_000, 600_000,
                80, 60, TripEngine.Signal.STALE, false, true);
        view.updateSpeed(60, gapState, true);
        String text = text(view);
        assertTrue("Speed value should display 60 without approximation symbol", text.contains("60"));
        assertFalse("Speed value should NOT have approximation prefix", text.contains("≈"));
        // The in-gap dead-reckoning estimate is a bridge prior only and is not displayed.
        assertTrue("Distance should display 12.0 km without approximation symbol", text.contains("12.0 km"));
    }

    @Test public void onHostResumedResetsBackoffDelay() {
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, null);
        c.setAttachedForTesting(true);
        c.setRetryDelayForTesting(60_000);
        c.setNextRetryForTesting(123_456);

        c.onHostResumed();

        assertEquals(5_000, c.getRetryDelay());
        assertEquals(0, c.getNextRetry());
    }

    @Test public void heartbeatResetsBackoffAfterDropout() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);

        GlassSpeedoTripController c = new GlassSpeedoTripController(context, null);
        c.setRunningForTesting(true);
        c.setRetryDelayForTesting(30_000);
        long now = SystemClock.elapsedRealtime();
        c.setLastCallbackForTesting(now - 10_000); // 10s (> 8s)
        c.setNextRetryForTesting(now + 20_000); // in future

        c.triggerHeartbeatForTesting();

        assertEquals(5_000, c.getRetryDelay());
        assertEquals(now + 5_000, c.getNextRetry());
    }

    @Test public void tripStatsRowLongClickTriggersResetTrip() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        java.util.concurrent.atomic.AtomicBoolean resetCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        view.setOnResetTripListener(() -> resetCalled.set(true));

        assertTrue(view.performTripStatsLongClick());
        android.app.AlertDialog dialog = (android.app.AlertDialog) org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("Confirmation dialog should be displayed", dialog);
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue("Reset listener should be called after confirmation", resetCalled.get());
    }

    @Test public void toggleTripButtonTriggersListener() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        java.util.concurrent.atomic.AtomicBoolean toggleCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        view.setOnToggleTripListener(() -> toggleCalled.set(true));

        assertTrue(view.performToggleTrip());
        assertTrue("Toggle listener should be called when toggle button clicked", toggleCalled.get());
    }

    @Test public void backgroundBlockedSignalEnablesTapToEnable() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        TripEngine.State blockedState = new TripEngine.State(0, 0, 0, 0, 0, -1,
                TripEngine.Signal.BACKGROUND_BLOCKED, false, true);
        view.updateSpeed(-1, blockedState, true);
        String text = text(view);
        assertTrue("Should display tap to enable GPS", text.contains("Tap to enable GPS"));
    }

    @Test
    public void noThreadLeakOnMultipleStartsAndStops() throws Exception {
        TripTrackingService.resetForTests();
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, null);
        for (int i = 0; i < 10; i++) {
            c.start();
            c.stop();
        }
        Thread.sleep(100);
        long threadCount = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("GlassSpeedo") && t.isAlive())
                .count();
        assertTrue("Expected <= 1 GlassSpeedo thread, found: " + threadCount, threadCount <= 1);
        c.destroyForTesting();
    }

    @Test
    public void imuQuietAcrossHeartbeatsTriggersStationaryAndPreventsGapDistanceGrowth() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);

        GlassSpeedoTripController c = new GlassSpeedoTripController(context, null);
        c.setRunningForTesting(true);
        c.setAttachedForTesting(true);
        c.setImuRegisteredForTesting(true);

        long t0 = 100_000L;
        c.getEngineForTesting().start(t0);

        // Feed an initial fix with speed 20 m/s
        Location loc = new Location("gps");
        loc.setLatitude(21.0);
        loc.setLongitude(105.0);
        loc.setAccuracy(5.0f);
        loc.setSpeed(20.0f);
        loc.setElapsedRealtimeNanos(t0 * 1_000_000L);
        c.handleLocationForTesting(loc);

        // Now vehicle stops in tunnel (no GPS fixes). Feed 3 quiet IMU windows
        for (int i = 0; i < 3; i++) {
            for (int s = 0; s < 10; s++) {
                c.getStationaryDetectorForTesting().onAcceleration(0.0, 0.0, 0.0);
            }
            c.triggerHeartbeatForTesting();
        }

        TripEngine.State state = c.getEngineForTesting().state();
        assertNotNull(state);
        // Because vehicle is stationary, gapMeters must NOT grow according to last speed
        assertEquals(0.0, state.gapMeters(), 0.01);
    }

    @Test
    public void staleSeedThirtySecondsOldNotUsedAsAnchorAndFirstRealFixAcceptedImmediately() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);

        long now = SystemClock.elapsedRealtime();

        // 30-second-old seed location (e.g. 500m away)
        Location staleSeed = new Location("gps");
        staleSeed.setLatitude(21.005);
        staleSeed.setLongitude(105.005);
        staleSeed.setAccuracy(10.0f);
        staleSeed.setTime(System.currentTimeMillis() - 30_000L);
        staleSeed.setElapsedRealtimeNanos((now - 30_000L) * 1_000_000L);
        Shadows.shadowOf(lm).setLastKnownLocation(LocationManager.GPS_PROVIDER, staleSeed);

        GlassSpeedoTripController c = new GlassSpeedoTripController(context, null);
        c.startTrip();

        // Feed first real fresh fix
        Location freshFix = new Location("gps");
        freshFix.setLatitude(21.000);
        freshFix.setLongitude(105.000);
        freshFix.setAccuracy(5.0f);
        freshFix.setSpeed(10.0f);
        freshFix.setElapsedRealtimeNanos(now * 1_000_000L);
        c.handleLocationForTesting(freshFix);

        TripEngine.State state = c.getTripState();
        assertNotNull(state);
        // Stale seed must NOT cause distance corruption or anchor rejection
        assertEquals(0.0, state.distanceMeters(), 0.1);
    }

    @Test
    public void singleProviderRegistrationWithoutNetworkFallback() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);

        GlassSpeedoTripController c = new GlassSpeedoTripController(context, null);
        c.setRunningForTesting(true);
        c.setAttachedForTesting(true);
        c.registerLocationForTesting();
        assertEquals(1, Shadows.shadowOf(lm).getRequestLocationUpdateListeners().size());

        // 12 s of GNSS starvation (tunnel): no NETWORK fallback and no churn of the GNSS request.
        long now = SystemClock.elapsedRealtime();
        c.setLastCallbackForTesting(now - 12_000L);
        c.setNextRetryForTesting(0);
        c.triggerHeartbeatForTesting();
        assertEquals(1, Shadows.shadowOf(lm).getRequestLocationUpdateListeners().size());
        assertTrue(c.isRegisteredForTesting());
        c.destroyForTesting();
    }

    @Test
    public void controllerKeepsRecordingWhileAnyHolderRemains() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> {});
        android.os.Looper looper = c.getThreadForTesting().getLooper();
        c.start(); // dashboard
        c.start(); // service
        Shadows.shadowOf(looper).idle();
        c.stop();  // service refused or destroyed: the visible dashboard keeps recording
        Shadows.shadowOf(looper).idle();
        assertTrue(c.isTracking());
        assertFalse(Shadows.shadowOf(lm).getRequestLocationUpdateListeners().isEmpty());
        c.stop();
        Shadows.shadowOf(looper).idle();
        assertFalse(c.isTracking());
        c.destroyForTesting();
    }

    @Test
    public void stopSuspendsTheTripClock() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> {});
        c.start();
        android.os.Looper looper = c.getThreadForTesting().getLooper();
        Shadows.shadowOf(looper).idle();
        c.stop();
        Shadows.shadowOf(looper).idle();
        assertTrue(c.getEngineForTesting().state().suspended());
        assertFalse(c.getEngineForTesting().state().isPaused());
        c.destroyForTesting();
    }

    @Test public void viewNeverRendersUnknownSpeedAsZeroWhileTracking() {
        GlassSpeedoTripView view = new GlassSpeedoTripView(context);
        TripEngine.State tracking = new TripEngine.State(1_000, 0, 60_000, 50_000, 50, -1,
                TripEngine.Signal.TRACKING, false, true);
        view.updateSpeed(-1, tracking, true);
        String text = text(view);
        assertTrue(text.contains("—"));
        assertFalse(text.startsWith("0\n") || text.contains("\n0\n"));
        TripEngine.State estimated = new TripEngine.State(1_000, 0, 0, 0, 60_000, 50_000, 50, 54,
                TripEngine.Signal.STALE, false, true, true, false);
        view.updateSpeed(54, estimated, true);
        assertTrue(text(view).contains("~54"));
        TripEngine.State mostlyEstimated = new TripEngine.State(10_000, 2_000, 600_000, 500_000, 50, 40,
                TripEngine.Signal.TRACKING, false, true);
        view.updateSpeed(40, mostlyEstimated, true);
        assertTrue(text(view).contains("≈10.0 km"));
    }

    @Test
    public void rapidStartPauseStopDoesNotThrowNpe() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        TripTrackingService.resetForTests();
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> {});
        c.start();
        android.os.Looper looper = c.getThreadForTesting().getLooper();
        Shadows.shadowOf(looper).pause();
        c.pauseTrip();
        c.stop();
        Shadows.shadowOf(looper).unPause();
        Shadows.shadowOf(looper).idle();
        c.destroyForTesting();
    }

    @Test
    public void rapidStartStopStartMaintainsRunningAndSingleThread() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);

        TripTrackingService.resetForTests();
        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> {});
        c.start();
        c.stop();
        c.start();

        // Idle loopers
        if (c.getThreadForTesting() != null) {
            Shadows.shadowOf(c.getThreadForTesting().getLooper()).idle();
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue("Controller must be running after rapid restart", c.isRunningForTesting());
        assertFalse("Location listener should still be registered",
                Shadows.shadowOf(lm).getRequestLocationUpdateListeners().isEmpty());
        long threadCount = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("GlassSpeedo") && t.isAlive())
                .count();
        assertEquals("Expected exactly 1 GlassSpeedo thread", 1, threadCount);
        c.destroyForTesting();
    }

    @Test
    public void noThreadLeakOnRegistryReload() throws Exception {
        TripTrackingService.resetForTests();
        TripTrackingService.init(context);
        TripTrackingRegistry registry = TripTrackingService.registry();
        for (int i = 0; i < 10; i++) {
            TripTrackingRegistry.Listener l = (spd, st) -> {};
            registry.addListener(l);
            registry.getController().start();
            registry.removeListener(l);
            registry.getController().stop();
        }
        Thread.sleep(100);
        long threadCount = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("GlassSpeedo") && t.isAlive())
                .count();
        assertTrue("Expected <= 1 GlassSpeedo thread, found: " + threadCount, threadCount <= 1);
    }

    @Test
    public void handleLocationRunsOnWorkerThread() {
        Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true);

        GlassSpeedoTripController c = new GlassSpeedoTripController(context, (v, s) -> {});
        c.start();
        android.os.Looper workerLooper = c.getThreadForTesting().getLooper();
        Shadows.shadowOf(workerLooper).idle();

        Location fix = new Location(LocationManager.GPS_PROVIDER);
        fix.setLatitude(21.0);
        fix.setLongitude(105.0);
        fix.setAccuracy(5.0f);
        fix.setSpeed(10.0f);
        fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        Shadows.shadowOf(lm).simulateLocation(fix);

        LocationHub hub = LocationHub.get(context);
        Shadows.shadowOf(hub.getLooperForTesting()).idle();
        Shadows.shadowOf(workerLooper).idle();

        assertSame("handleLocation must execute on worker thread", c.getThreadForTesting(), c.getLastHandleLocationThreadForTesting());
        c.stop();
        c.destroyForTesting();
    }

    private static String text(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView t) result.append(t.getText()).append('\n');
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) result.append(text(group.getChildAt(i)));
        }
        return result.toString();
    }
}
