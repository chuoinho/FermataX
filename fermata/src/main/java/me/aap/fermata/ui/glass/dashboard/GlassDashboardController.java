package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private FrameLayout headerLayout;
    private TextView tvClock;
    private TextView tvDate;
    private TextView tvWeatherChip;
    private ImageView btnSettings;

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
        int uiMode = ctx.getResources().getConfiguration().uiMode;
        this.isNight = (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_NO;
        this.colors = GlassThemeColors.get(ctx, isNight);

        int speedUnitPref = activity.getPrefs().getIntPref(MainActivityPrefs.GLASS_SPEED_UNIT);
        this.isMetric = (speedUnitPref == 0); // 0 = km/h, 1 = mph

        initHeader(ctx);
        initCards(ctx);
        initQuickBar(ctx);

        layout.setViews(headerLayout, speedoView, navWeatherView, mediaView, quickBarView);
    }

    private void initHeader(Context ctx) {
        headerLayout = new FrameLayout(ctx);

        LinearLayout leftSection = new LinearLayout(ctx);
        leftSection.setOrientation(LinearLayout.HORIZONTAL);
        leftSection.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
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

        // Right Section: Weather Chip + Settings Gear Button
        LinearLayout rightSection = new LinearLayout(ctx);
        rightSection.setOrientation(LinearLayout.HORIZONTAL);
        rightSection.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        rightSection.setLayoutParams(rightLp);

        tvWeatherChip = new TextView(ctx);
        tvWeatherChip.setText("--°");
        tvWeatherChip.setTextSize(12f);
        tvWeatherChip.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeatherChip.setTextColor(colors.getTextPrimary());
        int chipPadH = GlassDimensions.dpToPx(ctx, 12);
        int chipPadV = GlassDimensions.dpToPx(ctx, 4);
        tvWeatherChip.setPadding(chipPadH, chipPadV, chipPadH, chipPadV);
        tvWeatherChip.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 14f, isNight));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = GlassDimensions.dpToPx(ctx, 10);
        tvWeatherChip.setLayoutParams(chipLp);
        rightSection.addView(tvWeatherChip);

        // Top-Right Frosted Glass Settings Button
        btnSettings = new ImageView(ctx);
        int gearSize = GlassDimensions.dpToPx(ctx, 36);
        int gearPad = GlassDimensions.dpToPx(ctx, 8);
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(gearSize, gearSize);
        btnSettings.setLayoutParams(gearLp);
        btnSettings.setPadding(gearPad, gearPad, gearPad, gearPad);
        btnSettings.setImageResource(R.drawable.settings);
        btnSettings.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 18f, isNight));
        btnSettings.setOnClickListener(v -> {
            activity.showFragment(R.id.settings_fragment);
        });
        rightSection.addView(btnSettings);

        headerLayout.addView(rightSection);
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

        // Query default weather (Hanoi lat=21.0285, lon=105.8542 as initial or device location)
        weatherClient.fetchForecast(21.0285, 105.8542, isMetric, new GlassOpenMeteoClient.ForecastCallback() {
            @Override
            public void onSuccess(@NonNull GlassOpenMeteoClient.HourlyForecast forecast) {
                navWeatherView.bindWeather(forecast, isMetric);
                String unit = isMetric ? "°C" : "°F";
                tvWeatherChip.setText(String.format(Locale.US, "%.0f%s", forecast.getCurrentTemp(), unit));
            }

            @Override
            public void onError(@NonNull Throwable error) {}
        });

        // Media Player Card
        mediaView = new GlassMediaPlayerView(ctx);
        mediaController = new GlassMediaPlayerController();
        mediaView.setController(mediaController);
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
                GlassShortcutPickerDialog.show(ctx, selected -> {
                    quickBarManager.setCustomShortcut(slotIndex, selected);
                    quickBarView.refresh();
                });
            }

            @Override
            public void onShortcutLongClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut) {
                // Long press gives option to replace or remove
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                        .setTitle(shortcut.getTitle())
                        .setItems(new String[]{"Replace", "Remove"}, (d, which) -> {
                            if (which == 0) {
                                GlassShortcutPickerDialog.show(ctx, selected -> {
                                    quickBarManager.setCustomShortcut(slotIndex, selected);
                                    quickBarView.refresh();
                                });
                            } else {
                                quickBarManager.removeCustomShortcut(slotIndex);
                                quickBarView.refresh();
                            }
                        })
                        .create();
                if (activity.isGlassTheme()) {
                    me.aap.fermata.ui.glass.dialog.GlassDialogBuilder.styleAlertDialog(dialog, ctx);
                }
                dialog.show();
            }

            @Override
            public void onAddonsCatalogClicked() {
                activity.showFragment(R.id.glass_addons_catalog_fragment);
            }

            @Override
            public void onExitClicked() {
                activity.finish();
            }
        });
    }

    private void handleShortcutLaunch(GlassQuickShortcut shortcut) {
        String id = shortcut.getId();
        if (id.startsWith("addon:")) {
            try {
                int addonId = Integer.parseInt(id.substring(6));
                activity.showFragmentWhenReady(addonId);
            } catch (Exception ignored) {}
        } else if (id.equals("lib:playlists")) {
            activity.showFragment(R.id.playlists_fragment);
        } else if (id.equals("lib:folders")) {
            activity.showFragment(R.id.folders_fragment);
        } else if (id.equals("lib:favorites")) {
            activity.showFragment(R.id.favorites_fragment);
        } else if (id.equals("lib:recent")) {
            activity.showFragment(R.id.recent_fragment);
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
