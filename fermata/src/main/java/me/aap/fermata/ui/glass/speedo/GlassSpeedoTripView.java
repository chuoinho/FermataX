package me.aap.fermata.ui.glass.speedo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Automotive frosted glass card displaying GPS speedometer and Trip Computer statistics.
 */
public class GlassSpeedoTripView extends FrameLayout {

    private final boolean isNight;
    private final GlassThemeColors colors;

    private TextView tvSpeedValue;
    private TextView tvSpeedUnit;
    private TextView tvSpeedLimit;
    private FrameLayout speedLimitContainer;

    private TextView tvTripDistance;
    private TextView tvAvgSpeed;
    private TextView tvDriveTime;
    private LinearLayout tripStatsRow;

    private SpeedArcView speedArcView;

    private boolean isMetric = true;
    private int speedLimit = 0;
    private Runnable onResetTripListener;

    public GlassSpeedoTripView(@NonNull Context context) {
        this(context, null);
    }

    public GlassSpeedoTripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassSpeedoTripView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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

        speedArcView = new SpeedArcView(context, isNight, colors);
        LayoutParams arcLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(speedArcView, arcLp);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams contentLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(contentLayout, contentLp);

        // Top bar inside card: Title + Speed Limit Badge
        FrameLayout topBar = new FrameLayout(context);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(topBarLp);

        TextView tvCardTitle = new TextView(context);
        tvCardTitle.setText("SPEED & TRIP");
        tvCardTitle.setTextSize(11f);
        tvCardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvCardTitle.setTextColor(colors.getTextSecondary());
        tvCardTitle.setLetterSpacing(0.08f);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
        topBar.addView(tvCardTitle, titleLp);

        // Speed limit circle badge (standard EU/international sign)
        speedLimitContainer = new FrameLayout(context);
        int badgeSize = GlassDimensions.dpToPx(context, 34);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.END | Gravity.CENTER_VERTICAL);
        speedLimitContainer.setLayoutParams(badgeLp);
        speedLimitContainer.setBackground(GlassDrawables.createGlassPillDrawable(context, 17f, isNight));

        tvSpeedLimit = new TextView(context);
        tvSpeedLimit.setGravity(Gravity.CENTER);
        tvSpeedLimit.setTextSize(13f);
        tvSpeedLimit.setTypeface(Typeface.DEFAULT_BOLD);
        tvSpeedLimit.setTextColor(Color.RED);
        tvSpeedLimit.setText("--");
        speedLimitContainer.addView(tvSpeedLimit, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        speedLimitContainer.setVisibility(View.GONE); // Default hidden until speed limit set
        topBar.addView(speedLimitContainer);

        contentLayout.addView(topBar);

        // Central Speedometer Section
        LinearLayout speedCenter = new LinearLayout(context);
        speedCenter.setOrientation(LinearLayout.VERTICAL);
        speedCenter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        speedCenter.setLayoutParams(centerLp);

        tvSpeedValue = new TextView(context);
        tvSpeedValue.setText("0");
        tvSpeedValue.setTextSize(54f);
        tvSpeedValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSpeedValue.setTextColor(colors.getTextPrimary());
        tvSpeedValue.setGravity(Gravity.CENTER);
        speedCenter.addView(tvSpeedValue);

        tvSpeedUnit = new TextView(context);
        tvSpeedUnit.setText("KM/H");
        tvSpeedUnit.setTextSize(12f);
        tvSpeedUnit.setTypeface(Typeface.DEFAULT_BOLD);
        tvSpeedUnit.setTextColor(GlassThemeColors.ACCENT_CYAN);
        tvSpeedUnit.setLetterSpacing(0.12f);
        tvSpeedUnit.setGravity(Gravity.CENTER);
        speedCenter.addView(tvSpeedUnit);

        contentLayout.addView(speedCenter);

        // Bottom Trip Computer Section
        tripStatsRow = new LinearLayout(context);
        tripStatsRow.setOrientation(LinearLayout.HORIZONTAL);
        tripStatsRow.setGravity(Gravity.CENTER);
        int tripRowPadding = GlassDimensions.dpToPx(context, 8);
        tripStatsRow.setPadding(tripRowPadding, tripRowPadding, tripRowPadding, tripRowPadding);
        tripStatsRow.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));

        LinearLayout.LayoutParams tripLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tripStatsRow.setLayoutParams(tripLp);

        tvTripDistance = createStatItem(context, tripStatsRow, "DISTANCE", "0.0 km");
        addStatDivider(context, tripStatsRow);
        tvAvgSpeed = createStatItem(context, tripStatsRow, "AVG SPEED", "0 km/h");
        addStatDivider(context, tripStatsRow);
        tvDriveTime = createStatItem(context, tripStatsRow, "TIME", "00h 00m");

        // Long-press on trip stats triggers reset
        tripStatsRow.setOnLongClickListener(v -> {
            if (onResetTripListener != null) {
                onResetTripListener.run();
                return true;
            }
            return false;
        });

        contentLayout.addView(tripStatsRow);
    }

    private TextView createStatItem(Context context, LinearLayout parent, String label, String initialValue) {
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        itemLayout.setLayoutParams(lp);

        TextView tvVal = new TextView(context);
        tvVal.setText(initialValue);
        tvVal.setTextSize(13f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setTextColor(colors.getTextPrimary());
        tvVal.setGravity(Gravity.CENTER);
        itemLayout.addView(tvVal);

        TextView tvLbl = new TextView(context);
        tvLbl.setText(label);
        tvLbl.setTextSize(9f);
        tvLbl.setTextColor(colors.getTextSecondary());
        tvLbl.setGravity(Gravity.CENTER);
        tvLbl.setLetterSpacing(0.05f);
        itemLayout.addView(tvLbl);

        parent.addView(itemLayout);
        return tvVal;
    }

    private void addStatDivider(Context context, LinearLayout parent) {
        View divider = new View(context);
        int width = GlassDimensions.dpToPx(context, 1);
        int height = GlassDimensions.dpToPx(context, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.gravity = Gravity.CENTER_VERTICAL;
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(colors.getCardBorder());
        parent.addView(divider);
    }

    public void updateSpeed(float speedKmh, GlassSpeedoTripController.TripState tripState, boolean isMetric) {
        this.isMetric = isMetric;
        float displayedSpeed = isMetric ? speedKmh : (speedKmh * 0.621371f);
        tvSpeedValue.setText(String.format(Locale.US, "%.0f", displayedSpeed));
        tvSpeedUnit.setText(isMetric ? "KM/H" : "MPH");

        speedArcView.setSpeedRatio(Math.min(1.0f, speedKmh / 220f));

        if (tripState != null) {
            if (isMetric) {
                tvTripDistance.setText(String.format(Locale.US, "%.1f km", tripState.getDistanceKm()));
                tvAvgSpeed.setText(String.format(Locale.US, "%.0f km/h", tripState.getAverageSpeedKmh()));
            } else {
                tvTripDistance.setText(String.format(Locale.US, "%.1f mi", tripState.getDistanceMiles()));
                tvAvgSpeed.setText(String.format(Locale.US, "%.0f mph", tripState.getAverageSpeedMph()));
            }
            tvDriveTime.setText(tripState.getFormattedDriveTime());
        }
    }

    public void setSpeedLimit(int limitKmh) {
        this.speedLimit = limitKmh;
        if (limitKmh > 0) {
            speedLimitContainer.setVisibility(View.VISIBLE);
            int displayLimit = isMetric ? limitKmh : Math.round(limitKmh * 0.621371f);
            tvSpeedLimit.setText(String.valueOf(displayLimit));
        } else {
            speedLimitContainer.setVisibility(View.GONE);
        }
    }

    public void setOnResetTripListener(Runnable listener) {
        this.onResetTripListener = listener;
    }

    /**
     * Radial glow arc behind the speedometer digits.
     */
    private static class SpeedArcView extends View {
        private final Paint arcTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcRect = new RectF();
        private float speedRatio = 0f;

        public SpeedArcView(Context context, boolean isNight, GlassThemeColors colors) {
            super(context);
            arcTrackPaint.setStyle(Paint.Style.STROKE);
            arcTrackPaint.setStrokeWidth(GlassDimensions.dpToPx(context, 4));
            arcTrackPaint.setColor(isNight ? 0x22FFFFFF : 0x1A000000);
            arcTrackPaint.setStrokeCap(Paint.Cap.ROUND);

            arcProgressPaint.setStyle(Paint.Style.STROKE);
            arcProgressPaint.setStrokeWidth(GlassDimensions.dpToPx(context, 5));
            arcProgressPaint.setColor(GlassThemeColors.ACCENT_CYAN);
            arcProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setSpeedRatio(float ratio) {
            this.speedRatio = Math.max(0f, Math.min(1f, ratio));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float size = Math.min(w, h) * 0.75f;
            float cx = w / 2f;
            float cy = h * 0.44f;
            arcRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);

            // Draw 240-degree arc from 150 deg to 390 deg
            canvas.drawArc(arcRect, 150f, 240f, false, arcTrackPaint);
            if (speedRatio > 0f) {
                canvas.drawArc(arcRect, 150f, 240f * speedRatio, false, arcProgressPaint);
            }
        }
    }
}
