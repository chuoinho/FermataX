package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Adaptive container that reflows the 3 Glass cards (Speedo, Nav/Weather, Media)
 * between WIDE (3-col), SQUARE (2-row), PORTRAIT (vertical stack), and SPLIT (2-col prioritized)
 * based on available content bounds and minimum content budgets.
 */
public class GlassDashboardLayout extends ViewGroup {

    public enum Mode {
        WIDE,      // Ultrawide/wide displays (>= 1.25 ratio) -> 3 columns
        SQUARE,    // Square or 4:3 displays (0.85 - 1.25 ratio) -> 2 rows
        PORTRAIT,  // Tall / Portrait displays (< 0.85 ratio) -> 3 stacked rows
        SPLIT      // AA Split screen active -> 2 columns prioritizing Nav & Media
    }

    private View headerView;
    private View speedoCard;
    private View navWeatherCard;
    private View mediaCard;
    private View quickBarView;

    private boolean split = false;
    private boolean isNight;

    public GlassDashboardLayout(@NonNull Context context) {
        this(context, null);
    }

    public GlassDashboardLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassDashboardLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.isNight = GlassThemeColors.isNight(context);
    }

    public void setViews(View headerView, View speedoCard, View navWeatherCard, View mediaCard, View quickBarView) {
        this.headerView = headerView;
        this.speedoCard = speedoCard;
        this.navWeatherCard = navWeatherCard;
        this.mediaCard = mediaCard;
        this.quickBarView = quickBarView;

        removeAllViews();
        if (headerView != null) addView(headerView);
        if (speedoCard != null) addView(speedoCard);
        if (navWeatherCard != null) addView(navWeatherCard);
        if (mediaCard != null) addView(mediaCard);
        if (quickBarView != null) addView(quickBarView);
    }

    public boolean isSplit() {
        return split;
    }

    public void setSplit(boolean split) {
        if (this.split != split) {
            this.split = split;
            requestLayout();
        }
    }

    private Mode lastMode = Mode.WIDE;

    public Mode getMode() {
        return lastMode;
    }

    public Mode computeMode(int width, int height) {
        if (split) return Mode.SPLIT;
        if (height <= 0) return Mode.WIDE;
        float ratio = (float) width / (float) height;
        if (ratio >= 1.25f) return Mode.WIDE;
        if (ratio >= 0.85f) return Mode.SQUARE;
        return Mode.PORTRAIT;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            isNight = GlassThemeColors.isNight(getContext());
            setBackground(GlassDrawables.createBackgroundGradient(w, h, isNight));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return;

        Context ctx = getContext();
        isNight = GlassThemeColors.isNight(ctx);

        int padH = GlassDimensions.dpToPx(ctx, 16);
        int padV = GlassDimensions.dpToPx(ctx, 12);
        int gap = GlassDimensions.dpToPx(ctx, 14);

        int headerH = GlassDimensions.dpToPx(ctx, 42);
        int quickBarH = GlassDimensions.dpToPx(ctx, 64);

        // Layout Top Header
        if (headerView != null && headerView.getVisibility() != GONE) {
            layoutChild(headerView, padH, padV, width - 2 * padH, headerH);
        }

        // Layout Bottom Quick Bar
        int quickBarTop = height - padV - quickBarH;
        if (quickBarView != null && quickBarView.getVisibility() != GONE) {
            if (quickBarView instanceof me.aap.fermata.ui.glass.quickbar.GlassQuickBarView) {
                float widthDp = (width - 2 * padH) / getResources().getDisplayMetrics().density;
                ((me.aap.fermata.ui.glass.quickbar.GlassQuickBarView) quickBarView).setActiveSlotCount(widthDp < 550f ? 4 : 6);
            }
            layoutChild(quickBarView, padH, quickBarTop, width - 2 * padH, quickBarH);
        }

        // Available Body Area between Header and Quick Bar
        int bodyTop = padV + headerH + gap;
        int bodyBottom = quickBarTop - gap;
        int bodyHeight = Math.max(0, bodyBottom - bodyTop);
        int bodyWidth = Math.max(0, width - 2 * padH);

        Mode mode = computeMode(width, height);
        this.lastMode = mode;

        // Ensure speedo visibility is properly restored when not in split mode (Fixes P1 defect 3)
        if (mode != Mode.SPLIT) {
            if (speedoCard != null && speedoCard.getVisibility() != VISIBLE) {
                speedoCard.setVisibility(VISIBLE);
            }
        }

        switch (mode) {
            case WIDE: {
                // Priority budget: Speedo takes compact width, Media and Nav/Weather get remaining width
                int minSpeedoWidth = GlassDimensions.dpToPx(ctx, 180);
                int speedoWidth = Math.max(minSpeedoWidth, (bodyWidth - 2 * gap) / 4);
                int remainingWidth = Math.max(0, bodyWidth - speedoWidth - 2 * gap);
                int navWeatherWidth = remainingWidth / 2;
                int mediaWidth = remainingWidth - navWeatherWidth;

                int x0 = padH;
                int x1 = x0 + speedoWidth + gap;
                int x2 = x1 + navWeatherWidth + gap;

                if (speedoCard != null) layoutChild(speedoCard, x0, bodyTop, speedoWidth, bodyHeight);
                if (navWeatherCard != null) layoutChild(navWeatherCard, x1, bodyTop, navWeatherWidth, bodyHeight);
                if (mediaCard != null) layoutChild(mediaCard, x2, bodyTop, mediaWidth, bodyHeight);
                break;
            }
            case SQUARE: {
                // 2 rows: Row 1 (Speedo + Media), Row 2 (Nav/Weather)
                int rowHeight = (bodyHeight - gap) / 2;
                int halfWidth = (bodyWidth - gap) / 2;

                if (speedoCard != null) layoutChild(speedoCard, padH, bodyTop, halfWidth, rowHeight);
                if (mediaCard != null) layoutChild(mediaCard, padH + halfWidth + gap, bodyTop, halfWidth, rowHeight);
                if (navWeatherCard != null) layoutChild(navWeatherCard, padH, bodyTop + rowHeight + gap, bodyWidth, rowHeight);
                break;
            }
            case PORTRAIT: {
                // 3 rows stacked
                int rowHeight = (bodyHeight - 2 * gap) / 3;
                int y0 = bodyTop;
                int y1 = y0 + rowHeight + gap;
                int y2 = y1 + rowHeight + gap;

                if (navWeatherCard != null) layoutChild(navWeatherCard, padH, y0, bodyWidth, rowHeight);
                if (mediaCard != null) layoutChild(mediaCard, padH, y1, bodyWidth, rowHeight);
                if (speedoCard != null) layoutChild(speedoCard, padH, y2, bodyWidth, rowHeight);
                break;
            }
            case SPLIT: {
                // 2 columns prioritizing Nav & Media; Speedo hidden
                int halfWidth = (bodyWidth - gap) / 2;
                if (speedoCard != null) speedoCard.setVisibility(GONE);
                if (navWeatherCard != null) {
                    navWeatherCard.setVisibility(VISIBLE);
                    layoutChild(navWeatherCard, padH, bodyTop, halfWidth, bodyHeight);
                }
                if (mediaCard != null) {
                    mediaCard.setVisibility(VISIBLE);
                    layoutChild(mediaCard, padH + halfWidth + gap, bodyTop, halfWidth, bodyHeight);
                }
                break;
            }
        }
    }

    private void layoutChild(View child, int l, int t, int w, int h) {
        if (child == null || child.getVisibility() == GONE) return;
        child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, w), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, h), MeasureSpec.EXACTLY));
        child.layout(l, t, l + w, t + h);
    }
}
