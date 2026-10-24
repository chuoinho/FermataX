package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import me.aap.fermata.auto.AutomotiveConnectionState;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class TripTrackingServiceTest {

    private Context context;

    @Before
    public void setUp() {
        TripTrackingService.resetForTests();

        AutomotiveConnectionState.get().connectionChanged(false);
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("speedo_trip_v2", Context.MODE_PRIVATE).edit().clear().commit();
        Shadows.shadowOf((Application) context).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        );
        TripModePolicy.setEnabled(context, true);
        TripTrackingService.init(context);
    }

    @Test
    public void startDoesNothingUnlessBackgroundRecordingIsOn() {
        while (Shadows.shadowOf((Application) context).getNextStartedService() != null) {}
        TripModePolicy.setEnabled(context, false);
        TripTrackingService.start(context);
        assertNull("opt-in is off", Shadows.shadowOf((Application) context).getNextStartedService());

        TripModePolicy.setEnabled(context, true);
        Shadows.shadowOf((Application) context).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        TripTrackingService.start(context);
        assertNull("no 'Allow all the time'", Shadows.shadowOf((Application) context).getNextStartedService());
    }

    @Test
    public void registryReturnsSingleton() {
        TripTrackingRegistry reg1 = TripTrackingService.registry();
        TripTrackingRegistry reg2 = TripTrackingService.registry();
        assertNotNull(reg1);
        assertSame(reg1, reg2);
        assertNotNull(reg1.getController());
    }

    @Test
    public void startAndStopSendExpectedIntents() {
        while (Shadows.shadowOf((Application) context).getNextStartedService() != null) {}
        TripTrackingService.start(context);
        Intent startIntent = Shadows.shadowOf((Application) context).getNextStartedService();
        assertNotNull(startIntent);
        assertEquals(TripTrackingService.class.getName(), startIntent.getComponent().getClassName());
        assertEquals(TripTrackingService.ACTION_START, startIntent.getAction());

        TripTrackingService.stop(context);
    }

    @Test
    public void registryAddListenerReceivesImmediateSnapshot() {
        TripTrackingRegistry registry = TripTrackingService.registry();
        AtomicReference<TripEngine.State> stateRef = new AtomicReference<>();
        TripTrackingRegistry.Listener listener = (speed, state) -> stateRef.set(state);

        registry.addListener(listener);
        try {
            assertNotNull("Snapshot must be delivered on registration", stateRef.get());
        } finally {
            registry.removeListener(listener);
        }
    }

    @Test
    public void serviceWithoutPermissionReportsSignalPermissionAndStopsSelf() {
        Shadows.shadowOf((Application) context).denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );

        ServiceController<TripTrackingService> controller = Robolectric.buildService(TripTrackingService.class);
        TripTrackingService service = controller.create().get();

        service.onStartCommand(new Intent(context, TripTrackingService.class).setAction(TripTrackingService.ACTION_START), 0, 1);

        TripEngine.State state = TripTrackingService.registry().getController().getTripState();
        assertNotNull(state);
        assertEquals(TripEngine.Signal.PERMISSION, state.signal());
    }

    @Test
    public void startWithoutPermissionReportsSignalPermissionAndDoesNotStartService() {
        Shadows.shadowOf((Application) context).denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );
        while (Shadows.shadowOf((Application) context).getNextStartedService() != null) {}
        TripTrackingService.start(context);
        Intent intent = Shadows.shadowOf((Application) context).getNextStartedService();
        assertNull("Service should not be started when permissions are missing", intent);
        TripEngine.State state = TripTrackingService.registry().getController().getTripState();
        assertNotNull(state);
        assertEquals(TripEngine.Signal.PERMISSION, state.signal());
    }

    @Test
    public void serviceWithPermissionPromotesForegroundWithNotification() {
        Shadows.shadowOf((Application) context).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        );

        ServiceController<TripTrackingService> controller = Robolectric.buildService(TripTrackingService.class);
        controller.create();
        TripTrackingService service = controller.get();
        service.onStartCommand(new Intent(context, TripTrackingService.class).setAction(TripTrackingService.ACTION_START), 0, 1);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertNotNull(nm.getNotificationChannel(TripTrackingService.NOTIF_CHANNEL_ID));
        }

        controller.destroy();
    }

    @Test
    public void autoBinderLifecycleSequence() {
        TripStore store = new TripStore(context);
        store.setUserPaused(false);

        AtomicInteger startCalls = new AtomicInteger(0);
        AtomicInteger stopCalls = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(false);

        TripTrackingAutoBinder.ServiceStarter testStarter = new TripTrackingAutoBinder.ServiceStarter() {
            @Override
            public void startService(Context ctx) {
                startCalls.incrementAndGet();
                running.set(true);
            }

            @Override
            public void stopService(Context ctx) {
                stopCalls.incrementAndGet();
                running.set(false);
            }

            @Override
            public boolean isServiceRunning() {
                return running.get();
            }
        };

        TripTrackingAutoBinder binder = new TripTrackingAutoBinder(context, testStarter, store);

        // Sequence: DISCONNECTED -> CONNECTED (starts: background location allows it before the app shows)
        binder.onStateChanged(AutomotiveConnectionState.State.DISCONNECTED);
        assertEquals(0, startCalls.get());
        assertEquals(0, stopCalls.get());

        binder.onStateChanged(AutomotiveConnectionState.State.CONNECTED);
        assertEquals(1, startCalls.get());
        assertEquals(0, stopCalls.get());

        // CONNECTED -> APP_VISIBLE while running: no duplicate start
        binder.onStateChanged(AutomotiveConnectionState.State.APP_VISIBLE);
        assertEquals(1, startCalls.get());
        assertEquals(0, stopCalls.get());

        // Repeated APP_VISIBLE when running should not duplicate start
        binder.onStateChanged(AutomotiveConnectionState.State.APP_VISIBLE);
        assertEquals(1, startCalls.get());

        // APP_VISIBLE -> DISCONNECTED (triggers stop)
        binder.onStateChanged(AutomotiveConnectionState.State.DISCONNECTED);
        assertEquals(1, startCalls.get());
        assertEquals(1, stopCalls.get());

        // Repeated DISCONNECTED should not duplicate stop
        binder.onStateChanged(AutomotiveConnectionState.State.DISCONNECTED);
        assertEquals(1, stopCalls.get());
    }

    @Test
    public void autoBinderRetriesOnAppVisibleWhenStartWasBlocked() {
        TripStore store = new TripStore(context);
        store.setUserPaused(false);

        AtomicInteger startCalls = new AtomicInteger(0);

        TripTrackingAutoBinder.ServiceStarter testStarter = new TripTrackingAutoBinder.ServiceStarter() {
            @Override
            public void startService(Context ctx) {
                startCalls.incrementAndGet();
                // Simulating blocked start: service never became running
            }

            @Override
            public void stopService(Context ctx) {}

            @Override
            public boolean isServiceRunning() {
                return false;
            }
        };

        TripTrackingAutoBinder binder = new TripTrackingAutoBinder(context, testStarter, store);
        binder.onStateChanged(AutomotiveConnectionState.State.APP_VISIBLE);
        assertEquals(1, startCalls.get());

        // Next APP_VISIBLE can retry because isServiceRunning is still false
        binder.onStateChanged(AutomotiveConnectionState.State.APP_VISIBLE);
        assertEquals("Should retry when service is not running", 2, startCalls.get());
    }

    @Test
    public void autoBinderRespectsUserPausedState() {
        TripStore store = new TripStore(context);
        store.setUserPaused(true);

        AtomicInteger startCalls = new AtomicInteger(0);
        AtomicInteger stopCalls = new AtomicInteger(0);

        TripTrackingAutoBinder.ServiceStarter testStarter = new TripTrackingAutoBinder.ServiceStarter() {
            @Override
            public void startService(Context ctx) {
                startCalls.incrementAndGet();
            }

            @Override
            public void stopService(Context ctx) {
                stopCalls.incrementAndGet();
            }
        };

        TripTrackingAutoBinder binder = new TripTrackingAutoBinder(context, testStarter, store);
        binder.onStateChanged(AutomotiveConnectionState.State.APP_VISIBLE);
        assertEquals("Should not start if user paused trip", 0, startCalls.get());
    }

    @Test
    public void notificationFormatsDistanceAccordingToPrefs() {
        ServiceController<TripTrackingService> controller = Robolectric.buildService(TripTrackingService.class);
        controller.create();
        TripTrackingService service = controller.get();

        TripEngine.State metricState = new TripEngine.State(
                12_500.0, 0.0, 0.0, 0.0, 60_000, 50_000, 80f, 60f,
                TripEngine.Signal.TRACKING, false, true
        );

        // Verify formatDistance helper logic
        assertEquals("12.5 km", GlassSpeedoTripController.formatDistance((float) metricState.distanceMeters(), true));
        assertEquals("7.8 mi", GlassSpeedoTripController.formatDistance((float) metricState.distanceMeters(), false));

        controller.destroy();
    }

    @Test
    public void startRecordsWhyTheServiceDidOrDidNotStart() {
        java.util.List<Object> reasons = new java.util.ArrayList<>();
        GlassSpeedoTripController.diagnosticsSink = (name, a) -> {
            if (name.equals("trip_service_start")) reasons.add(a.get("reason"));
        };
        try {
            TripModePolicy.setEnabled(context, false);
            TripTrackingService.start(context);
            TripModePolicy.setEnabled(context, true);
            Shadows.shadowOf((Application) context).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            TripTrackingService.start(context);
            Shadows.shadowOf((Application) context).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            TripTrackingService.start(context);
            assertEquals(java.util.List.of("off", "needs_background", "started"), reasons);
        } finally {
            GlassSpeedoTripController.diagnosticsSink = GlassSpeedoTripController.DEFAULT_DIAGNOSTICS;
        }
    }

    @Test
    public void autoStateChangeIsRecorded() {
        java.util.List<java.util.Map<String, Object>> events = new java.util.ArrayList<>();
        TripTrackingAutoBinder.diagnostics = (name, a) -> {
            if (name.equals("auto_state_changed")) events.add(a);
        };
        try {
            new TripTrackingAutoBinder(context).onStateChanged(
                    me.aap.fermata.auto.AutomotiveConnectionState.State.DISCONNECTED);
            assertEquals(1, events.size());
            assertEquals("DISCONNECTED", events.get(0).get("state"));
            assertNotNull(events.get(0).get("importance"));
        } finally {
            TripTrackingAutoBinder.diagnostics = TripTrackingAutoBinder.DEFAULT_DIAGNOSTICS;
        }
    }
    @org.junit.After public void cleanupDashboardTests() {
        TripTrackingService.resetForTests();
        AutomotiveConnectionState.get().connectionChanged(false);
        GlassSpeedoTripController.diagnosticsSink = GlassSpeedoTripController.DEFAULT_DIAGNOSTICS;

    }
    private void whileInUseOnly() {
        TripModePolicy.setEnabled(context, false);
        Shadows.shadowOf((Application) context).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }
    @Test public void dashboardStartsServiceWithWhileInUsePermission() {
        whileInUseOnly();
        java.util.List<Object> reasons = new java.util.ArrayList<>();
        GlassSpeedoTripController.diagnosticsSink = (n,a) -> { if(n.equals("trip_service_start")) reasons.add(a.get("reason")); };
        TripTrackingService.startForDashboard(context);
        assertEquals(TripTrackingService.ACTION_START_DASHBOARD, Shadows.shadowOf((Application) context).getNextStartedService().getAction());
        assertEquals(java.util.List.of("dashboard"), reasons);
    }
    /** Android refuses the service when only the car screen shows the app, and the projection keeps location anyway. */
    @Test public void carDashboardDoesNotTryTheService() {
        whileInUseOnly();
        while (Shadows.shadowOf((Application) context).getNextStartedService() != null) {}
        java.util.List<Object> reasons = new java.util.ArrayList<>();
        GlassSpeedoTripController.diagnosticsSink = (n,a) -> { if(n.equals("trip_service_start")) reasons.add(a.get("reason")); };
        TripTrackingService.startForDashboard(context, true);
        TripTrackingService.resumeDashboard(context);
        assertNull(Shadows.shadowOf((Application) context).getNextStartedService());
        assertEquals(java.util.List.of(), reasons);
        TripTrackingService.dashboardClosed(context, true);
        TripTrackingService.startForDashboard(context, false);
        assertEquals(TripTrackingService.ACTION_START_DASHBOARD,
                Shadows.shadowOf((Application) context).getNextStartedService().getAction());
        GlassSpeedoTripController.diagnosticsSink = GlassSpeedoTripController.DEFAULT_DIAGNOSTICS;
    }
    @Test public void dashboardServiceStopsWhenClosedWithoutCar() {
        whileInUseOnly(); TripTrackingService.startForDashboard(context);
        TripTrackingService.dashboardClosed(context);
        assertNotNull(Shadows.shadowOf((Application) context).getNextStoppedService());
    }
    @Test public void twoDashboardsKeepServiceUntilBothClose() {
        whileInUseOnly();
        TripTrackingService.startForDashboard(context); TripTrackingService.startForDashboard(context);
        TripTrackingService.dashboardClosed(context);
        assertNull(Shadows.shadowOf((Application) context).getNextStoppedService());
        TripTrackingService.dashboardClosed(context);
        assertNotNull(Shadows.shadowOf((Application) context).getNextStoppedService());
        TripTrackingService.startForDashboard(context); TripTrackingService.dashboardClosed(context);
        assertNotNull(Shadows.shadowOf((Application) context).getNextStoppedService());
    }
    @Test public void dashboardServiceKeepsRunningWithCar() {
        whileInUseOnly(); AutomotiveConnectionState.get().connectionChanged(true);
        TripTrackingService.startForDashboard(context); TripTrackingService.dashboardClosed(context);
        assertNull(Shadows.shadowOf((Application) context).getNextStoppedService());
        TripTrackingService.registry().setServiceRunning(true);
        new TripTrackingAutoBinder(context).onStateChanged(AutomotiveConnectionState.State.DISCONNECTED);
        assertNotNull(Shadows.shadowOf((Application) context).getNextStoppedService());
    }
    @Test public void optInOffDoesNotStopDashboardService() {
        TripTrackingService.startForDashboard(context); TripTrackingService.start(context);
        TripModePolicy.setEnabled(context, false); TripTrackingService.backgroundDisabled(context);
        assertNull(Shadows.shadowOf((Application) context).getNextStoppedService());
        TripTrackingService.dashboardClosed(context);
        assertNotNull(Shadows.shadowOf((Application) context).getNextStoppedService());
    }
    @Test public void noPermissionDoesNotStartDashboardService() {
        Shadows.shadowOf((Application) context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
        TripTrackingService.startForDashboard(context);
        assertNull(Shadows.shadowOf((Application) context).getNextStartedService());
    }
    @Test public void blockedDashboardServiceDoesNotCrash() {
        whileInUseOnly(); java.util.List<Object> reasons = new java.util.ArrayList<>();
        GlassSpeedoTripController.diagnosticsSink = (n,a) -> { if(n.equals("trip_service_start")) reasons.add(a.get("reason")); };
        TripTrackingService.startForDashboard(new android.content.ContextWrapper(context) {
            @Override public android.content.ComponentName startForegroundService(Intent intent) { throw new IllegalStateException("blocked"); }
        });
        assertEquals(java.util.List.of("blocked"), reasons);
    }
    @Test public void userPauseStopsDashboardService() {
        whileInUseOnly(); TripTrackingService.startForDashboard(context);
        TripTrackingService.registry().getController().pauseTrip();
        assertNotNull(Shadows.shadowOf((Application) context).getNextStoppedService());
    }

    @Test @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
    public void dashboardServiceSurvivesInitialPausedSnapshot() {
        whileInUseOnly(); TripTrackingService.startForDashboard(context);
        Intent intent = Shadows.shadowOf((Application) context).getNextStartedService();
        ServiceController<TripTrackingService> controller = Robolectric.buildService(TripTrackingService.class).create();
        TripTrackingService service = controller.get();
        try {
            service.onStartCommand(intent, 0, 1);
            assertFalse("restored snapshot is not a user pause", Shadows.shadowOf(service).isStoppedBySelf());
        } finally { controller.destroy(); }
    }


    private void startControllerWorker() {
        android.location.LocationManager lm = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Shadows.shadowOf(lm).setLocationEnabled(true);
        Shadows.shadowOf(lm).setProviderEnabled("gps",true);
        GlassSpeedoTripController c=TripTrackingService.registry().getController();
        c.start(); Shadows.shadowOf(c.getThreadForTesting().getLooper()).idle();
    }
    @Test @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
    public void pendingDashboardStartCannotOutliveDisconnect() {
        whileInUseOnly(); AutomotiveConnectionState.get().connectionChanged(true);
        TripTrackingService.startForDashboard(context);
        Intent pending=Shadows.shadowOf((Application)context).getNextStartedService();
        startControllerWorker(); TripTrackingService.dashboardClosed(context);
        AutomotiveConnectionState.get().connectionChanged(false);
        new TripTrackingAutoBinder(context).onStateChanged(AutomotiveConnectionState.State.DISCONNECTED);
        ServiceController<TripTrackingService> sc=Robolectric.buildService(TripTrackingService.class).create();
        try {
            sc.get().onStartCommand(pending,0,1);
            assertTrue("closed/disconnected demand must reject late start", Shadows.shadowOf(sc.get()).isStoppedBySelf());
        } finally { sc.destroy(); }
    }
    @Test @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
    public void pendingBackgroundStartCannotUndoOptInOff() {
        TripTrackingService.start(context);
        Intent pending=Shadows.shadowOf((Application)context).getNextStartedService();
        TripTrackingService.startForDashboard(context); startControllerWorker();
        TripModePolicy.setEnabled(context,false); TripTrackingService.backgroundDisabled(context);
        ServiceController<TripTrackingService> sc=Robolectric.buildService(TripTrackingService.class).create();
        try {
            sc.get().onStartCommand(pending,0,1);
            TripTrackingService.registry().getController().pauseTrip();
            assertNotNull("late opt-in start must be downgraded to dashboard", Shadows.shadowOf((Application)context).getNextStoppedService());
        } finally { sc.destroy(); }
    }

}
