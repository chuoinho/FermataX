package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
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
import me.aap.fermata.ui.glass.speedo.TripTrackingRegistry;
import me.aap.fermata.ui.glass.speedo.TripTrackingService;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.fermata.ui.glass.weather.GlassOpenMeteoClient;

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

        // Fetch real device/GPS location and weather states
        refreshWeather(ctx);

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
            } else if (id.equals("car_finder") || id.endsWith("car_finder")) {
                activity.showFragment(R.id.car_finder_fragment);
                launched = true;
            } else if (id.equals("settings") || id.endsWith("settings")) {
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
            me.aap.utils.ui.UiUtils.showAlert(activity.getContext(), "Shortcut target unavailable: " + shortcut.getTitle());
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

    private me.aap.fermata.carfinder.CarLocatorManager.UserLocationListener activeLocationListener;
    private final android.os.Handler weatherHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable gpsTimeoutRunnable;
    private volatile boolean isStopped = false;

    public void start() {
        isStopped = false;
        tripRegistry.addListener(tripListener);
        // The visible dashboard always records; the service only keeps recording when it is allowed to.
        tripRegistry.getController().start();
        AutomotiveNavNotificationService.registerListener(this);
    }

    public void stop() {
        isStopped = true;
        if (gpsTimeoutRunnable != null) {
            weatherHandler.removeCallbacks(gpsTimeoutRunnable);
            gpsTimeoutRunnable = null;
        }
        if (activeLocationListener != null) {
            me.aap.fermata.carfinder.CarLocatorManager.get().stopTrackingUserLocation(activeLocationListener);
            activeLocationListener = null;
        }
        weatherClient.shutdown();
        tripRegistry.removeListener(tripListener);
        tripRegistry.getController().stop();
        AutomotiveNavNotificationService.unregisterListener(this);
        mediaController.unbind();
    }

    public void onHostResumed() {
        if (!isStopped) tripRegistry.getController().onHostResumed();
        if (!isStopped && navWeatherView != null && !navWeatherView.hasForecastData()) {
            refreshWeather(activity.getContext());
        }
    }

    @Override
    public void onNavTurnUpdated(@NonNull NavTurnInfo info) {
        navWeatherView.bindNavTurn(info);
    }

    private void refreshWeather(Context ctx) {
        if (isStopped) return;
        if (gpsTimeoutRunnable != null) {
            weatherHandler.removeCallbacks(gpsTimeoutRunnable);
            gpsTimeoutRunnable = null;
        }
        if (activeLocationListener != null) {
            me.aap.fermata.carfinder.CarLocatorManager.get().stopTrackingUserLocation(activeLocationListener);
            activeLocationListener = null;
        }

        Location loc = me.aap.fermata.carfinder.CarLocatorManager.get().queryBestKnownUserLocation(ctx);
        if (loc != null) {
            fetchWeatherForLocation(ctx, loc.getLatitude(), loc.getLongitude());
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
            Runnable req = isCar ? null : () -> {
                Context c = activity.getContext();
                if (c instanceof android.app.Activity) {
                    androidx.core.app.ActivityCompat.requestPermissions((android.app.Activity) c,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1010);
                }
            };
            navWeatherView.showPermissionNeeded(hint, req);
            return;
        }

        navWeatherView.showLocating(ctx.getString(me.aap.fermata.R.string.glass_weather_waiting_gps));
        activeLocationListener = new me.aap.fermata.carfinder.CarLocatorManager.UserLocationListener() {
            @Override
            public void onUserLocationChanged(Location updatedLoc) {
                if (isStopped) return;
                if (gpsTimeoutRunnable != null) {
                    weatherHandler.removeCallbacks(gpsTimeoutRunnable);
                    gpsTimeoutRunnable = null;
                }
                me.aap.fermata.carfinder.CarLocatorManager.get().stopTrackingUserLocation(this);
                activeLocationListener = null;
                if (updatedLoc != null) {
                    fetchWeatherForLocation(ctx, updatedLoc.getLatitude(), updatedLoc.getLongitude());
                }
            }
        };

        gpsTimeoutRunnable = () -> {
            if (isStopped) return;
            if (activeLocationListener != null) {
                me.aap.fermata.carfinder.CarLocatorManager.get().stopTrackingUserLocation(activeLocationListener);
                activeLocationListener = null;
            }
            Location fallback = me.aap.fermata.carfinder.CarLocatorManager.get().queryBestKnownUserLocation(ctx);
            if (fallback != null) {
                fetchWeatherForLocation(ctx, fallback.getLatitude(), fallback.getLongitude());
            } else {
                navWeatherView.showError(safeGetString(ctx, R.string.glass_nav_gps_timeout, "GPS timeout. Tap to retry."), () -> refreshWeather(ctx));
            }
        };
        weatherHandler.postDelayed(gpsTimeoutRunnable, 15000L);

        me.aap.fermata.carfinder.CarLocatorManager.get().startTrackingUserLocation(ctx, activeLocationListener);
    }

    private void fetchWeatherForLocation(Context ctx, double lat, double lon) {
        weatherClient.fetchForecast(lat, lon, isMetric, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(@NonNull GlassOpenMeteoClient.HourlyForecast forecast) {
                if (!isStopped) {
                    navWeatherView.bindWeather(forecast, isMetric, null, forecast.isExpired());
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                if (!isStopped) {
                    navWeatherView.showError(safeGetString(ctx, R.string.glass_nav_network_error, "Network error. Tap to retry."), () -> fetchWeatherForLocation(ctx, lat, lon));
                }
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
