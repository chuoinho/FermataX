package me.aap.fermata.ui.glass.speedo;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.glass.dialog.GlassDialogBuilder;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Automotive frosted glass card displaying GPS speedometer and Trip Computer statistics.
 */
public class GlassSpeedoTripView extends FrameLayout {

    private boolean isNight;
    private GlassThemeColors colors;

    private TextView tvSpeedValue;
    private TextView tvSpeedUnit;
    private TextView tvCardTitle;
    private TextView tvSpeedLimit;
    private FrameLayout speedLimitContainer;
    private ImageView btnResetTrip;

    private TextView tvTripDistance;
    private TextView tvAvgSpeed;
    private TextView tvDriveTime;
    private LinearLayout tripStatsRow;
    private final java.util.List<TextView> statLabels = new java.util.ArrayList<>();
    private final java.util.List<View> statDividers = new java.util.ArrayList<>();

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
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);
        initView(context);
    }

    private void initView(Context context) {
        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
        int padH = GlassDimensions.dpToPx(context, 12);
        int padV = GlassDimensions.dpToPx(context, 10);
        setPadding(padH, padV, padH, padV);

        speedArcView = new SpeedArcView(context, isNight, colors);
        LayoutParams arcLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(speedArcView, arcLp);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams contentLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(contentLayout, contentLp);

        // Top bar inside card: Title + Reset Button + Speed Limit Badge
        FrameLayout topBar = new FrameLayout(context);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(topBarLp);

        tvCardTitle = new TextView(context);
        tvCardTitle.setText(safeGetString(context, R.string.glass_speedo_card_title, "SPEED & TRIP"));
        tvCardTitle.setTextSize(11f);
        tvCardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvCardTitle.setTextColor(colors.getTextSecondary());
        tvCardTitle.setLetterSpacing(0.08f);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
        topBar.addView(tvCardTitle, titleLp);

        // Top-end actions: Reset button + Speed Limit Container
        LinearLayout topEndLayout = new LinearLayout(context);
        topEndLayout.setOrientation(LinearLayout.HORIZONTAL);
        topEndLayout.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topEndLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        topBar.addView(topEndLayout, topEndLp);

        // Explicit reset trip button (R12)
        btnResetTrip = new ImageView(context);
        int resetSize = GlassDimensions.dpToPx(context, 26);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(resetSize, resetSize);
        resetLp.setMarginEnd(GlassDimensions.dpToPx(context, 4));
        btnResetTrip.setLayoutParams(resetLp);
        btnResetTrip.setImageResource(R.drawable.refresh);
        btnResetTrip.setColorFilter(colors.getTextSecondary());
        int pad = GlassDimensions.dpToPx(context, 5);
        btnResetTrip.setPadding(pad, pad, pad, pad);
        btnResetTrip.setBackground(GlassDrawables.createGlassPillStateDrawable(context, 13f, isNight));
        String resetDesc = "Reset Trip";
        try {
            resetDesc = context.getString(R.string.speedo_reset_trip);
        } catch (Exception ignored) {}
        btnResetTrip.setContentDescription(resetDesc);
        btnResetTrip.setFocusable(true);
        btnResetTrip.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnResetTrip.setTooltipText(resetDesc);
        }
        btnResetTrip.setOnClickListener(v -> confirmResetTrip(context));
        topEndLayout.addView(btnResetTrip);

        // Speed limit circle badge (standard EU/international sign)
        speedLimitContainer = new FrameLayout(context);
        int badgeSize = GlassDimensions.dpToPx(context, 34);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
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
        topEndLayout.addView(speedLimitContainer);

        contentLayout.addView(topBar);

        // Central Speedometer Section
        LinearLayout speedCenter = new LinearLayout(context);
        speedCenter.setOrientation(LinearLayout.VERTICAL);
        speedCenter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        speedCenter.setLayoutParams(centerLp);

        tvSpeedValue = new TextView(context);
        tvSpeedValue.setText("--");
        tvSpeedValue.setTextSize(54f);
        tvSpeedValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSpeedValue.setTextColor(colors.getTextPrimary());
        tvSpeedValue.setGravity(Gravity.CENTER);
        speedCenter.addView(tvSpeedValue);

        tvSpeedUnit = new TextView(context);
        tvSpeedUnit.setText("KM/H");
        tvSpeedUnit.setTextSize(12f);
        tvSpeedUnit.setTypeface(Typeface.DEFAULT_BOLD);
        tvSpeedUnit.setTextColor(colors.getActionText());
        tvSpeedUnit.setLetterSpacing(0.12f);
        tvSpeedUnit.setGravity(Gravity.CENTER);
        speedCenter.addView(tvSpeedUnit);

        contentLayout.addView(speedCenter);

        // Bottom Trip Computer Section
        tripStatsRow = new LinearLayout(context);
        tripStatsRow.setOrientation(LinearLayout.HORIZONTAL);
        tripStatsRow.setGravity(Gravity.CENTER);
        int tripPadH = GlassDimensions.dpToPx(context, 6);
        boolean isCar = GlassDimensions.isCar(context);
        int tripPadV = GlassDimensions.dpToPx(context, isCar ? 6 : 4);
        tripStatsRow.setPadding(tripPadH, tripPadV, tripPadH, tripPadV);
        tripStatsRow.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));

        LinearLayout.LayoutParams tripLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tripStatsRow.setLayoutParams(tripLp);

        String distLabel = safeGetString(context, R.string.glass_speedo_trip_dist, "DISTANCE");
        String avgLabel = safeGetString(context, R.string.glass_speedo_trip_avg, "AVG SPEED");
        String timeLabel = safeGetString(context, R.string.glass_speedo_trip_time, "TIME");

        tvTripDistance = createStatItem(context, tripStatsRow, distLabel, "0.0 km", isCar);
        addStatDivider(context, tripStatsRow);
        tvAvgSpeed = createStatItem(context, tripStatsRow, avgLabel, "0 km/h", isCar);
        addStatDivider(context, tripStatsRow);
        tvDriveTime = createStatItem(context, tripStatsRow, timeLabel, "00h 00m", isCar);

        // Click or long-press on trip stats triggers reset with confirmation (R12)
        tripStatsRow.setOnClickListener(v -> confirmResetTrip(context));
        tripStatsRow.setOnLongClickListener(v -> {
            confirmResetTrip(context);
            return true;
        });

        contentLayout.addView(tripStatsRow);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            float density = getResources().getDisplayMetrics().density;
            float hDp = h / Math.max(0.1f, density);
            float wDp = w / Math.max(0.1f, density);
            if (hDp < 220f || wDp < 160f) {
                tvSpeedValue.setTextSize(36f);
            } else if (hDp < 260f || wDp < 190f) {
                tvSpeedValue.setTextSize(42f);
            } else {
                tvSpeedValue.setTextSize(48f);
            }
        }
    }

    private TextView createStatItem(Context context, LinearLayout parent, String label, String initialValue, boolean isCar) {
        LinearLayout itemLayout = new LinearLayout(context);
        itemLayout.setOrientation(LinearLayout.VERTICAL);
        itemLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        itemLayout.setLayoutParams(lp);

        TextView tvVal = new TextView(context);
        tvVal.setText(initialValue);
        tvVal.setTextSize(isCar ? 14f : 12f);
        tvVal.setTypeface(Typeface.DEFAULT_BOLD);
        tvVal.setTextColor(colors.getTextPrimary());
        tvVal.setGravity(Gravity.CENTER);
        tvVal.setSingleLine(true);
        itemLayout.addView(tvVal);

        TextView tvLbl = new TextView(context);
        tvLbl.setText(label);
        tvLbl.setTextSize(isCar ? 11.5f : 9.5f);
        tvLbl.setTextColor(colors.getTextSecondary());
        tvLbl.setGravity(Gravity.CENTER);
        tvLbl.setLetterSpacing(0f);
        tvLbl.setSingleLine(true);
        tvLbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
        itemLayout.addView(tvLbl);
        statLabels.add(tvLbl);

        parent.addView(itemLayout);
        return tvVal;
    }

    private static String safeGetString(Context context, int resId, String fallback) {
        if (context == null) return fallback;
        try {
            return context.getString(resId);
        } catch (Throwable ignored) {
            return fallback;
        }
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
        statDividers.add(divider);
    }

    public void refreshAppearance() {
        Context context = getContext();
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);

        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));

        if (tvCardTitle != null) {
            tvCardTitle.setTextColor(colors.getTextSecondary());
        }
        if (btnResetTrip != null) {
            btnResetTrip.setColorFilter(colors.getTextSecondary());
            btnResetTrip.setBackground(GlassDrawables.createGlassPillStateDrawable(context, 13f, isNight));
        }
        if (speedLimitContainer != null) {
            speedLimitContainer.setBackground(GlassDrawables.createGlassPillDrawable(context, 17f, isNight));
        }
        if (tvSpeedValue != null) {
            tvSpeedValue.setTextColor(colors.getTextPrimary());
        }
        if (tvSpeedUnit != null) {
            tvSpeedUnit.setTextColor(colors.getActionText());
        }
        if (tripStatsRow != null) {
            tripStatsRow.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        }
        if (tvTripDistance != null) {
            tvTripDistance.setTextColor(colors.getTextPrimary());
        }
        if (tvAvgSpeed != null) {
            tvAvgSpeed.setTextColor(colors.getTextPrimary());
        }
        if (tvDriveTime != null) {
            tvDriveTime.setTextColor(colors.getTextPrimary());
        }
        for (TextView lbl : statLabels) {
            lbl.setTextColor(colors.getTextSecondary());
        }
        for (View div : statDividers) {
            div.setBackgroundColor(colors.getCardBorder());
        }
        if (speedArcView != null) {
            speedArcView.refreshAppearance(isNight, colors);
        }
        invalidate();
    }

    public void updateSpeed(float speedKmh, GlassSpeedoTripController.TripState tripState, boolean isMetric) {
        this.isMetric = isMetric;
        if (speedKmh < 0f) {
            tvSpeedValue.setText("--");
            speedArcView.setSpeedRatio(0f);
        } else {
            float displayedSpeed = isMetric ? speedKmh : (speedKmh * 0.621371f);
            tvSpeedValue.setText(String.format(Locale.US, "%.0f", displayedSpeed));
            speedArcView.setSpeedRatio(Math.min(1.0f, speedKmh / 220f));
        }
        tvSpeedUnit.setText(isMetric ? "KM/H" : "MPH");

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

    private void confirmResetTrip(Context context) {
        if (onResetTripListener == null) return;
        Context actCtx = context;
        while (actCtx instanceof ContextWrapper && !(actCtx instanceof Activity)) {
            actCtx = ((ContextWrapper) actCtx).getBaseContext();
        }
        if (actCtx instanceof Activity && !((Activity) actCtx).isFinishing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(actCtx);
            try {
                builder.setTitle(R.string.speedo_reset_trip)
                        .setMessage(R.string.speedo_reset_trip_confirm);
            } catch (Exception ignored) {
                builder.setTitle("Reset Trip")
                        .setMessage("Reset trip distance, average speed, and driving time?");
            }
            AlertDialog dialog = builder
                    .setPositiveButton(android.R.string.ok, (d, which) -> onResetTripListener.run())
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            GlassDialogBuilder.styleAlertDialog(dialog, actCtx, null);
            dialog.show();
        } else {
            onResetTripListener.run();
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
            arcProgressPaint.setColor(colors.getActionText());
            arcProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setSpeedRatio(float ratio) {
            this.speedRatio = Math.max(0f, Math.min(1f, ratio));
            invalidate();
        }

        public void refreshAppearance(boolean isNight, GlassThemeColors colors) {
            arcTrackPaint.setColor(isNight ? 0x22FFFFFF : 0x1A000000);
            arcProgressPaint.setColor(colors.getActionText());
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            int padTop = GlassDimensions.dpToPx(getContext(), 38);
            int padBottom = GlassDimensions.dpToPx(getContext(), 46);
            float availH = Math.max(10, h - padTop - padBottom);
            float availW = Math.max(10, w - GlassDimensions.dpToPx(getContext(), 20));
            float diameter = Math.min(availW, availH * 1.35f);
            float cx = w / 2f;
            float cy = padTop + availH * 0.50f;
            float r = diameter / 2f;
            arcRect.set(cx - r, cy - r, cx + r, cy + r);

            // Draw 240-degree arc from 150 deg to 390 deg
            canvas.drawArc(arcRect, 150f, 240f, false, arcTrackPaint);
            if (speedRatio > 0f) {
                canvas.drawArc(arcRect, 150f, 240f * speedRatio, false, arcProgressPaint);
            }
        }
    }
}
