package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;

/**
 * Responsive automotive Glass ViewGroup adapting cards layout according to screen aspect ratios.
 * Adapted from CoDienBoCuc.
 */
public class GlassDashboardLayout extends ViewGroup {

    public enum Mode {
        WIDE,
        SQUARE,
        PORTRAIT,
        SPLIT
    }

    private final boolean isNight;
    private boolean split = false;

    private View headerView;
    private View speedoCard;
    private View navWeatherCard;
    private View mediaCard;
    private View quickBarView;

    public GlassDashboardLayout(@NonNull Context context) {
        this(context, null);
    }

    public GlassDashboardLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassDashboardLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int uiMode = getResources().getConfiguration().uiMode;
        this.isNight = (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_NO;
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
            layoutChild(quickBarView, padH, quickBarTop, width - 2 * padH, quickBarH);
        }

        // Available Body Area between Header and Quick Bar
        int bodyTop = padV + headerH + gap;
        int bodyBottom = quickBarTop - gap;
        int bodyHeight = Math.max(0, bodyBottom - bodyTop);
        int bodyWidth = Math.max(0, width - 2 * padH);

        Mode mode = computeMode(width, height);

        switch (mode) {
            case WIDE: {
                // 3 columns: Left (Speedo), Center (Nav/Weather), Right (Media)
                int colWidth = (bodyWidth - 2 * gap) / 3;
                int x0 = padH;
                int x1 = x0 + colWidth + gap;
                int x2 = x1 + colWidth + gap;

                if (speedoCard != null) layoutChild(speedoCard, x0, bodyTop, colWidth, bodyHeight);
                if (navWeatherCard != null) layoutChild(navWeatherCard, x1, bodyTop, colWidth, bodyHeight);
                if (mediaCard != null) layoutChild(mediaCard, x2, bodyTop, colWidth, bodyHeight);
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
                // 2 columns prioritizing Nav & Media
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
