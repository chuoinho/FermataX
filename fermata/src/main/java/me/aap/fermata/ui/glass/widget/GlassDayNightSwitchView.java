package me.aap.fermata.ui.glass.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Sleek automotive toggle switch for Glass theme Day/Night mode.
 * Features an animated sliding thumb with Sun (Day) and Moon (Night) vector icons.
 */
public class GlassDayNightSwitchView extends View implements android.widget.Checkable {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private Drawable sunDrawable;
    private Drawable moonDrawable;

    private float density = 1.0f;
    private boolean isNight = false;
    private float thumbPosition = 0.0f; // 0.0f = Day, 1.0f = Night
    private ValueAnimator animator;

    @Nullable
    private MainActivityDelegate activity;

    public GlassDayNightSwitchView(@NonNull Context context) {
        this(context, (AttributeSet) null);
    }

    public GlassDayNightSwitchView(@NonNull Context context, @Nullable MainActivityDelegate activity) {
        super(context);
        this.activity = activity;
        init(context);
    }

    public GlassDayNightSwitchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassDayNightSwitchView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (context instanceof me.aap.fermata.ui.activity.MainActivity) {
            this.activity = MainActivityDelegate.get(context);
        }
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);

        trackPaint.setStyle(Paint.Style.FILL);
        trackStrokePaint.setStyle(Paint.Style.STROKE);
        trackStrokePaint.setStrokeWidth(Math.max(1, (int) (1.0f * density)));
        thumbPaint.setStyle(Paint.Style.FILL);

        sunDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_glass_day);
        if (sunDrawable != null) sunDrawable = sunDrawable.mutate();

        moonDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_glass_night);
        if (moonDrawable != null) moonDrawable = moonDrawable.mutate();

        syncMode(false);

        setOnClickListener(v -> toggleMode());
    }

    public void syncMode(boolean animate) {
        Context ctx = getContext();
        boolean newNight = (activity != null)
                ? activity.getPrefs().isGlassNightMode()
                : GlassThemeColors.isNight(ctx);

        this.isNight = newNight;
        float target = isNight ? 1.0f : 0.0f;

        String desc = isNight ? "Switch to Day Mode" : "Switch to Night Mode";
        try {
            desc = ctx.getString(isNight ? R.string.glass_appearance_day : R.string.glass_appearance_night);
        } catch (Throwable ignored) {}
        setContentDescription(desc);

        if (animate && getWidth() > 0) {
            startAnimation(target);
        } else {
            if (animator != null && animator.isRunning()) animator.cancel();
            thumbPosition = target;
            invalidate();
        }
    }

    private void toggleMode() {
        if (activity != null) {
            int nextMode = isNight ? MainActivityPrefs.GLASS_APPEARANCE_DAY : MainActivityPrefs.GLASS_APPEARANCE_NIGHT;
            activity.getPrefs().setGlassAppearancePref(nextMode);
            syncMode(true);
        } else {
            isNight = !isNight;
            syncMode(true);
        }
    }

    private void startAnimation(float target) {
        if (animator != null && animator.isRunning()) animator.cancel();
        animator = ValueAnimator.ofFloat(thumbPosition, target);
        animator.setDuration(180L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            thumbPosition = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = GlassDimensions.dpToPx(getContext(), 58);
        int desiredHeight = GlassDimensions.dpToPx(getContext(), 30);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = (widthMode == MeasureSpec.EXACTLY) ? widthSize : desiredWidth;
        int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : desiredHeight;

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0.0f || height <= 0.0f) return;

        float cornerRadius = height / 2.0f;
        trackRect.set(0.0f, 0.0f, width, height);

        // 1. Draw Track
        if (thumbPosition > 0.0f) {
            // Night track: dark obsidian / frosted glass
            trackPaint.setColor(0x35000000);
            trackPaint.setAlpha(Math.round(255 * (0.35f + thumbPosition * 0.45f)));
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);
        }
        if (thumbPosition < 1.0f) {
            // Day track: soft frosted translucent
            trackPaint.setColor(0x18000000);
            trackPaint.setAlpha(Math.round(255 * (1.0f - thumbPosition) * 0.25f));
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);
        }

        // Track Stroke
        int strokeColor = (thumbPosition >= 0.5f) ? 0x30FFFFFF : 0x22000000;
        trackStrokePaint.setColor(strokeColor);
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackStrokePaint);

        // 2. Thumb Metrics
        float margin = 3.0f * density;
        float thumbRadius = (height - margin * 2.0f) / 2.0f;
        float startX = margin + thumbRadius;
        float endX = width - margin - thumbRadius;
        float currentThumbX = startX + (endX - startX) * thumbPosition;
        float centerY = height / 2.0f;

        // Draw Sliding Thumb
        int thumbColor = (thumbPosition >= 0.5f) ? 0xFF00E5FF : 0xFFFFFFFF;
        thumbPaint.setColor(thumbColor);
        thumbPaint.setShadowLayer(3.0f * density, 0.0f, 1.0f * density, 0x44000000);
        canvas.drawCircle(currentThumbX, centerY, thumbRadius, thumbPaint);
        thumbPaint.clearShadowLayer();

        // 3. Draw Sun Icon (Left)
        if (sunDrawable != null) {
            int iconSize = Math.round(thumbRadius * 1.35f);
            int half = iconSize / 2;
            int sunX = Math.round(startX);
            int sunY = Math.round(centerY);
            sunDrawable.setBounds(sunX - half, sunY - half, sunX + half, sunY + half);

            // If thumb is on left (Day): warm amber / dark gray on white thumb.
            // If thumb is on right (Night): muted white.
            int sunColor;
            if (thumbPosition < 0.5f) {
                sunColor = 0xFFF59E0B; // Vibrant warm sun amber
            } else {
                sunColor = 0x55FFFFFF; // Dimmed in night
            }
            DrawableCompat.setTint(sunDrawable, sunColor);
            sunDrawable.draw(canvas);
        }

        // 4. Draw Moon Icon (Right)
        if (moonDrawable != null) {
            int iconSize = Math.round(thumbRadius * 1.35f);
            int half = iconSize / 2;
            int moonX = Math.round(endX);
            int moonY = Math.round(centerY);
            moonDrawable.setBounds(moonX - half, moonY - half, moonX + half, moonY + half);

            // If thumb is on right (Night): dark navy/black on neon cyan thumb.
            // If thumb is on left (Day): muted gray.
            int moonColor;
            if (thumbPosition >= 0.5f) {
                moonColor = 0xFF0A1118; // Crisp dark contrast on cyan thumb
            } else {
                moonColor = 0x44000000; // Dimmed in day
            }
            DrawableCompat.setTint(moonDrawable, moonColor);
            moonDrawable.draw(canvas);
        }
    }

    @Override
    public boolean isChecked() {
        return isNight;
    }

    @Override
    public void setChecked(boolean checked) {
        if (isNight != checked) {
            toggleMode();
        }
    }

    @Override
    public void toggle() {
        toggleMode();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Switch.class.getName());
        info.setCheckable(true);
        info.setChecked(isChecked());
    }
}
