package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
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

    // Header views
    private LinearLayout headerLayout;
    private TextView tvClock;
    private TextView tvDate;
    private ImageView btnDayNight;

    // Cards & Managers
    private GlassSpeedoTripView speedoView;
    private GlassSpeedoTripController speedoController;

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

        Context ctx = layout.getContext();
        this.isNight = activity.getPrefs().isGlassNightMode();
        this.colors = GlassThemeColors.get(ctx, isNight);

        int speedUnitPref = activity.getPrefs().getIntPref(MainActivityPrefs.GLASS_SPEED_UNIT);
        this.isMetric = (speedUnitPref == 0); // 0 = km/h, 1 = mph

        initHeader(ctx);
        initCards(ctx);
        initQuickBar(ctx);

        layout.setViews(headerLayout, speedoView, navWeatherView, mediaView, quickBarView);
    }

    private void initHeader(Context ctx) {
        headerLayout = new LinearLayout(ctx);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout leftSection = new LinearLayout(ctx);
        leftSection.setOrientation(LinearLayout.HORIZONTAL);
        leftSection.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        leftSection.setLayoutParams(leftLp);

        // Digital Clock
        tvClock = new TextView(ctx);
        tvClock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        tvClock.setTextSize(20f);
        tvClock.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvClock.setTextColor(colors.getTextPrimary());
        leftSection.addView(tvClock);

        // Date pill
        tvDate = new TextView(ctx);
        tvDate.setText(new SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(new Date()));
        tvDate.setTextSize(11f);
        tvDate.setTextColor(colors.getTextSecondary());
        int datePadH = GlassDimensions.dpToPx(ctx, 10);
        int datePadV = GlassDimensions.dpToPx(ctx, 4);
        tvDate.setPadding(datePadH, datePadV, datePadH, datePadV);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateLp.leftMargin = GlassDimensions.dpToPx(ctx, 12);
        tvDate.setLayoutParams(dateLp);
        tvDate.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 12f, isNight));
        leftSection.addView(tvDate);

        headerLayout.addView(leftSection);

        // Weight-based spacer pushes Day/Night quick switch reliably to right edge
        View spacer = new View(ctx);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1.0f);
        headerLayout.addView(spacer, spacerLp);

        // Glass Day/Night Quick Toggle Switch with 48dp touch target
        btnDayNight = new ImageView(ctx);
        int toggleSize = GlassDimensions.dpToPx(ctx, 48);
        int togglePad = GlassDimensions.dpToPx(ctx, 12);
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(toggleSize, toggleSize);
        btnDayNight.setLayoutParams(toggleLp);
        btnDayNight.setPadding(togglePad, togglePad, togglePad, togglePad);
        btnDayNight.setImageResource(isNight ? R.drawable.ic_glass_night : R.drawable.ic_glass_day);
        btnDayNight.setColorFilter(colors.getTextPrimary());
        btnDayNight.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 24f, isNight));
        btnDayNight.setFocusable(true);
        String dayNightDesc = isNight ? "Switch to Day Mode" : "Switch to Night Mode";
        try {
            dayNightDesc = ctx.getString(isNight ? R.string.glass_appearance_day : R.string.glass_appearance_night);
        } catch (Throwable ignored) {}
        btnDayNight.setContentDescription(dayNightDesc);
        btnDayNight.setOnClickListener(v -> {
            int nextMode = isNight ? MainActivityPrefs.GLASS_APPEARANCE_DAY : MainActivityPrefs.GLASS_APPEARANCE_NIGHT;
            activity.getPrefs().setGlassAppearancePref(nextMode);
        });
        headerLayout.addView(btnDayNight);
    }

    private void initCards(Context ctx) {
        // Speedo & Trip
        speedoView = new GlassSpeedoTripView(ctx);
        speedoController = new GlassSpeedoTripController(ctx, (speedKmh, state) -> {
            speedoView.updateSpeed(speedKmh, state, isMetric);
        });
        speedoView.setOnResetTripListener(speedoController::resetTrip);

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
                CharSequence[] options = new CharSequence[]{"Open", "Replace", "Remove", "Manage All 6 Slots"};
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
                    } else {
                        showManageAllSlotsDialog(ctx);
                    }
                });
                dialog.setNegativeButton(android.R.string.cancel, null);
                dialog.show();
            }
        });
    }

    public void showManageAllSlotsDialog(Context ctx) {
        GlassQuickBarManager.showManageDialog(ctx, activity, () -> quickBarView.refresh());
    }

    private void handleShortcutLaunch(GlassQuickShortcut shortcut) {
        if (shortcut == null) return;
        String id = shortcut.getId();
        boolean launched = false;
        try {
            if (id.startsWith("addon:")) {
                int addonId = Integer.parseInt(id.substring(6));
                launched = activity.showFragmentWhenReady(addonId);
            } else if (id.equals("lib:playlists")) {
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
            }
        } catch (Throwable e) {
            launched = false;
        }

        if (!launched) {
            me.aap.utils.ui.UiUtils.showAlert(activity.getContext(), "Shortcut target unavailable: " + shortcut.getTitle());
        }
    }

    public void start() {
        speedoController.start();
        AutomotiveNavNotificationService.registerListener(this);
    }

    public void stop() {
        speedoController.stop();
        AutomotiveNavNotificationService.unregisterListener(this);
        mediaController.unbind();
    }

    @Override
    public void onNavTurnUpdated(@NonNull NavTurnInfo info) {
        navWeatherView.bindNavTurn(info);
    }

    private void refreshWeather(Context ctx) {
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
            navWeatherView.showPermissionNeeded("Please grant location permission on phone.", () -> {
                Context c = activity.getContext();
                if (c instanceof android.app.Activity) {
                    androidx.core.app.ActivityCompat.requestPermissions((android.app.Activity) c,
                            new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1010);
                }
            });
            return;
        }

        navWeatherView.showLocating("Waiting for GPS satellite fix...");
        me.aap.fermata.carfinder.CarLocatorManager.get().startTrackingUserLocation(ctx, new me.aap.fermata.carfinder.CarLocatorManager.UserLocationListener() {
            @Override
            public void onUserLocationChanged(Location updatedLoc) {
                me.aap.fermata.carfinder.CarLocatorManager.get().stopTrackingUserLocation(this);
                if (updatedLoc != null) {
                    fetchWeatherForLocation(ctx, updatedLoc.getLatitude(), updatedLoc.getLongitude());
                }
            }
        });
    }

    private void fetchWeatherForLocation(Context ctx, double lat, double lon) {
        weatherClient.fetchForecast(lat, lon, isMetric, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(@NonNull GlassOpenMeteoClient.HourlyForecast forecast) {
                navWeatherView.bindWeather(forecast, isMetric, null, forecast.isExpired());
            }

            @Override
            public void onError(@NonNull Throwable error) {
                navWeatherView.showError("Network error. Tap to retry.", () -> fetchWeatherForLocation(ctx, lat, lon));
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
