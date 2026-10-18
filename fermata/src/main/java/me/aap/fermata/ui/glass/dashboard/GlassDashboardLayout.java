package me.aap.fermata.ui.glass.dashboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.glass.media.GlassMediaPlayerView;
import me.aap.fermata.ui.glass.quickbar.GlassQuickBarView;
import me.aap.fermata.ui.glass.speedo.GlassSpeedoTripView;
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

        refreshAppearance();
    }

    public void refreshAppearance() {
        refreshAppearance(true);
    }

    public void refreshAppearance(boolean requestLayout) {
        Context ctx = getContext();
        isNight = GlassThemeColors.isNight(ctx);
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            setBackground(GlassDrawables.createBackgroundGradient(w, h, isNight));
        }
        if (speedoCard instanceof GlassSpeedoTripView s) {
            s.refreshAppearance();
        }
        if (navWeatherCard instanceof GlassNavWeatherView nw) {
            nw.refreshAppearance();
        }
        if (mediaCard instanceof GlassMediaPlayerView m) {
            m.refreshAppearance();
        }
        if (quickBarView instanceof GlassQuickBarView qb) {
            qb.refreshAppearance();
        }
        if (requestLayout) {
            requestLayout();
        }
    }

    public View getQuickBarView() {
        return quickBarView;
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
        boolean newNight = GlassThemeColors.isNight(ctx);
        if (newNight != isNight || getBackground() == null) {
            refreshAppearance(false);
        }

        float density = getResources().getDisplayMetrics().density;
        float rawHeightDp = height / Math.max(0.1f, density);
        float rawWidthDp = width / Math.max(0.1f, density);

        boolean isCar = GlassDimensions.isCar(ctx);
        boolean isCompactH = rawHeightDp < 440f;
        boolean isCompactW = rawWidthDp < 550f;

        int padH = GlassDimensions.dpToPx(ctx, isCompactW ? 10 : 16);
        int padV = GlassDimensions.dpToPx(ctx, isCompactH ? 6 : 10);
        int gap = GlassDimensions.dpToPx(ctx, (isCompactH || isCompactW) ? 8 : 12);

        float widthDp = (width - 2 * padH) / Math.max(0.1f, density);
        float heightDp = height / Math.max(0.1f, density);

        boolean hasHeader = (headerView != null && headerView.getVisibility() != GONE);
        int headerH = !hasHeader ? 0 : GlassDimensions.dpToPx(ctx, isCar
                ? (heightDp < 440f ? 28 : (heightDp < 540f ? 30 : 34))
                : 28);
        boolean hasQuickBar = (quickBarView != null && quickBarView.getVisibility() != GONE);
        int quickBarH = !hasQuickBar ? 0 : (isCar
                ? GlassDimensions.dpToPx(ctx, heightDp < 440f ? 54 : (heightDp < 540f ? 62 : 72))
                : ((heightDp >= 460f && widthDp >= 550f)
                        ? GlassDimensions.dpToPx(ctx, 64)
                        : (heightDp < 360f ? GlassDimensions.dpToPx(ctx, 52) : GlassDimensions.dpToPx(ctx, 56))));

        // Layout Top Header
        if (hasHeader) {
            layoutChild(headerView, padH, padV, width - 2 * padH, headerH);
        }

        // Layout Bottom Quick Bar
        int quickBarTop = height - padV - quickBarH;
        if (hasQuickBar) {
            if (quickBarView instanceof me.aap.fermata.ui.glass.quickbar.GlassQuickBarView) {
                ((me.aap.fermata.ui.glass.quickbar.GlassQuickBarView) quickBarView).setActiveSlotCount(widthDp < 550f ? 4 : 6);
            }
            layoutChild(quickBarView, padH, quickBarTop, width - 2 * padH, quickBarH);
        }

        // Available Body Area between Header and Quick Bar
        int bodyTop = hasHeader ? (padV + headerH + gap) : padV;
        int bodyBottom = hasQuickBar ? (quickBarTop - gap) : (height - padV);
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
                // Proportional distribution across all 3 cards:
                // Speedo card takes ~28% of available cards width (bounded between 130dp and 220dp),
                // while Nav/Weather and Media cards share the remaining width equally.
                int availCardsWidth = Math.max(0, bodyWidth - 2 * gap);
                int minSpeedo = GlassDimensions.dpToPx(ctx, 130);
                int maxSpeedo = GlassDimensions.dpToPx(ctx, 220);
                int speedoWidth = Math.max(minSpeedo, Math.min(maxSpeedo, Math.round(availCardsWidth * 0.28f)));
                int remainingWidth = Math.max(0, availCardsWidth - speedoWidth);
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
                float fontScale = getContext().getResources().getConfiguration().fontScale;
                int minCardHeight = GlassDimensions.dpToPx(ctx, Math.round(96 * Math.max(1.0f, fontScale)));
                if (bodyHeight < minCardHeight * 3 + 2 * gap) {
                    // Height-constrained portrait: Prioritize NavWeather and Media cards, hide Speedo to preserve minimum touch targets
                    if (speedoCard != null) speedoCard.setVisibility(GONE);
                    int rowHeight = (bodyHeight - gap) / 2;
                    if (navWeatherCard != null) {
                        navWeatherCard.setVisibility(VISIBLE);
                        layoutChild(navWeatherCard, padH, bodyTop, bodyWidth, rowHeight);
                    }
                    if (mediaCard != null) {
                        mediaCard.setVisibility(VISIBLE);
                        layoutChild(mediaCard, padH, bodyTop + rowHeight + gap, bodyWidth, rowHeight);
                    }
                } else {
                    if (speedoCard != null && speedoCard.getVisibility() != VISIBLE) {
                        speedoCard.setVisibility(VISIBLE);
                    }
                    int rowHeight = (bodyHeight - 2 * gap) / 3;
                    int y0 = bodyTop;
                    int y1 = y0 + rowHeight + gap;
                    int y2 = y1 + rowHeight + gap;

                    if (navWeatherCard != null) layoutChild(navWeatherCard, padH, y0, bodyWidth, rowHeight);
                    if (mediaCard != null) layoutChild(mediaCard, padH, y1, bodyWidth, rowHeight);
                    if (speedoCard != null) layoutChild(speedoCard, padH, y2, bodyWidth, rowHeight);
                }
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
