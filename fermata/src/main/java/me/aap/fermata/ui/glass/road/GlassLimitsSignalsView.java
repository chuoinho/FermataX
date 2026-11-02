package me.aap.fermata.ui.glass.road;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import androidx.core.content.ContextCompat;

import me.aap.fermata.R;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.fermata.ui.view.MinimumTouchTargetDelegate;

public class GlassLimitsSignalsView extends FrameLayout {

    private boolean isNight;
    private GlassThemeColors colors;

    private TextView tvCardTitle;
    private View btnSwap;
    private SpeedLimitSignView signView;
    private TextView tvSpeedValue;
    private TextView tvSpeedUnit;
    private FrameLayout chipContainer;
    private LinearLayout chipActiveLayout;
    private TextView tvChipActiveLabel;
    private TextView tvChipActiveDist;
    private SignalProgressBar chipProgressBar;
    private TextView tvChipMuted;

    private Runnable onSwapListener;

    public GlassLimitsSignalsView(@NonNull Context context) {
        this(context, null);
    }

    public GlassLimitsSignalsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassLimitsSignalsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);
        initView(context);
    }

    private void initView(Context context) {
        boolean isCar = GlassDimensions.isCar(context);
        setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
        int padH = GlassDimensions.dpToPx(context, 12);
        int padV = GlassDimensions.dpToPx(context, 10);
        setPadding(padH, padV, padH, padV);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams contentLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(contentLayout, contentLp);

        // 1. Top Bar: Title + Swap Button
        FrameLayout topBar = new FrameLayout(context);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(topBarLp);

        tvCardTitle = new TextView(context);
        tvCardTitle.setText(context.getString(R.string.glass_limits_title));
        tvCardTitle.setTextSize(11f);
        tvCardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvCardTitle.setTextColor(colors.getTextSecondary());
        tvCardTitle.setLetterSpacing(0.08f);
        tvCardTitle.setSingleLine(true);
        tvCardTitle.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL);
        titleLp.setMarginEnd(GlassDimensions.dpToPx(context, 36));
        topBar.addView(tvCardTitle, titleLp);

        contentLayout.addView(topBar);

        // Outside the title row and centred on the title line, so the title sits at the same height as on other cards.
        btnSwap = createSwapButton(context);
        addView(btnSwap, new LayoutParams(0, 0, Gravity.TOP | Gravity.END));
        sizeSwapButton(GlassDimensions.dpToPx(context, 28));
        setClipToPadding(false);
        MinimumTouchTargetDelegate.install(this, btnSwap);

        // 2. Middle Row: Round Speed Limit Sign (left) + Speed & Unit (right)
        LinearLayout midRow = new LinearLayout(context);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        midRow.setLayoutParams(midLp);

        signView = new SpeedLimitSignView(context, isNight);
        int signSize = GlassDimensions.dpToPx(context, isCar ? 84 : 76);
        LinearLayout.LayoutParams signLp = new LinearLayout.LayoutParams(signSize, signSize);
        signLp.setMarginEnd(GlassDimensions.dpToPx(context, 16));
        midRow.addView(signView, signLp);

        LinearLayout speedLayout = new LinearLayout(context);
        speedLayout.setOrientation(LinearLayout.VERTICAL);
        speedLayout.setGravity(Gravity.CENTER);

        tvSpeedValue = new TextView(context);
        tvSpeedValue.setText("--");
        tvSpeedValue.setTextSize(48f);
        tvSpeedValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvSpeedValue.setTextColor(colors.getTextPrimary());
        tvSpeedValue.setGravity(Gravity.CENTER);
        speedLayout.addView(tvSpeedValue);

        tvSpeedUnit = new TextView(context);
        tvSpeedUnit.setText("KM/H");
        tvSpeedUnit.setTextSize(11f);
        tvSpeedUnit.setTypeface(Typeface.DEFAULT_BOLD);
        tvSpeedUnit.setTextColor(colors.getActionText());
        tvSpeedUnit.setLetterSpacing(0.12f);
        tvSpeedUnit.setGravity(Gravity.CENTER);
        speedLayout.addView(tvSpeedUnit);

        midRow.addView(speedLayout);
        contentLayout.addView(midRow);

        // 3. Signal Chip (fixed height)
        chipContainer = new FrameLayout(context);
        int chipMinH = GlassDimensions.dpToPx(context, isCar ? 50 : 44);
        chipContainer.setMinimumHeight(chipMinH);
        int chipPadH = GlassDimensions.dpToPx(context, 10);
        int chipPadV = GlassDimensions.dpToPx(context, 6);
        chipContainer.setPadding(chipPadH, chipPadV, chipPadH, chipPadV);
        chipContainer.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, chipMinH);
        chipContainer.setLayoutParams(chipLp);

        chipActiveLayout = new LinearLayout(context);
        chipActiveLayout.setOrientation(LinearLayout.VERTICAL);
        chipActiveLayout.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams activeLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        chipActiveLayout.setLayoutParams(activeLp);

        LinearLayout activeTop = new LinearLayout(context);
        activeTop.setOrientation(LinearLayout.HORIZONTAL);
        activeTop.setGravity(Gravity.CENTER_VERTICAL);

        ImageView lightIcon = new ImageView(context);
        lightIcon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_traffic_light));
        lightIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = GlassDimensions.dpToPx(context, 18);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(GlassDimensions.dpToPx(context, 6));
        activeTop.addView(lightIcon, iconLp);

        tvChipActiveLabel = new TextView(context);
        tvChipActiveLabel.setText(context.getString(R.string.glass_signal_label));
        tvChipActiveLabel.setTextSize(12f);
        tvChipActiveLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvChipActiveLabel.setTextColor(colors.getTextPrimary());
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        activeTop.addView(tvChipActiveLabel, labelLp);

        tvChipActiveDist = new TextView(context);
        tvChipActiveDist.setTextSize(13f);
        tvChipActiveDist.setTypeface(Typeface.DEFAULT_BOLD);
        tvChipActiveDist.setTextColor(colors.getTextPrimary());
        activeTop.addView(tvChipActiveDist);
        chipActiveLayout.addView(activeTop);

        chipProgressBar = new SignalProgressBar(context);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, GlassDimensions.dpToPx(context, 4));
        barLp.topMargin = GlassDimensions.dpToPx(context, 4);
        chipActiveLayout.addView(chipProgressBar, barLp);
        chipContainer.addView(chipActiveLayout);

        tvChipMuted = new TextView(context);
        tvChipMuted.setTextSize(12f);
        tvChipMuted.setTextColor(colors.getTextSecondary());
        tvChipMuted.setGravity(Gravity.CENTER);
        tvChipMuted.setSingleLine(true);
        tvChipMuted.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams mutedLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        chipContainer.addView(tvChipMuted, mutedLp);

        contentLayout.addView(chipContainer);

        showWaiting();
    }

    // Same 28dp corner icon as the Weather / Navigation switch; MinimumTouchTargetDelegate widens the tap area to 48dp.
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0) post(() -> sizeSwapButton(GlassDimensions.headerButtonPx(getContext(), h)));
    }

    // Centred on the single-line title (font padding included, the TextView default).
    private void sizeSwapButton(int size) {
        LayoutParams lp = (LayoutParams) btnSwap.getLayoutParams();
        if (lp.width == size) return;
        Paint.FontMetricsInt fm = tvCardTitle.getPaint().getFontMetricsInt();
        lp.width = size;
        lp.height = size;
        lp.topMargin = (fm.bottom - fm.top - size) / 2;
        int pad = Math.round(size * 0.1f);
        btnSwap.setPadding(pad, pad, pad, pad);
        btnSwap.setLayoutParams(lp);
    }

    private View createSwapButton(Context context) {
        ImageView btn = new ImageView(context);
        btn.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_speedo_mini));
        btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        btn.setBackground(GlassDrawables.createGlassPillDrawable(context, 14f, isNight));
        btn.setContentDescription(context.getString(R.string.glass_show_speed));
        btn.setFocusable(true);
        btn.setOnClickListener(v -> {
            if (onSwapListener != null) {
                onSwapListener.run();
            }
        });
        return btn;
    }

    // Speed is not touched here: it always mirrors the speed card (showSpeed).
    public void showWaiting() {
        signView.setLimit(null);
        showMuted("◌ " + getContext().getString(R.string.speedo_waiting));
    }

    /** The exact value and unit the speed card shows, so both cards always agree. */
    public void showSpeed(CharSequence value, CharSequence unit) {
        tvSpeedValue.setText(value);
        tvSpeedUnit.setText(unit);
    }

    /**
     * The chip shows the traffic light only while one is in range; otherwise it names the road.
     * roadName is null when no road is matched yet, empty for an unnamed road.
     */
    public void show(@Nullable SpeedLimit limit, @Nullable String roadName,
                     float signalDistanceM, float signalProgress) {
        signView.setLimit(limit);

        if (signalDistanceM >= 0f) {
            int distM = Math.round(signalDistanceM);
            chipActiveLayout.setVisibility(View.VISIBLE);
            tvChipMuted.setVisibility(View.GONE);
            tvChipActiveDist.setText(distM + " m");
            chipProgressBar.setProgress(signalProgress);
            chipContainer.setContentDescription(getContext().getString(R.string.glass_signal_desc, distM));
        } else if (roadName == null) {
            showMuted(getContext().getString(R.string.glass_road_searching));
        } else if (roadName.isEmpty()) {
            showMuted(getContext().getString(R.string.glass_road_unnamed));
        } else {
            showMuted(roadName);
        }
    }

    private void showMuted(String text) {
        chipActiveLayout.setVisibility(View.GONE);
        tvChipMuted.setVisibility(View.VISIBLE);
        tvChipMuted.setText(text);
        chipContainer.setContentDescription(text);
    }

    public void setOnSwapListener(Runnable r) {
        this.onSwapListener = r;
    }

    public void refreshAppearance() {
        this.isNight = GlassThemeColors.isNight(getContext());
        this.colors = GlassThemeColors.get(getContext(), isNight);
        setBackground(GlassDrawables.createGlassCardDrawable(getContext(), GlassDimensions.CARD_RADIUS_DP, isNight));
        tvCardTitle.setTextColor(colors.getTextSecondary());
        btnSwap.setBackground(GlassDrawables.createGlassPillDrawable(getContext(), 14f, isNight));
        signView.setNight(isNight);
        tvSpeedValue.setTextColor(colors.getTextPrimary());
        tvSpeedUnit.setTextColor(colors.getActionText());
        chipContainer.setBackground(GlassDrawables.createGlassPillDrawable(getContext(), GlassDimensions.PILL_RADIUS_DP, isNight));
        tvChipActiveLabel.setTextColor(colors.getTextPrimary());
        tvChipActiveDist.setTextColor(colors.getTextPrimary());
        tvChipMuted.setTextColor(colors.getTextSecondary());
    }

    // Testing and inspection helpers
    public CharSequence getSpeedText() {
        return tvSpeedValue.getText();
    }

    public CharSequence getSpeedUnitText() {
        return tvSpeedUnit.getText();
    }

    public CharSequence getSignContentDescription() {
        return signView.getContentDescription();
    }

    public CharSequence getChipContentDescription() {
        return chipContainer.getContentDescription();
    }

    public CharSequence getChipText() {
        return tvChipMuted.getVisibility() == View.VISIBLE ? tvChipMuted.getText() : tvChipActiveDist.getText();
    }

    public View getSwapButton() {
        return btnSwap;
    }

    /**
     * Canvas-drawn round speed limit sign with variants:
     * - Number: white disc, red ring, black number.
     * - Unlimited: white disc, thin black ring, diagonal black stripes.
     * - No data: transparent background, grey ring, grey em-dash.
     */
    private static class SpeedLimitSignView extends View {
        private SpeedLimit limit;
        private boolean isNight;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        SpeedLimitSignView(Context context, boolean isNight) {
            super(context);
            this.isNight = isNight;
            ringPaint.setStyle(Paint.Style.STROKE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            stripePaint.setStyle(Paint.Style.STROKE);
            stripePaint.setColor(Color.parseColor("#222222"));
            stripePaint.setStrokeWidth(GlassDimensions.dpToPx(context, 3));
        }

        void setLimit(@Nullable SpeedLimit limit) {
            this.limit = limit;
            if (limit != null) {
                if (limit.unlimited) {
                    setContentDescription(getContext().getString(R.string.glass_limits_no_data));
                } else {
                    setContentDescription(getContext().getString(R.string.glass_speed_limit_desc, limit.value));
                }
            } else {
                setContentDescription(getContext().getString(R.string.glass_limits_no_data));
            }
            invalidate();
        }

        void setNight(boolean isNight) {
            this.isNight = isNight;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float cx = w / 2f;
            float cy = h / 2f;
            float strokeW = GlassDimensions.dpToPx(getContext(), 5);
            float radius = Math.min(cx, cy) - strokeW / 2f;
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

            if (limit == null) {
                // No data / Waiting: grey ring, "—"
                ringPaint.setColor(Color.parseColor("#4B5567"));
                ringPaint.setStrokeWidth(strokeW);
                canvas.drawCircle(cx, cy, radius, ringPaint);

                textPaint.setColor(Color.parseColor("#5D6B82"));
                textPaint.setTextSize(radius * 0.9f);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textY = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText("—", cx, textY, textPaint);
            } else if (limit.unlimited) {
                // German Autobahn end-of-all-limits: white disc, black diagonal stripes
                fillPaint.setColor(Color.WHITE);
                fillPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius, fillPaint);

                ringPaint.setColor(Color.parseColor("#222222"));
                ringPaint.setStrokeWidth(GlassDimensions.dpToPx(getContext(), 2));
                canvas.drawCircle(cx, cy, radius, ringPaint);

                // Diagonal stripes
                canvas.save();
                canvas.rotate(45, cx, cy);
                for (int offset = -2; offset <= 2; offset++) {
                    float x = cx + offset * GlassDimensions.dpToPx(getContext(), 5);
                    canvas.drawLine(x, cy - radius * 0.8f, x, cy + radius * 0.8f, stripePaint);
                }
                canvas.restore();
            } else {
                // Standard speed limit sign: white disc, red ring, black number
                fillPaint.setColor(Color.WHITE);
                fillPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius, fillPaint);

                ringPaint.setColor(Color.parseColor("#E11D2E"));
                ringPaint.setStrokeWidth(strokeW);
                canvas.drawCircle(cx, cy, radius, ringPaint);

                textPaint.setColor(Color.parseColor("#111111"));
                String speedStr = String.valueOf(limit.value);
                float textSize = speedStr.length() > 2 ? radius * 0.8f : radius * 0.95f;
                textPaint.setTextSize(textSize);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textY = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(speedStr, cx, textY, textPaint);
            }
        }
    }

    private static class SignalProgressBar extends View {
        private float progress = 1.0f;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        SignalProgressBar(Context context) {
            super(context);
            trackPaint.setColor(Color.parseColor("#1FFFFFFF")); // 12% white
            barPaint.setColor(Color.parseColor("#FBBF24")); // Amber
        }

        void setProgress(float progress) {
            this.progress = Math.max(0f, Math.min(1f, progress));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float r = h / 2f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, trackPaint);

            float barW = w * progress;
            if (barW > 0) {
                rect.set(0, 0, barW, h);
                canvas.drawRoundRect(rect, r, r, barPaint);
            }
        }
    }
}
