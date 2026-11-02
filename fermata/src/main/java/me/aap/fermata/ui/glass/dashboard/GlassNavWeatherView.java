package me.aap.fermata.ui.glass.dashboard;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.glass.nav.NavTurnInfo;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.fermata.ui.glass.weather.GlassOpenMeteoClient;

/**
 * Automotive frosted glass card that seamlessly switches between turn-by-turn navigation
 * guidance and an ambient 24-hour Open-Meteo weather forecast fallback.
 */
public class GlassNavWeatherView extends FrameLayout {

    private boolean isNight;
    private GlassThemeColors colors;

    // Nav container
    private LinearLayout navContainer;
    private TextView tvNavHeader;
    private TurnArrowView navArrowView;
    private TextView tvNavInstruction;
    private TextView tvNavDistance;
    private TextView tvNavEta;
    private TextView tvNavRemaining;
    private TextView btnNavExit;
    private LinearLayout navBottomBar;
    private PendingIntent activeExitIntent;

    // Weather container
    private LinearLayout weatherContainer;
    private TextView tvWeatherHeader;
    private TextView tvWeatherTemp;
    private TextView tvWeatherCondition;
    private TextView tvWeatherHighLow;
    private TextView tvWeatherLocation;
    private TextView btnWeatherAction;
    private HorizontalScrollView weatherScroll;
    private LinearLayout hourlyTimelineRow;

    private GlassOpenMeteoClient.HourlyForecast lastForecast;
    private boolean lastIsMetric = true;
    private String lastLocationName;
    private boolean lastIsStale;

    public GlassNavWeatherView(@NonNull Context context) {
        this(context, null);
    }

    public GlassNavWeatherView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassNavWeatherView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);
        initView(context);
    }

    private void initView(Context context) {
        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
        int padH = GlassDimensions.dpToPx(context, 12);
        int padV = GlassDimensions.dpToPx(context, 10);
        setPadding(padH, padV, padH, padV);

        initNavLayout(context);
        initWeatherLayout(context);

        // Default to weather fallback view
        navContainer.setVisibility(View.GONE);
        weatherContainer.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            float density = getResources().getDisplayMetrics().density;
            float hDp = h / Math.max(0.1f, density);
            float wDp = w / Math.max(0.1f, density);
            if (hDp < 220f || wDp < 160f) {
                tvWeatherTemp.setTextSize(36f);
            } else if (hDp < 260f || wDp < 190f) {
                tvWeatherTemp.setTextSize(42f);
            } else {
                tvWeatherTemp.setTextSize(50f);
            }
        }
    }

    private void initNavLayout(Context context) {
        navContainer = new LinearLayout(context);
        navContainer.setOrientation(LinearLayout.VERTICAL);
        navContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        navContainer.setLayoutParams(lp);

        // Header
        tvNavHeader = new TextView(context);
        tvNavHeader.setText(safeGetString(context, R.string.glass_nav_header, "NAVIGATION"));
        tvNavHeader.setTextSize(11f);
        tvNavHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvNavHeader.setTextColor(colors.getTextSecondary());
        tvNavHeader.setLetterSpacing(0.08f);
        navContainer.addView(tvNavHeader);

        // Turn Maneuver Center
        LinearLayout maneuverLayout = new LinearLayout(context);
        maneuverLayout.setOrientation(LinearLayout.VERTICAL);
        maneuverLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams manLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        maneuverLayout.setLayoutParams(manLp);

        navArrowView = new TurnArrowView(context, colors);
        int arrowSize = GlassDimensions.dpToPx(context, 64);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(arrowSize, arrowSize);
        arrowLp.bottomMargin = GlassDimensions.dpToPx(context, 8);
        maneuverLayout.addView(navArrowView, arrowLp);

        tvNavDistance = new TextView(context);
        tvNavDistance.setText("");
        tvNavDistance.setTextSize(26f);
        tvNavDistance.setTypeface(Typeface.DEFAULT_BOLD);
        tvNavDistance.setTextColor(colors.getActionText());
        tvNavDistance.setGravity(Gravity.CENTER);
        maneuverLayout.addView(tvNavDistance);

        tvNavInstruction = new TextView(context);
        tvNavInstruction.setText("");
        tvNavInstruction.setTextSize(16f);
        tvNavInstruction.setTypeface(Typeface.DEFAULT_BOLD);
        tvNavInstruction.setTextColor(colors.getTextPrimary());
        tvNavInstruction.setGravity(Gravity.CENTER);
        tvNavInstruction.setMaxLines(2);
        maneuverLayout.addView(tvNavInstruction);

        navContainer.addView(maneuverLayout);

        // Bottom Nav Bar (ETA, remaining distance, exit button)
        navBottomBar = new LinearLayout(context);
        navBottomBar.setOrientation(LinearLayout.HORIZONTAL);
        navBottomBar.setGravity(Gravity.CENTER_VERTICAL);
        int barPadding = GlassDimensions.dpToPx(context, 8);
        navBottomBar.setPadding(barPadding, barPadding, barPadding, barPadding);
        navBottomBar.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        navBottomBar.setLayoutParams(botLp);

        LinearLayout etaLayout = new LinearLayout(context);
        etaLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams etaLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        etaLayout.setLayoutParams(etaLp);

        tvNavEta = new TextView(context);
        tvNavEta.setText("--:--");
        tvNavEta.setTextSize(14f);
        tvNavEta.setTypeface(Typeface.DEFAULT_BOLD);
        tvNavEta.setTextColor(colors.getTextPrimary());
        etaLayout.addView(tvNavEta);

        tvNavRemaining = new TextView(context);
        tvNavRemaining.setText("");
        tvNavRemaining.setTextSize(10f);
        tvNavRemaining.setTextColor(colors.getTextSecondary());
        etaLayout.addView(tvNavRemaining);

        navBottomBar.addView(etaLayout);

        btnNavExit = new TextView(context);
        btnNavExit.setText(safeGetString(context, R.string.glass_nav_exit, "EXIT"));
        btnNavExit.setTextSize(11f);
        btnNavExit.setTypeface(Typeface.DEFAULT_BOLD);
        btnNavExit.setTextColor(colors.getTextPrimary());
        btnNavExit.setGravity(Gravity.CENTER);
        int btnPadH = GlassDimensions.dpToPx(context, 14);
        int btnPadV = GlassDimensions.dpToPx(context, 6);
        btnNavExit.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        btnNavExit.setBackground(GlassDrawables.createGlassPillDrawable(context, 14f, isNight));
        btnNavExit.setOnClickListener(v -> {
            if (activeExitIntent != null) {
                try {
                    activeExitIntent.send();
                } catch (Exception ignored) {}
            }
        });
        navBottomBar.addView(btnNavExit);

        navContainer.addView(navBottomBar);
        addView(navContainer);
    }

    private void initWeatherLayout(Context context) {
        weatherContainer = new LinearLayout(context);
        weatherContainer.setOrientation(LinearLayout.VERTICAL);
        weatherContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        weatherContainer.setLayoutParams(lp);

        // Header
        tvWeatherHeader = new TextView(context);
        tvWeatherHeader.setText(safeGetString(context, R.string.glass_weather_header, "WEATHER & FORECAST"));
        tvWeatherHeader.setTextSize(11f);
        tvWeatherHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeatherHeader.setTextColor(colors.getTextSecondary());
        tvWeatherHeader.setLetterSpacing(0.08f);
        weatherContainer.addView(tvWeatherHeader);

        // Center Current Weather
        LinearLayout centerWeather = new LinearLayout(context);
        centerWeather.setOrientation(LinearLayout.VERTICAL);
        centerWeather.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        centerWeather.setLayoutParams(centerLp);

        tvWeatherTemp = new TextView(context);
        tvWeatherTemp.setText("--°");
        tvWeatherTemp.setTextSize(52f);
        tvWeatherTemp.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvWeatherTemp.setTextColor(colors.getTextPrimary());
        tvWeatherTemp.setGravity(Gravity.CENTER);
        centerWeather.addView(tvWeatherTemp);

        tvWeatherCondition = new TextView(context);
        tvWeatherCondition.setText(safeGetString(context, R.string.glass_weather_updating, "Updating..."));
        tvWeatherCondition.setTextSize(14f);
        tvWeatherCondition.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeatherCondition.setTextColor(colors.getActionText());
        tvWeatherCondition.setGravity(Gravity.CENTER);
        centerWeather.addView(tvWeatherCondition);

        tvWeatherHighLow = new TextView(context);
        tvWeatherHighLow.setText("");
        tvWeatherHighLow.setTextSize(11f);
        tvWeatherHighLow.setTextColor(colors.getTextSecondary());
        tvWeatherHighLow.setGravity(Gravity.CENTER);
        centerWeather.addView(tvWeatherHighLow);

        tvWeatherLocation = new TextView(context);
        tvWeatherLocation.setText("");
        tvWeatherLocation.setTextSize(11f);
        tvWeatherLocation.setTextColor(colors.getTextSecondary());
        tvWeatherLocation.setGravity(Gravity.CENTER);
        centerWeather.addView(tvWeatherLocation);

        btnWeatherAction = new TextView(context);
        btnWeatherAction.setText(safeGetString(context, R.string.retry, "Retry"));
        btnWeatherAction.setTextSize(11f);
        btnWeatherAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnWeatherAction.setTextColor(colors.getActionText());
        btnWeatherAction.setGravity(Gravity.CENTER);
        int actPadH = GlassDimensions.dpToPx(context, 12);
        int actPadV = GlassDimensions.dpToPx(context, 6);
        btnWeatherAction.setPadding(actPadH, actPadV, actPadH, actPadV);
        btnWeatherAction.setBackground(GlassDrawables.createGlassPillDrawable(context, 12f, isNight));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = GlassDimensions.dpToPx(context, 6);
        btnWeatherAction.setLayoutParams(btnLp);
        btnWeatherAction.setVisibility(View.GONE);
        centerWeather.addView(btnWeatherAction);

        weatherContainer.addView(centerWeather);

        // Horizontal Hourly Timeline
        weatherScroll = new HorizontalScrollView(context);
        weatherScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        weatherScroll.setLayoutParams(scrollLp);

        hourlyTimelineRow = new LinearLayout(context);
        hourlyTimelineRow.setOrientation(LinearLayout.HORIZONTAL);
        hourlyTimelineRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherScroll.addView(hourlyTimelineRow);

        weatherContainer.addView(weatherScroll);
        addView(weatherContainer);
    }

    public void bindNavTurn(@Nullable NavTurnInfo info) {
        if (info != null && info.isActive()) {
            navContainer.setVisibility(View.VISIBLE);
            weatherContainer.setVisibility(View.GONE);

            navArrowView.setDirection(info.getDirection());
            tvNavDistance.setText(info.getDistanceText());
            tvNavInstruction.setText(info.getInstruction());
            tvNavEta.setText(info.getEta().isEmpty() ? "--:--" : "ETA " + info.getEta());
            tvNavRemaining.setText(info.getRemainingInfo());

            this.activeExitIntent = info.getExitIntent();
            btnNavExit.setVisibility(info.hasExitAction() ? View.VISIBLE : View.GONE);
        } else {
            navContainer.setVisibility(View.GONE);
            weatherContainer.setVisibility(View.VISIBLE);
        }
    }

    public boolean hasForecastData() {
        return lastForecast != null;
    }

    public void bindWeather(@Nullable GlassOpenMeteoClient.HourlyForecast forecast, boolean isMetric) {
        bindWeather(forecast, isMetric, null, false);
    }

    public void bindWeather(@Nullable GlassOpenMeteoClient.HourlyForecast forecast, boolean isMetric,
                            @Nullable String locationName, boolean isStale) {
        if (forecast == null) return;
        this.lastForecast = forecast;
        this.lastIsMetric = isMetric;
        this.lastLocationName = locationName;
        this.lastIsStale = isStale;

        btnWeatherAction.setVisibility(View.GONE);
        weatherScroll.setVisibility(View.VISIBLE);

        String unit = isMetric ? "°C" : "°F";
        tvWeatherTemp.setText(String.format(Locale.US, "%.0f%s", forecast.getCurrentTemp(), unit));
        String condition = GlassOpenMeteoClient.getWeatherConditionDescription(getContext(), forecast.getCurrentWeatherCode());
        if (isStale) {
            condition += safeGetString(getContext(), me.aap.fermata.R.string.weather_cached_suffix, " (Cached)");
        }
        tvWeatherCondition.setText(condition);
        String highLow = String.format(Locale.US, "H: %.0f°  L: %.0f°",
                forecast.getHighTemp(), forecast.getLowTemp());
        if (forecast.getTimestamp() > 0) {
            String updateTime = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new java.util.Date(forecast.getTimestamp()));
            String prefix = isStale ? safeGetString(getContext(), me.aap.fermata.R.string.weather_cached_prefix, "Cached ")
                    : safeGetString(getContext(), me.aap.fermata.R.string.weather_updated_prefix, "Updated ");
            highLow += " • " + prefix + updateTime;
        }
        tvWeatherHighLow.setText(highLow);
        if (locationName != null && !locationName.trim().isEmpty()) {
            tvWeatherLocation.setText(locationName.trim());
            tvWeatherLocation.setVisibility(View.VISIBLE);
        } else {
            tvWeatherLocation.setVisibility(View.GONE);
        }

        hourlyTimelineRow.removeAllViews();
        Context ctx = getContext();
        boolean isAuto = me.aap.fermata.BuildConfig.AUTO;
        int padH = GlassDimensions.dpToPx(ctx, 10);
        int padV = GlassDimensions.dpToPx(ctx, isAuto ? 4 : 6);
        for (GlassOpenMeteoClient.HourlyPoint point : forecast.getHourlyPoints()) {
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(padH, padV, padH, padV);
            item.setBackground(GlassDrawables.createGlassPillDrawable(ctx, 12f, isNight));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = GlassDimensions.dpToPx(ctx, 6);
            item.setLayoutParams(lp);

            TextView tvTime = new TextView(ctx);
            tvTime.setText(point.getTime());
            tvTime.setTextSize(9f);
            tvTime.setTextColor(colors.getTextSecondary());
            item.addView(tvTime);

            TextView tvT = new TextView(ctx);
            tvT.setText(String.format(Locale.US, "%.0f°", point.getTemp()));
            tvT.setTextSize(12f);
            tvT.setTypeface(Typeface.DEFAULT_BOLD);
            tvT.setTextColor(colors.getTextPrimary());
            item.addView(tvT);

            hourlyTimelineRow.addView(item);
        }
    }

    public void showPermissionNeeded(String message, Runnable onAction) {
        Context ctx = getContext();
        tvWeatherTemp.setText("--°");
        tvWeatherCondition.setText(safeGetString(ctx, R.string.glass_weather_perm_needed, "Permission Needed"));
        tvWeatherHighLow.setText(message);
        tvWeatherLocation.setVisibility(View.GONE);
        weatherScroll.setVisibility(View.GONE);

        if (onAction != null) {
            btnWeatherAction.setText(safeGetString(ctx, R.string.glass_weather_grant_btn, "Grant Permission"));
            btnWeatherAction.setVisibility(View.VISIBLE);
            btnWeatherAction.setOnClickListener(v -> onAction.run());
        } else {
            btnWeatherAction.setVisibility(View.GONE);
        }
    }

    public void showLocating(String message) {
        Context ctx = getContext();
        tvWeatherTemp.setText("--°");
        tvWeatherCondition.setText(safeGetString(ctx, R.string.glass_weather_locating, "Locating GPS..."));
        tvWeatherHighLow.setText(message);
        tvWeatherLocation.setVisibility(View.GONE);
        weatherScroll.setVisibility(View.GONE);
        btnWeatherAction.setVisibility(View.GONE);
    }

    public void showError(String message, Runnable onRetry) {
        tvWeatherTemp.setText("--°");
        tvWeatherCondition.setText(safeGetString(getContext(), R.string.glass_nav_weather_unavailable, "Weather Unavailable"));
        tvWeatherHighLow.setText(message);
        tvWeatherLocation.setVisibility(View.GONE);
        weatherScroll.setVisibility(View.GONE);

        btnWeatherAction.setText(safeGetString(getContext(), R.string.retry, "Retry"));
        btnWeatherAction.setVisibility(View.VISIBLE);
        btnWeatherAction.setOnClickListener(v -> {
            if (onRetry != null) onRetry.run();
        });
    }

    public void refreshAppearance() {
        Context context = getContext();
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);

        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));

        if (tvNavHeader != null) {
            tvNavHeader.setTextColor(colors.getTextSecondary());
        }
        if (tvWeatherHeader != null) {
            tvWeatherHeader.setTextColor(colors.getTextSecondary());
        }
        if (tvNavDistance != null) {
            tvNavDistance.setTextColor(colors.getActionText());
        }
        if (tvNavInstruction != null) {
            tvNavInstruction.setTextColor(colors.getTextPrimary());
        }
        if (tvNavEta != null) {
            tvNavEta.setTextColor(colors.getTextPrimary());
        }
        if (tvNavRemaining != null) {
            tvNavRemaining.setTextColor(colors.getTextSecondary());
        }
        if (btnNavExit != null) {
            btnNavExit.setTextColor(colors.getTextPrimary());
            btnNavExit.setBackground(GlassDrawables.createGlassPillDrawable(context, 14f, isNight));
        }
        if (navBottomBar != null) {
            navBottomBar.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        }
        if (navArrowView != null) {
            navArrowView.refreshAppearance(isNight, colors);
        }
        if (tvWeatherTemp != null) {
            tvWeatherTemp.setTextColor(colors.getTextPrimary());
        }
        if (tvWeatherCondition != null) {
            tvWeatherCondition.setTextColor(colors.getActionText());
        }
        if (tvWeatherHighLow != null) {
            tvWeatherHighLow.setTextColor(colors.getTextSecondary());
        }
        if (tvWeatherLocation != null) {
            tvWeatherLocation.setTextColor(colors.getTextSecondary());
        }
        if (btnWeatherAction != null) {
            btnWeatherAction.setTextColor(colors.getActionText());
            btnWeatherAction.setBackground(GlassDrawables.createGlassPillDrawable(context, 12f, isNight));
        }
        if (lastForecast != null) {
            bindWeather(lastForecast, lastIsMetric, lastLocationName, lastIsStale);
        }
        invalidate();
    }

    /**
     * Vector drawing for Turn Arrow directions.
     */
    private static class TurnArrowView extends View {
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrowPath = new Path();
        private NavTurnInfo.TurnDirection direction = NavTurnInfo.TurnDirection.STRAIGHT;

        public TurnArrowView(Context context, GlassThemeColors colors) {
            super(context);
            arrowPaint.setColor(GlassThemeColors.ACCENT_CYAN);
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(GlassDimensions.dpToPx(context, 5));
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void refreshAppearance(boolean isNight, GlassThemeColors colors) {
            arrowPaint.setColor(isNight ? GlassThemeColors.ACCENT_CYAN : colors.getActionText());
            invalidate();
        }

        public void setDirection(NavTurnInfo.TurnDirection direction) {
            this.direction = direction != null ? direction : NavTurnInfo.TurnDirection.STRAIGHT;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            arrowPath.reset();
            float cx = w / 2f;
            float cy = h / 2f;

            switch (direction) {
                case SLIGHT_LEFT:
                    arrowPath.moveTo(cx + w * 0.15f, cy + h * 0.3f);
                    arrowPath.lineTo(cx + w * 0.15f, cy + h * 0.05f);
                    arrowPath.lineTo(cx - w * 0.15f, cy - h * 0.25f);
                    // Arrow head
                    arrowPath.moveTo(cx - w * 0.05f, cy - h * 0.3f);
                    arrowPath.lineTo(cx - w * 0.15f, cy - h * 0.25f);
                    arrowPath.lineTo(cx - w * 0.2f, cy - h * 0.15f);
                    break;
                case TURN_LEFT:
                    arrowPath.moveTo(cx + w * 0.2f, cy + h * 0.3f);
                    arrowPath.lineTo(cx + w * 0.2f, cy);
                    arrowPath.lineTo(cx - w * 0.2f, cy);
                    // Arrow head
                    arrowPath.moveTo(cx - w * 0.05f, cy - h * 0.15f);
                    arrowPath.lineTo(cx - w * 0.2f, cy);
                    arrowPath.lineTo(cx - w * 0.05f, cy + h * 0.15f);
                    break;
                case SHARP_LEFT:
                    arrowPath.moveTo(cx, cy + h * 0.3f);
                    arrowPath.lineTo(cx, cy - h * 0.1f);
                    arrowPath.lineTo(cx - w * 0.25f, cy + h * 0.15f);
                    // Arrow head
                    arrowPath.moveTo(cx - w * 0.12f, cy + h * 0.15f);
                    arrowPath.lineTo(cx - w * 0.25f, cy + h * 0.15f);
                    arrowPath.lineTo(cx - w * 0.22f, cy + h * 0.02f);
                    break;
                case SLIGHT_RIGHT:
                    arrowPath.moveTo(cx - w * 0.15f, cy + h * 0.3f);
                    arrowPath.lineTo(cx - w * 0.15f, cy + h * 0.05f);
                    arrowPath.lineTo(cx + w * 0.15f, cy - h * 0.25f);
                    // Arrow head
                    arrowPath.moveTo(cx + w * 0.05f, cy - h * 0.3f);
                    arrowPath.lineTo(cx + w * 0.15f, cy - h * 0.25f);
                    arrowPath.lineTo(cx + w * 0.2f, cy - h * 0.15f);
                    break;
                case TURN_RIGHT:
                    arrowPath.moveTo(cx - w * 0.2f, cy + h * 0.3f);
                    arrowPath.lineTo(cx - w * 0.2f, cy);
                    arrowPath.lineTo(cx + w * 0.2f, cy);
                    // Arrow head
                    arrowPath.moveTo(cx + w * 0.05f, cy - h * 0.15f);
                    arrowPath.lineTo(cx + w * 0.2f, cy);
                    arrowPath.lineTo(cx + w * 0.05f, cy + h * 0.15f);
                    break;
                case SHARP_RIGHT:
                    arrowPath.moveTo(cx, cy + h * 0.3f);
                    arrowPath.lineTo(cx, cy - h * 0.1f);
                    arrowPath.lineTo(cx + w * 0.25f, cy + h * 0.15f);
                    // Arrow head
                    arrowPath.moveTo(cx + w * 0.12f, cy + h * 0.15f);
                    arrowPath.lineTo(cx + w * 0.25f, cy + h * 0.15f);
                    arrowPath.lineTo(cx + w * 0.22f, cy + h * 0.02f);
                    break;
                case U_TURN:
                    arrowPath.moveTo(cx - w * 0.15f, cy + h * 0.3f);
                    arrowPath.lineTo(cx - w * 0.15f, cy - h * 0.1f);
                    arrowPath.arcTo(cx - w * 0.15f, cy - h * 0.25f, cx + w * 0.15f, cy + h * 0.05f, 180f, 180f, false);
                    arrowPath.lineTo(cx + w * 0.15f, cy + h * 0.3f);
                    // Arrow head
                    arrowPath.moveTo(cx + w * 0.05f, cy + h * 0.15f);
                    arrowPath.lineTo(cx + w * 0.15f, cy + h * 0.3f);
                    arrowPath.lineTo(cx + w * 0.25f, cy + h * 0.15f);
                    break;
                case ROUNDABOUT:
                    arrowPath.addCircle(cx, cy, w * 0.25f, Path.Direction.CW);
                    break;
                case DESTINATION:
                    arrowPath.addCircle(cx, cy - h * 0.08f, w * 0.18f, Path.Direction.CW);
                    arrowPath.moveTo(cx, cy + h * 0.1f);
                    arrowPath.lineTo(cx, cy + h * 0.3f);
                    break;
                case STRAIGHT:
                default:
                    arrowPath.moveTo(cx, cy + h * 0.3f);
                    arrowPath.lineTo(cx, cy - h * 0.3f);
                    arrowPath.moveTo(cx - w * 0.15f, cy - h * 0.15f);
                    arrowPath.lineTo(cx, cy - h * 0.3f);
                    arrowPath.lineTo(cx + w * 0.15f, cy - h * 0.15f);
                    break;
            }

            canvas.drawPath(arrowPath, arrowPaint);
        }
    }

    private static String safeGetString(Context context, int resId, String fallback) {
        if (context == null) return fallback;
        try {
            return context.getString(resId);
        } catch (Throwable e) {
            return fallback;
        }
    }
}
