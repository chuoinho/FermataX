package me.aap.fermata.ui.glass.rail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.utils.ui.view.NavBarView;

/**
 * Independent 5-button automotive Glass Navigation Rail.
 * Exactly 5 buttons in order: Home, Voice, Apps, Settings, Exit.
 * No individual addons, no ScrollView, no scroll.
 * Collapsed: visible grabber handle at left/right edge.
 * Expanded: floats over content with scrim backdrop; does not push layout.
 */
public class GlassNavigationRailView extends FrameLayout {

    private static final String TAG = "GlassNavRail";
    private static final long ANIM_DURATION_MS = 220L;

    private final MainActivityDelegate activity;
    private boolean isNight;
    private GlassThemeColors colors;
    private boolean isRight;

    private View scrimView;
    private LinearLayout railContainer;
    private FrameLayout grabberHandle;
    private View[] actionButtons;

    private boolean isOpen = false;
    private boolean fullscreenHidden = false;
    private View previouslyFocusedView = null;
    private Runnable pendingFocusRunnable = null;
    private ValueAnimator activeAnimator = null;

    public GlassNavigationRailView(@NonNull Context context) {
        this(context, (MainActivityDelegate) null);
    }

    public GlassNavigationRailView(@NonNull Context context, @Nullable MainActivityDelegate activity) {
        super(context);
        this.activity = activity;
        init(context);
    }

    public GlassNavigationRailView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.activity = (context instanceof me.aap.fermata.ui.activity.MainActivity) ? MainActivityDelegate.get(context) : null;
        init(context);
    }

    private void init(Context context) {
        this.isNight = (activity != null) ? activity.getPrefs().isGlassNightMode() : GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);
        this.isRight = (activity != null) && (activity.getPrefs().getNavBarPosPref(activity) == NavBarView.POSITION_RIGHT);

        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Scrim Backdrop
        scrimView = new View(context);
        scrimView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrimView.setBackgroundColor(Color.argb(135, 0, 0, 0));
        scrimView.setVisibility(GONE);
        scrimView.setAlpha(0f);
        scrimView.setOnClickListener(v -> collapse());
        addView(scrimView);

        // 2. Rail Container (Floating overlay)
        int railWidth = GlassDimensions.dpToPx(context, 76);
        railContainer = new LinearLayout(context);
        railContainer.setOrientation(LinearLayout.VERTICAL);
        railContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        railContainer.setBackground(GlassDrawables.createGlassCardDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
        railContainer.setElevation(GlassDimensions.dpToPx(context, 16));

        LayoutParams railLp = new LayoutParams(railWidth, LayoutParams.MATCH_PARENT);
        railLp.gravity = (isRight ? Gravity.END : Gravity.START) | Gravity.TOP;
        int vPad = GlassDimensions.dpToPx(context, 12);
        railLp.topMargin = vPad;
        railLp.bottomMargin = vPad;
        railContainer.setLayoutParams(railLp);
        railContainer.setPadding(0, GlassDimensions.dpToPx(context, 8), 0, GlassDimensions.dpToPx(context, 8));
        railContainer.setVisibility(GONE);
        addView(railContainer);

        // 3. Grabber Handle (Tay nắm tại mép)
        int handleWidth = GlassDimensions.dpToPx(context, 26);
        int handleHeight = GlassDimensions.dpToPx(context, 72);
        grabberHandle = new FrameLayout(context);
        LayoutParams handleLp = new LayoutParams(handleWidth, handleHeight);
        handleLp.gravity = (isRight ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        grabberHandle.setLayoutParams(handleLp);
        grabberHandle.setBackground(GlassDrawables.createGlassPillDrawable(context, 13f, isNight));
        grabberHandle.setElevation(GlassDimensions.dpToPx(context, 18));
        grabberHandle.setFocusable(true);
        grabberHandle.setContentDescription(safeGetString(context, R.string.theme_glass, "Glass") + " Menu");

        // Subtle grip indicator inside handle
        TextView tvGrip = new TextView(context);
        tvGrip.setText(isRight ? "‹" : "›");
        tvGrip.setTextSize(18f);
        tvGrip.setTypeface(Typeface.DEFAULT_BOLD);
        tvGrip.setTextColor(colors.getTextPrimary());
        FrameLayout.LayoutParams gripLp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        tvGrip.setLayoutParams(gripLp);
        grabberHandle.addView(tvGrip);

        grabberHandle.setOnClickListener(v -> expand());
        addView(grabberHandle);

        buildActionButtons(context);
    }

    private void buildActionButtons(Context context) {
        railContainer.removeAllViews();
        actionButtons = new View[5];

        // Button 1: Home
        actionButtons[0] = createButton(context, R.drawable.home, R.string.home, false, () -> {
            if (activity != null) activity.showFragment(R.id.dashboard_fragment);
        });

        // Button 2: Voice
        actionButtons[1] = createButton(context, R.drawable.voice_microphone, R.string.voice_control, false, () -> {
            if (activity != null) activity.startGlobalVoiceControl();
        });

        // Button 3: Apps / Addons Catalog
        actionButtons[2] = createButton(context, R.drawable.view_grid, "Apps", false, () -> {
            if (activity != null) activity.showFragment(R.id.glass_addons_catalog_fragment);
        });

        // Button 4: Settings
        actionButtons[3] = createButton(context, R.drawable.settings, R.string.settings, false, () -> {
            if (activity != null) activity.showFragment(R.id.settings_fragment);
        });

        // Button 5: Exit
        actionButtons[4] = createButton(context, R.drawable.exit, R.string.exit, true, () -> {
            if (activity != null) activity.finish();
        });

        for (View btn : actionButtons) {
            railContainer.addView(btn);
        }
    }

    private static String safeGetString(Context context, int resId, String fallback) {
        try {
            if (context != null && resId != 0) {
                return context.getString(resId);
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private View createButton(Context context, int iconResId, int titleResId, boolean isExit, Runnable action) {
        String title;
        if (titleResId == R.string.home) title = safeGetString(context, titleResId, "Home");
        else if (titleResId == R.string.voice_control) title = safeGetString(context, titleResId, "Voice");
        else if (titleResId == R.string.settings) title = safeGetString(context, titleResId, "Settings");
        else if (titleResId == R.string.exit) title = safeGetString(context, titleResId, "Exit");
        else title = safeGetString(context, titleResId, "");
        return createButton(context, iconResId, title, isExit, action);
    }

    private View createButton(Context context, int iconResId, String title, boolean isExit, Runnable action) {
        LinearLayout btn = new LinearLayout(context);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(false);
        btn.setContentDescription(title);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        lp.topMargin = GlassDimensions.dpToPx(context, 4);
        lp.bottomMargin = GlassDimensions.dpToPx(context, 4);
        btn.setLayoutParams(lp);

        int pad = GlassDimensions.dpToPx(context, 6);
        btn.setPadding(pad, pad, pad, pad);
        btn.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));

        ImageView iv = new ImageView(context);
        int iconSize = GlassDimensions.dpToPx(context, 24);
        iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        iv.setImageResource(iconResId);
        int iconColor = isExit ? Color.parseColor("#FF5252") : colors.getTextPrimary();
        iv.setColorFilter(iconColor);
        btn.addView(iv);

        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextSize(10f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(iconColor);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        btn.addView(tv);

        btn.setOnClickListener(v -> {
            collapse();
            if (action != null) action.run();
        });

        btn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                collapse();
                if (action != null) action.run();
                return true;
            }
            return false;
        });

        return btn;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (height > 0) {
            float heightDp = height / getResources().getDisplayMetrics().density;
            // 5 buttons need minimum 48dp touch target each = 240dp
            if (heightDp < 240f) {
                Log.w(TAG, "Viewport height (" + heightDp + "dp) is smaller than required 240dp for 5 buttons without scroll.");
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void expand() {
        if (isOpen) return;
        isOpen = true;

        cancelPendingCallbacks();

        int railWidth = railContainer.getWidth();
        if (railWidth <= 0) railWidth = GlassDimensions.dpToPx(getContext(), 76);
        float startX = isRight ? railWidth : -railWidth;

        scrimView.setVisibility(VISIBLE);
        railContainer.setVisibility(VISIBLE);
        railContainer.setTranslationX(startX);
        grabberHandle.setVisibility(GONE);

        previouslyFocusedView = findFocus();

        activeAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeAnimator.setDuration(ANIM_DURATION_MS);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        final float animDistance = startX;
        activeAnimator.addUpdateListener(animation -> {
            float frac = (float) animation.getAnimatedValue();
            scrimView.setAlpha(frac);
            railContainer.setTranslationX(animDistance * (1f - frac));
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                activeAnimator = null;
                railContainer.setTranslationX(0f);
                scrimView.setAlpha(1f);

                // Safe focus request guarded by pendingFocusRunnable
                pendingFocusRunnable = () -> {
                    if (isOpen && isAttachedToWindow() && actionButtons != null && actionButtons.length > 0) {
                        actionButtons[0].requestFocus();
                        actionButtons[0].sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    }
                    pendingFocusRunnable = null;
                };
                post(pendingFocusRunnable);
            }
        });
        activeAnimator.start();
    }

    public void collapse() {
        if (!isOpen) return;
        isOpen = false;

        cancelPendingCallbacks();

        int railWidth = railContainer.getWidth();
        if (railWidth <= 0) railWidth = GlassDimensions.dpToPx(getContext(), 76);
        final float endX = isRight ? railWidth : -railWidth;

        activeAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeAnimator.setDuration(ANIM_DURATION_MS);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        activeAnimator.addUpdateListener(animation -> {
            float frac = (float) animation.getAnimatedValue();
            scrimView.setAlpha(1f - frac);
            railContainer.setTranslationX(endX * frac);
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                activeAnimator = null;
                railContainer.setVisibility(GONE);
                scrimView.setVisibility(GONE);
                if (!fullscreenHidden) {
                    grabberHandle.setVisibility(VISIBLE);
                }
                if (previouslyFocusedView != null && previouslyFocusedView.isAttachedToWindow()) {
                    previouslyFocusedView.requestFocus();
                    previouslyFocusedView = null;
                }
            }
        });
        activeAnimator.start();
    }

    public void collapseImmediate() {
        isOpen = false;
        cancelPendingCallbacks();
        railContainer.setVisibility(GONE);
        scrimView.setVisibility(GONE);
        scrimView.setAlpha(0f);
        if (!fullscreenHidden) {
            grabberHandle.setVisibility(VISIBLE);
        }
    }

    public boolean onBackPressed() {
        if (isOpen) {
            collapse();
            return true;
        }
        return false;
    }

    public void setFullscreenHidden(boolean hidden) {
        this.fullscreenHidden = hidden;
        if (hidden) {
            collapseImmediate();
            grabberHandle.setVisibility(GONE);
        } else {
            if (!isOpen) {
                grabberHandle.setVisibility(VISIBLE);
            }
        }
    }

    public void onAppearanceChanged() {
        this.isNight = (activity != null) ? activity.getPrefs().isGlassNightMode() : GlassThemeColors.isNight(getContext());
        this.colors = GlassThemeColors.get(getContext(), isNight);
        railContainer.setBackground(GlassDrawables.createGlassCardDrawable(getContext(), GlassDimensions.CARD_RADIUS_DP, isNight));
        grabberHandle.setBackground(GlassDrawables.createGlassPillDrawable(getContext(), 13f, isNight));
        buildActionButtons(getContext());
    }

    private void cancelPendingCallbacks() {
        if (pendingFocusRunnable != null) {
            removeCallbacks(pendingFocusRunnable);
            pendingFocusRunnable = null;
        }
        if (activeAnimator != null) {
            activeAnimator.cancel();
            activeAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelPendingCallbacks();
    }
}
