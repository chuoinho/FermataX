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

    private final boolean isNight;
    private final GlassThemeColors colors;

    // Nav container
    private LinearLayout navContainer;
    private TurnArrowView navArrowView;
    private TextView tvNavInstruction;
    private TextView tvNavDistance;
    private TextView tvNavEta;
    private TextView tvNavRemaining;
    private TextView btnNavExit;
    private PendingIntent activeExitIntent;

    // Weather container
    private LinearLayout weatherContainer;
    private TextView tvWeatherTemp;
    private TextView tvWeatherCondition;
    private TextView tvWeatherHighLow;
    private LinearLayout hourlyTimelineRow;

    public GlassNavWeatherView(@NonNull Context context) {
        this(context, null);
    }

    public GlassNavWeatherView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassNavWeatherView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int uiMode = getResources().getConfiguration().uiMode;
        this.isNight = (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_NO;
        this.colors = GlassThemeColors.get(context, isNight);
        initView(context);
    }

    private void initView(Context context) {
        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
        int padding = GlassDimensions.dpToPx(context, 16);
        setPadding(padding, padding, padding, padding);

        initNavLayout(context);
        initWeatherLayout(context);

        // Default to weather fallback view
        navContainer.setVisibility(View.GONE);
        weatherContainer.setVisibility(View.VISIBLE);
    }

    private void initNavLayout(Context context) {
        navContainer = new LinearLayout(context);
        navContainer.setOrientation(LinearLayout.VERTICAL);
        navContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        navContainer.setLayoutParams(lp);

        // Header
        TextView tvHeader = new TextView(context);
        tvHeader.setText("NAVIGATION");
        tvHeader.setTextSize(11f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(colors.getTextSecondary());
        tvHeader.setLetterSpacing(0.08f);
        navContainer.addView(tvHeader);

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
        tvNavDistance.setTextColor(GlassThemeColors.ACCENT_CYAN);
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
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        int barPadding = GlassDimensions.dpToPx(context, 8);
        bottomBar.setPadding(barPadding, barPadding, barPadding, barPadding);
        bottomBar.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomBar.setLayoutParams(botLp);

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

        bottomBar.addView(etaLayout);

        btnNavExit = new TextView(context);
        btnNavExit.setText("EXIT");
        btnNavExit.setTextSize(11f);
        btnNavExit.setTypeface(Typeface.DEFAULT_BOLD);
        btnNavExit.setTextColor(Color.WHITE);
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
        bottomBar.addView(btnNavExit);

        navContainer.addView(bottomBar);
        addView(navContainer);
    }

    private void initWeatherLayout(Context context) {
        weatherContainer = new LinearLayout(context);
        weatherContainer.setOrientation(LinearLayout.VERTICAL);
        weatherContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        weatherContainer.setLayoutParams(lp);

        // Header
        TextView tvHeader = new TextView(context);
        tvHeader.setText("WEATHER & FORECAST");
        tvHeader.setTextSize(11f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(colors.getTextSecondary());
        tvHeader.setLetterSpacing(0.08f);
        weatherContainer.addView(tvHeader);

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
        tvWeatherCondition.setText("Updating...");
        tvWeatherCondition.setTextSize(14f);
        tvWeatherCondition.setTypeface(Typeface.DEFAULT_BOLD);
        tvWeatherCondition.setTextColor(GlassThemeColors.ACCENT_CYAN);
        tvWeatherCondition.setGravity(Gravity.CENTER);
        centerWeather.addView(tvWeatherCondition);

        tvWeatherHighLow = new TextView(context);
        tvWeatherHighLow.setText("");
        tvWeatherHighLow.setTextSize(11f);
        tvWeatherHighLow.setTextColor(colors.getTextSecondary());
        tvWeatherHighLow.setGravity(Gravity.CENTER);
        centerWeather.addView(tvWeatherHighLow);

        weatherContainer.addView(centerWeather);

        // Horizontal Hourly Timeline
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scroll.setLayoutParams(scrollLp);

        hourlyTimelineRow = new LinearLayout(context);
        hourlyTimelineRow.setOrientation(LinearLayout.HORIZONTAL);
        hourlyTimelineRow.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(hourlyTimelineRow);

        weatherContainer.addView(scroll);
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

    public void bindWeather(@Nullable GlassOpenMeteoClient.HourlyForecast forecast, boolean isMetric) {
        if (forecast == null) return;

        String unit = isMetric ? "°C" : "°F";
        tvWeatherTemp.setText(String.format(Locale.US, "%.0f%s", forecast.getCurrentTemp(), unit));
        tvWeatherCondition.setText(GlassOpenMeteoClient.getWeatherConditionDescription(forecast.getCurrentWeatherCode()));
        tvWeatherHighLow.setText(String.format(Locale.US, "H: %.0f°  L: %.0f°",
                forecast.getHighTemp(), forecast.getLowTemp()));

        hourlyTimelineRow.removeAllViews();
        Context ctx = getContext();
        for (GlassOpenMeteoClient.HourlyPoint point : forecast.getHourlyPoints()) {
            LinearLayout item = new LinearLayout(ctx);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            int padH = GlassDimensions.dpToPx(ctx, 10);
            int padV = GlassDimensions.dpToPx(ctx, 6);
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
                case TURN_LEFT:
                case SLIGHT_LEFT:
                case SHARP_LEFT:
                    arrowPath.moveTo(cx + w * 0.2f, cy + h * 0.3f);
                    arrowPath.lineTo(cx + w * 0.2f, cy);
                    arrowPath.lineTo(cx - w * 0.2f, cy);
                    // Arrow head
                    arrowPath.moveTo(cx - w * 0.05f, cy - h * 0.15f);
                    arrowPath.lineTo(cx - w * 0.2f, cy);
                    arrowPath.lineTo(cx - w * 0.05f, cy + h * 0.15f);
                    break;
                case TURN_RIGHT:
                case SLIGHT_RIGHT:
                case SHARP_RIGHT:
                    arrowPath.moveTo(cx - w * 0.2f, cy + h * 0.3f);
                    arrowPath.lineTo(cx - w * 0.2f, cy);
                    arrowPath.lineTo(cx + w * 0.2f, cy);
                    // Arrow head
                    arrowPath.moveTo(cx + w * 0.05f, cy - h * 0.15f);
                    arrowPath.lineTo(cx + w * 0.2f, cy);
                    arrowPath.lineTo(cx + w * 0.05f, cy + h * 0.15f);
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
}
