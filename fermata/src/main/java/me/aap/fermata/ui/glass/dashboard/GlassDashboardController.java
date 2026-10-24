package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonState;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.location.LocationHub;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.ControlOnlyPresentation;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.fragment.DashboardPlayableNavigator;
import me.aap.fermata.ui.glass.media.GlassMediaPlayerController;
import me.aap.fermata.ui.glass.media.GlassMediaPlayerView;
import me.aap.fermata.ui.glass.nav.AutomotiveNavNotificationService;
import me.aap.fermata.ui.glass.nav.NavTurnInfo;
import me.aap.fermata.ui.glass.quickbar.GlassQuickBarManager;
import me.aap.fermata.ui.glass.quickbar.GlassQuickBarView;
import me.aap.fermata.ui.glass.quickbar.GlassQuickShortcut;
import me.aap.fermata.ui.glass.quickbar.GlassShortcutPickerDialog;
import me.aap.fermata.ui.glass.speedo.GlassSpeedoTripController;
import me.aap.fermata.ui.glass.speedo.GlassSpeedoTripView;
import me.aap.fermata.ui.glass.speedo.TripModePolicy;
import me.aap.fermata.ui.glass.speedo.TripTrackingRegistry;
import me.aap.fermata.ui.glass.speedo.TripTrackingService;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.fermata.ui.glass.weather.GlassOpenMeteoClient;
import me.aap.fermata.ui.glass.weather.WeatherLocationPolicy;

/**
 * Orchestrator connecting the Glass Dashboard components with Android Auto and Fermata services.
 */
public class GlassDashboardController implements AutomotiveNavNotificationService.NavListener {

    private final MainActivityDelegate activity;
    private final GlassDashboardLayout layout;
    private final FermataServiceUiBinder binder;
    private final boolean isNight;
    private final GlassThemeColors colors;

    // Cards & Managers
    private GlassSpeedoTripView speedoView;
    private final TripTrackingRegistry tripRegistry;
    private TripTrackingRegistry.Listener tripListener;

    private GlassNavWeatherView navWeatherView;
    private GlassOpenMeteoClient weatherClient;

    private GlassMediaPlayerView mediaView;
    private GlassMediaPlayerController mediaController;

    private GlassQuickBarView quickBarView;
    private GlassQuickBarManager quickBarManager;

    private boolean isMetric = true;

    public GlassDashboardController(@NonNull MainActivityDelegate activity,
                                    @NonNull GlassDashboardLayout layout,
                                    @Nullable FermataServiceUiBinder binder) {
        this.activity = activity;
        this.layout = layout;
        this.binder = binder;
        this.tripRegistry = TripTrackingService.registry();

        Context ctx = layout.getContext();
        this.isNight = activity.getPrefs().isGlassNightMode();
        this.colors = GlassThemeColors.get(ctx, isNight);

        int speedUnitPref = activity.getPrefs().getIntPref(MainActivityPrefs.GLASS_SPEED_UNIT);
        this.isMetric = (speedUnitPref == 0); // 0 = km/h, 1 = mph

        initCards(ctx);
        initQuickBar(ctx);

        layout.setViews(null, speedoView, navWeatherView, mediaView, quickBarView);
    }

    private void initCards(Context ctx) {
        // Speedo & Trip
        speedoView = new GlassSpeedoTripView(ctx);
        tripListener = (speedKmh, state) -> {
            if (speedoView != null) {
                speedoView.updateSpeed(speedKmh, state, isMetric);
            }
        };
        speedoView.setOnResetTripListener(tripRegistry::resetTrip);
        speedoView.setOnToggleTripListener(tripRegistry::toggleTrip);

        // Nav & Weather
        navWeatherView = new GlassNavWeatherView(ctx);
        weatherClient = new GlassOpenMeteoClient();

        // Media Player Card
        mediaView = new GlassMediaPlayerView(ctx);
        mediaController = new GlassMediaPlayerController();
        mediaView.setController(mediaController);
        mediaView.setOnBrowseMediaClickListener(() -> activity.showFragment(R.id.folders_fragment));
        mediaView.setOnMediaCardClickListener(new GlassMediaPlayerView.OnMediaCardClickListener() {
            @Override
            public void onBrowseMedia() {
                activity.showFragment(R.id.folders_fragment);
            }

            @Override
            public void onResumeRecent() {
                mediaController.resumeRecent(activity);
            }

            @Override
            public void onOpenPlayingItem(MediaLib.PlayableItem item) {
                DashboardPlayableNavigator.openSmartTop(activity, item);
            }

            @Override
            public void onOpenControlOnly(ControlOnlyPresentation controlOnly) {
                MediaSessionCallback cb = activity.getMediaSessionCallback();
                if (cb == null || !cb.isControlOnlyCurrent(controlOnly.leaseId())) {
                    mediaController.refresh();
                    return;
                }
                AddonInfo info = AddonManager.get().getAddonInfo(controlOnly.addonClass());
                if (info != null && info.addonId != 0) {
                    activity.showFragmentWhenReadyGuarded(info.addonId, null,
                            () -> cb.isControlOnlyCurrent(controlOnly.leaseId()));
                }
            }
        });
        if (binder != null) {
            mediaController.bind(binder);
        }
    }

    private void initQuickBar(Context ctx) {
        quickBarView = new GlassQuickBarView(ctx);
        quickBarManager = new GlassQuickBarManager(activity.getPrefs());

        quickBarView.init(quickBarManager, new GlassQuickBarView.Listener() {
            @Override
            public void onShortcutClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut) {
                handleShortcutLaunch(shortcut);
            }

            @Override
            public void onEmptySlotClicked(int slotIndex) {
                GlassShortcutPickerDialog.show(ctx, activity, selected -> {
                    quickBarManager.setCustomShortcut(slotIndex, selected);
                    quickBarView.refresh();
                });
            }

            @Override
            public void onShortcutLongClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut) {
                CharSequence[] options = new CharSequence[]{
                        safeGetString(ctx, R.string.glass_shortcut_open, "Open"),
                        safeGetString(ctx, R.string.glass_shortcut_replace, "Replace"),
                        safeGetString(ctx, R.string.glass_shortcut_remove, "Remove")
                };
                me.aap.utils.ui.view.DialogBuilder dialog = activity.createDialogBuilder(ctx);
                dialog.setTitle(null, shortcut.getTitle());
                dialog.setSingleChoiceItems(options, -1, (d, which) -> {
                    if (d != null) d.dismiss();
                    if (which == 0) {
                        handleShortcutLaunch(shortcut);
                    } else if (which == 1) {
                        GlassShortcutPickerDialog.show(ctx, activity, selected -> {
                            quickBarManager.setCustomShortcut(slotIndex, selected);
                            quickBarView.refresh();
                        });
                    } else if (which == 2) {
                        quickBarManager.removeCustomShortcut(slotIndex);
                        quickBarView.refresh();
                    }
                });
                dialog.setNegativeButton(android.R.string.cancel, null);
                dialog.show();
            }
        });
    }

    private void handleShortcutLaunch(GlassQuickShortcut shortcut) {
        if (shortcut == null) return;
        String id = shortcut.getId();
        boolean launched = false;
        try {
            if (id.equals("lib:playlists")) {
                activity.showFragment(R.id.playlists_fragment);
                launched = true;
            } else if (id.equals("lib:folders")) {
                activity.showFragment(R.id.folders_fragment);
                launched = true;
            } else if (id.equals("lib:favorites")) {
                activity.showFragment(R.id.favorites_fragment);
                launched = true;
            } else if (id.equals("lib:recent")) {
                activity.showFragment(R.id.recent_fragment);
                launched = true;
            } else if (id.equals("car_finder")) {
                activity.showFragment(R.id.car_finder_fragment);
                launched = true;
            } else if (id.equals("settings")) {
                activity.showFragment(R.id.settings_fragment);
                launched = true;
            } else if (id.startsWith("addon:") || "addon".equals(shortcut.getType())) {
                Context ctx = activity.getContext();
                AddonInfo info = GlassQuickBarManager.resolveAddonInfo(shortcut, ctx);
                if (info != null) {
                    AddonManager mgr = AddonManager.get();
                    AddonState state = (mgr != null) ? mgr.getAddonState(info) : AddonState.DISABLED;
                    if (state == AddonState.DISABLED) {
                        String appTitle = (info.addonName != 0 && ctx != null) ? ctx.getString(info.addonName) : shortcut.getTitle();
                        String msg = safeGetString(ctx, R.string.glass_addon_disabled_hint, "This addon is disabled.") + "\n\n" +
                                safeGetString(ctx, R.string.enable, "Enable") + " " + appTitle + "?";
                        me.aap.utils.ui.view.DialogBuilder d = activity.createDialogBuilder(ctx);
                        d.setTitle(null, appTitle);
                        d.setMessage(msg);
                        d.setPositiveButton(R.string.enable, (dialog, which) -> {
                            if (dialog != null) dialog.dismiss();
                            if (info.enabledPref != null) {
                                FermataApplication.get().getPreferenceStore().applyBooleanPref(info.enabledPref, true);
                            }
                            activity.showFragmentWhenReady(info.addonId);
                        });
                        d.setNegativeButton(android.R.string.cancel, null);
                        d.show();
                        return;
                    }

                    if (info.hasFragment && info.addonId != 0) {
                        launched = activity.showFragmentWhenReady(info.addonId);
                    }
                }
            }
        } catch (Throwable e) {
            launched = false;
        }

        if (!launched) {
            Context ctx = activity.getContext();
            me.aap.utils.ui.UiUtils.showAlert(ctx, ctx.getString(R.string.glass_shortcut_unavailable, shortcut.getTitle()));
        }
    }

    private String safeGetString(@Nullable Context context, int resId, String fallback) {
        if (context != null && resId != 0) {
            try {
                return context.getString(resId);
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    private final android.os.Handler weatherHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable gpsTimeoutRunnable;
    private final Runnable scheduledWeatherRefresh = new Runnable() {
        @Override
        public void run() {
            if (isStopped) return;
            Context ctx = activity.getContext();
            if (ctx != null) {
                refreshWeather(ctx, true);
            }
        }
    };
    // On Android Auto granting the permission on the phone does not resume the car screen, so poll until granted.
    private final Runnable permissionWatch = new Runnable() {
        @Override
        public void run() {
            Context ctx = activity.getContext();
            if (isStopped || ctx == null) return;
            if (!TripModePolicy.hasForegroundLocation(ctx)) {
                weatherHandler.postDelayed(this, 2_000L);
                return;
            }
            tripRegistry.getController().onHostResumed();
            speedoView.setTripMode(TripModePolicy.current(ctx));
            refreshWeather(ctx, true);
        }
    };
    private boolean dashboardStarted;
    private volatile boolean isStopped = false;
    private boolean weatherAcquired = false;
    // True from the start of a refresh until its forecast or error arrives.
    private boolean weatherBusy = false;
    // Identifies the current one-shot location request; stale hub callbacks are ignored.
    @Nullable private Object weatherRequest;
    private long lastWeatherFetchMs = 0L;

    private void scheduleNextWeatherRefresh() {
        if (isStopped) return;
        weatherHandler.removeCallbacks(scheduledWeatherRefresh);
        if (AutomotiveConnectionState.get().isProjectionConnected()) {
            long delay = WeatherLocationPolicy.nextFetchDelayMs(System.currentTimeMillis(), lastWeatherFetchMs);
            if (delay <= 0) delay = WeatherLocationPolicy.REFRESH_MS;
            weatherHandler.postDelayed(scheduledWeatherRefresh, delay);
        }
    }

    public void start() {
        if (dashboardStarted) return;
        dashboardStarted = true;
        isStopped = false;
        tripRegistry.addListener(tripListener);
        // The visible dashboard always records; the service only keeps recording when it is allowed to.
        tripRegistry.getController().start();
        TripTrackingService.startForDashboard(activity.getContext(), activity.isCarActivity());
        AutomotiveNavNotificationService.registerListener(this);
        speedoView.setTripMode(TripModePolicy.current(activity.getContext()));

        // On Android Auto this is the single initial load; the schedule takes over afterwards.
        refreshWeather(activity.getContext(), AutomotiveConnectionState.get().isProjectionConnected());
        if (!TripModePolicy.hasForegroundLocation(activity.getContext())) weatherHandler.postDelayed(permissionWatch, 2_000L);
    }

    public void stop() {
        if (!dashboardStarted) return;
        dashboardStarted = false;
        isStopped = true;
        weatherHandler.removeCallbacks(scheduledWeatherRefresh);
        weatherHandler.removeCallbacks(permissionWatch);
        if (gpsTimeoutRunnable != null) {
            weatherHandler.removeCallbacks(gpsTimeoutRunnable);
            gpsTimeoutRunnable = null;
        }
        if (weatherAcquired) {
            Context ctx = activity.getContext();
            if (ctx != null) {
                LocationHub.get(ctx).release("weather");
            }
            weatherAcquired = false;
        }
        weatherClient.shutdown();
        tripRegistry.removeListener(tripListener);
        tripRegistry.getController().stop();
        TripTrackingService.dashboardClosed(activity.getContext(), activity.isCarActivity());
        AutomotiveNavNotificationService.unregisterListener(this);
        mediaController.unbind();
    }

    public void onHostResumed() {
        if (!isStopped) tripRegistry.getController().onHostResumed();
        if (!isStopped) {
            // The user may have changed the opt-in or the permission on the phone meanwhile.
            speedoView.setTripMode(TripModePolicy.current(activity.getContext()));
            refreshWeather(activity.getContext());
        }
    }

    @Override
    public void onNavTurnUpdated(@NonNull NavTurnInfo info) {
        navWeatherView.bindNavTurn(info);
    }

    private void refreshWeather(Context ctx) {
        refreshWeather(ctx, false);
    }

    private void refreshWeather(Context ctx, boolean force) {
        if (isStopped || ctx == null) return;
        boolean isAA = AutomotiveConnectionState.get().isProjectionConnected();
        boolean hasData = navWeatherView != null && navWeatherView.hasForecastData();
        if (!WeatherLocationPolicy.shouldRefresh(force, weatherBusy, isAA,
                System.currentTimeMillis(), lastWeatherFetchMs, hasData)) {
            return;
        }
        weatherBusy = true;
        weatherRequest = null;

        if (gpsTimeoutRunnable != null) {
            weatherHandler.removeCallbacks(gpsTimeoutRunnable);
            gpsTimeoutRunnable = null;
        }
        if (weatherAcquired) {
            LocationHub.get(ctx).release("weather");
            weatherAcquired = false;
        }

        LocationHub hub = LocationHub.get(ctx);
        Location loc = null;
        Location latest = hub.latest();
        if (latest != null) {
            long ageMs = latest.getElapsedRealtimeNanos() > 0
                    ? (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos()) / 1_000_000L
                    : Math.max(0, System.currentTimeMillis() - latest.getTime());
            if (ageMs <= 5 * 60_000L) {
                loc = latest;
            }
        }

        if (loc == null) {
            loc = hub.freshSystemLocation(30 * 60_000L);
        }

        if (loc != null) {
            fetchWeatherForLocation(ctx, loc.getLatitude(), loc.getLongitude(), force);
            return;
        }

        boolean hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!hasPerm) {
            boolean isCar = activity.isCarActivity();
            String hint = ctx.getString(isCar ?
                    me.aap.fermata.R.string.glass_weather_perm_car_hint :
                    me.aap.fermata.R.string.glass_weather_perm_phone_hint);
            weatherBusy = false;
            Runnable req = isCar ? null : () -> {
                Context c = activity.getContext();
                if (c instanceof android.app.Activity) {
                    androidx.core.app.ActivityCompat.requestPermissions((android.app.Activity) c,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1010);
                }
            };
            navWeatherView.showPermissionNeeded(hint, req);
            // On Android Auto non-forced refreshes are blocked, so the schedule is the only retry.
            scheduleNextWeatherRefresh();
            return;
        }

        navWeatherView.showLocating(ctx.getString(me.aap.fermata.R.string.glass_weather_waiting_gps));
        weatherAcquired = true;
        final Object request = new Object();
        weatherRequest = request;

        gpsTimeoutRunnable = () -> {
            if (isStopped || weatherRequest != request) return;
            weatherRequest = null;
            if (weatherAcquired) {
                hub.release("weather");
                weatherAcquired = false;
            }
            Location fallback = hub.freshSystemLocation(30 * 60_000L);
            if (fallback != null) {
                fetchWeatherForLocation(ctx, fallback.getLatitude(), fallback.getLongitude(), force);
            } else {
                weatherBusy = false;
                navWeatherView.showError(safeGetString(ctx, R.string.glass_nav_gps_timeout, "GPS timeout. Tap to retry."), () -> refreshWeather(ctx, force));
                scheduleNextWeatherRefresh();
            }
        };
        weatherHandler.postDelayed(gpsTimeoutRunnable, 15000L);

        hub.acquire("weather", new LocationHub.Listener() {
            @Override
            public void onLocation(@NonNull Location updatedLoc) {
                weatherHandler.post(() -> {
                    // The hub delivers at 1 Hz: only the first fix of this request is used.
                    if (isStopped || weatherRequest != request) return;
                    weatherRequest = null;
                    if (gpsTimeoutRunnable != null) {
                        weatherHandler.removeCallbacks(gpsTimeoutRunnable);
                        gpsTimeoutRunnable = null;
                    }
                    hub.release("weather");
                    weatherAcquired = false;
                    if (updatedLoc != null) {
                        fetchWeatherForLocation(ctx, updatedLoc.getLatitude(), updatedLoc.getLongitude(), force);
                    }
                });
            }
        });
    }

    private void fetchWeatherForLocation(Context ctx, double lat, double lon, boolean force) {
        weatherClient.fetchForecast(lat, lon, isMetric, force, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(@NonNull GlassOpenMeteoClient.HourlyForecast forecast) {
                lastWeatherFetchMs = forecast.getTimestamp();
                weatherBusy = false;
                if (!isStopped) {
                    navWeatherView.bindWeather(forecast, isMetric, null, forecast.isExpired());
                }
                scheduleNextWeatherRefresh();
            }

            @Override
            public void onError(@NonNull Throwable error) {
                weatherBusy = false;
                if (!isStopped) {
                    navWeatherView.showError(safeGetString(ctx, R.string.glass_nav_network_error, "Network error. Tap to retry."), () -> fetchWeatherForLocation(ctx, lat, lon, false));
                }
                scheduleNextWeatherRefresh();
            }
        });
    }

    public GlassSpeedoTripView getSpeedoView() {
        return speedoView;
    }

    public GlassNavWeatherView getNavWeatherView() {
        return navWeatherView;
    }

    public GlassMediaPlayerView getMediaView() {
        return mediaView;
    }

    public GlassQuickBarView getQuickBarView() {
        return quickBarView;
    }
}
