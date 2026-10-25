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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
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
    private TextView tvSignal;
    private TextView btnToggleTrip;
    private TripEngine.State lastTripState;

    private TextView tvTripDistance;
    private TextView tvAvgSpeed;
    private TextView tvDriveTime;
    private LinearLayout tripStatsRow;
    private TextView tvTripHint;
    private final java.util.List<TextView> statLabels = new java.util.ArrayList<>();
    private final java.util.List<View> statDividers = new java.util.ArrayList<>();

    private SpeedArcView speedArcView;

    private boolean isMetric = true;
    private final SpeedDisplay speedDisplay = new SpeedDisplay();
    private Runnable onResetTripListener;
    private Runnable onToggleTripListener;

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
        tvCardTitle.setSingleLine(true);
        tvCardTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
        boolean isCar = GlassDimensions.isCar(context);
        titleLp.setMarginEnd(GlassDimensions.dpToPx(context, isCar ? 120 : 90));
        topBar.addView(tvCardTitle, titleLp);

        LinearLayout topActions = new LinearLayout(context);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams actionsLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        topActions.setLayoutParams(actionsLp);

        btnToggleTrip = new TextView(context);
        int minH = GlassDimensions.dpToPx(context, isCar ? 48 : 36);
        btnToggleTrip.setMinHeight(minH);
        btnToggleTrip.setMinWidth(minH);
        btnToggleTrip.setGravity(Gravity.CENTER);
        btnToggleTrip.setTextSize(10f);
        btnToggleTrip.setTypeface(Typeface.DEFAULT_BOLD);
        btnToggleTrip.setTextColor(colors.getTextPrimary());
        int padHBtn = GlassDimensions.dpToPx(context, 10);
        btnToggleTrip.setPadding(padHBtn, 0, padHBtn, 0);
        btnToggleTrip.setBackground(GlassDrawables.createGlassPillDrawable(context, 14f, isNight));
        btnToggleTrip.setText(context.getString(R.string.speedo_pause_trip));
        btnToggleTrip.setContentDescription(context.getString(R.string.speedo_pause_trip));
        btnToggleTrip.setClickable(true);
        btnToggleTrip.setFocusable(true);
        btnToggleTrip.setOnClickListener(v -> {
            if (onToggleTripListener != null) {
                onToggleTripListener.run();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, minH);
        btnLp.setMarginEnd(GlassDimensions.dpToPx(context, 6));
        topActions.addView(btnToggleTrip, btnLp);

        topBar.addView(topActions);
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

        tvSignal = new TextView(context);
        tvSignal.setTextSize(10f);
        tvSignal.setTextColor(colors.getTextSecondary());
        tvSignal.setGravity(Gravity.CENTER);
        tvSignal.setMaxLines(1);
        tvSignal.setEllipsize(TextUtils.TruncateAt.END);
        tvSignal.setText("◌ " + context.getString(R.string.speedo_waiting));
        speedCenter.addView(tvSignal);

        contentLayout.addView(speedCenter);

        // Bottom Trip Computer Section
        tripStatsRow = new LinearLayout(context);
        tripStatsRow.setOrientation(LinearLayout.HORIZONTAL);
        tripStatsRow.setGravity(Gravity.CENTER);
        int tripPadH = GlassDimensions.dpToPx(context, 6);
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

        // Long-press or click on trip stats triggers reset dialog (R12)
        tripStatsRow.setClickable(true);
        tripStatsRow.setFocusable(true);
        tripStatsRow.setOnClickListener(v -> confirmResetTrip(context));
        tripStatsRow.setOnLongClickListener(v -> {
            confirmResetTrip(context);
            return true;
        });
        String resetTip = safeGetString(context, R.string.speedo_reset_trip, "Reset Trip");
        tripStatsRow.setTooltipText(resetTip);

        contentLayout.addView(tripStatsRow);

        // Replaces trip totals while background location is not available.
        tvTripHint = new TextView(context);
        tvTripHint.setTextSize(11f);
        tvTripHint.setTextColor(colors.getTextSecondary());
        tvTripHint.setGravity(Gravity.CENTER);
        tvTripHint.setPadding(tripPadH, tripPadV, tripPadH, tripPadV);
        tvTripHint.setVisibility(View.GONE);
        contentLayout.addView(tvTripHint);
    }

    /** Trip statistics need background location; otherwise the card shows speed only. */
    public void setTripMode(@NonNull TripModePolicy.Mode mode) {
        boolean on = mode == TripModePolicy.Mode.ON;
        tripStatsRow.setVisibility(on ? View.VISIBLE : View.GONE);
        btnToggleTrip.setVisibility(on ? View.VISIBLE : View.GONE);
        boolean hint = mode == TripModePolicy.Mode.NEEDS_BACKGROUND_PERMISSION;
        tvTripHint.setVisibility(hint ? View.VISIBLE : View.GONE);
        if (hint) tvTripHint.setText(R.string.location_speed_hint);
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
        tvSignal.setTextColor(colors.getTextSecondary());
        if (tvTripHint != null) tvTripHint.setTextColor(colors.getTextSecondary());
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
        if (btnToggleTrip != null) {
            btnToggleTrip.setBackground(GlassDrawables.createGlassPillDrawable(context, 12f, isNight));
            btnToggleTrip.setTextColor(colors.getActionText());
        }
        invalidate();
    }

    public void updateSpeed(float speedKmh, TripEngine.State tripState, boolean isMetric) {
        this.isMetric = isMetric;
        this.lastTripState = tripState;
        Locale locale = displayLocale();
        TripEngine.Signal signal = (tripState != null) ? tripState.signal() : TripEngine.Signal.WAITING;
        // A dash only when there is nothing to measure with; otherwise the number stays and the
        // signal line below reports a weak signal.
        float shown = speedDisplay.show(speedKmh, signal);
        boolean weakSignal = !Float.isFinite(speedKmh) || speedKmh < 0f
                || (tripState != null && tripState.speedEstimated());
        if (shown < 0f) {
            tvSpeedValue.setText("—");
            tvSpeedValue.setContentDescription(getContext().getString(R.string.speedo_speed_unknown));
            speedArcView.setSpeedRatio(0f);
        } else {
            float displayedSpeed = isMetric ? shown : (shown * 0.621371f);
            tvSpeedValue.setText(String.format(locale, "%.0f", displayedSpeed));
            tvSpeedValue.setContentDescription(null);
            speedArcView.setSpeedRatio(Math.min(1.0f, shown / 220f));
        }
        tvSpeedUnit.setText(isMetric ? "KM/H" : "MPH");

        if (tripState != null) {
            boolean isPaused = tripState.isPaused();
            if (btnToggleTrip != null) {
                if (isPaused) {
                    btnToggleTrip.setText(R.string.speedo_resume_trip);
                    btnToggleTrip.setContentDescription(getContext().getString(R.string.speedo_resume_trip));
                } else {
                    btnToggleTrip.setText(R.string.speedo_pause_trip);
                    btnToggleTrip.setContentDescription(getContext().getString(R.string.speedo_pause_trip));
                }
            }
            String approx = tripState.isMostlyEstimated() ? "≈" : "";
            if (isMetric) {
                tvTripDistance.setText(approx + String.format(locale, "%.1f km", tripState.getDistanceKm()));
                tvAvgSpeed.setText(String.format(locale, "%.0f km/h", tripState.getAverageSpeedKmh()));
            } else {
                tvTripDistance.setText(approx + String.format(locale, "%.1f mi", tripState.getDistanceMiles()));
                tvAvgSpeed.setText(String.format(locale, "%.0f mph", tripState.getAverageSpeedMph()));
            }
            tvDriveTime.setText(tripState.getFormattedDriveTime());
            String description = switch (tripState.signal()) {
                case TRACKING -> weakSignal ? "◌ " + getContext().getString(R.string.speedo_signal_lost)
                        : "● " + getContext().getString(R.string.speedo_gps_ready);
                case WAITING -> "◌ " + getContext().getString(R.string.speedo_waiting);
                case STALE -> "◌ " + getContext().getString(R.string.speedo_signal_lost);
                case GPS_OFF -> getContext().getString(R.string.speedo_gps_off);
                case PERMISSION -> getContext().getString(R.string.speedo_permission);
                case PAUSED -> getContext().getString(R.string.speedo_paused);
                case ERROR -> getContext().getString(R.string.speedo_start_error);
                case BACKGROUND_BLOCKED -> "⚠ " + getContext().getString(R.string.speedo_tap_to_enable);
            };
            if (tripState.signal() == TripEngine.Signal.BACKGROUND_BLOCKED) {
                tvSignal.setText(description);
                tvSignal.setTextColor(colors.getActionText());
                tvSignal.setClickable(true);
                tvSignal.setFocusable(true);
                tvSignal.setOnClickListener(v -> TripTrackingService.start(getContext()));
                setClickable(true);
                setFocusable(true);
                setOnClickListener(v -> TripTrackingService.start(getContext()));
            } else {
                tvSignal.setText(description);
                tvSignal.setTextColor(colors.getTextSecondary());
                tvSignal.setClickable(false);
                tvSignal.setFocusable(false);
                tvSignal.setOnClickListener(null);
                setOnClickListener(null);
                setClickable(false);
            }
            String distanceText = tvTripDistance.getText().toString();
            if (tripState.isMostlyEstimated()) {
                distanceText = getContext().getString(R.string.speedo_estimated, distanceText);
            }
            if (tripState.incomplete()) {
                distanceText += ". " + getContext().getString(R.string.speedo_incomplete);
            }
            tvTripDistance.setContentDescription(distanceText + ". " + description);
            tvAvgSpeed.setContentDescription(getContext().getString(R.string.speedo_total_average)
                    + ": " + tvAvgSpeed.getText());
        }
    }

    private void confirmResetTrip(Context context) {
        try {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignored) {}
        me.aap.utils.ui.view.DialogBuilder builder = null;
        try {
            MainActivityDelegate a = MainActivityDelegate.get(context);
            if (a != null) {
                builder = a.createDialogBuilder(context);
            }
        } catch (Throwable ignored) {}
        if (builder == null) {
            try {
                me.aap.utils.ui.activity.ActivityDelegate ad = me.aap.utils.ui.activity.ActivityDelegate.get(context);
                if (ad != null) {
                    builder = ad.createDialogBuilder(context);
                }
            } catch (Throwable ignored) {}
        }
        if (builder != null) {
            builder.setTitle(R.string.speedo_reset_trip);
            builder.setMessage(R.string.speedo_reset_trip_confirm);
            builder.setPositiveButton(android.R.string.ok, (d, which) -> {
                if (onResetTripListener != null) {
                    onResetTripListener.run();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else {
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                    .setTitle(R.string.speedo_reset_trip)
                    .setMessage(R.string.speedo_reset_trip_confirm)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        if (onResetTripListener != null) {
                            onResetTripListener.run();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.show();
        }
    }

    private Locale displayLocale() {
        android.os.LocaleList locales = getResources().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }

    public void setOnResetTripListener(Runnable listener) {
        this.onResetTripListener = listener;
    }

    public void setOnToggleTripListener(Runnable listener) { onToggleTripListener = listener; }

    public boolean performToggleTrip() {
        return btnToggleTrip != null && btnToggleTrip.performClick();
    }

    boolean performTripStatsLongClick() {
        return tripStatsRow != null && tripStatsRow.performLongClick();
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

        @Nullable private android.animation.ValueAnimator sweep;

        /** Glides to the new value between the 1 Hz updates, then stops: no continuous frame loop. */
        public void setSpeedRatio(float ratio) {
            float target = Math.max(0f, Math.min(1f, ratio));
            if (sweep != null) sweep.cancel();
            if (!isAttachedToWindow() || Math.abs(target - speedRatio) < 0.002f) {
                speedRatio = target;
                invalidate();
                return;
            }
            sweep = android.animation.ValueAnimator.ofFloat(speedRatio, target);
            sweep.setDuration(600);
            sweep.setInterpolator(new android.view.animation.DecelerateInterpolator());
            sweep.addUpdateListener(a -> {
                speedRatio = (float) a.getAnimatedValue();
                invalidate();
            });
            sweep.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (sweep != null) sweep.cancel();
            super.onDetachedFromWindow();
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
