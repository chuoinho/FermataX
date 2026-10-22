package me.aap.fermata.ui.glass.rail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.NavBarView;

/**
 * Independent 5-button automotive Glass Navigation Rail.
 * Exactly 5 buttons in order: Home, Voice, Apps, Settings, Exit.
 * No individual addons, no ScrollView, no scroll.
 * Collapsed: visible grabber handle at left/right edge with generous touch envelope.
 * Expanded: floats over content with scrim backdrop; does not push layout.
 */
public class GlassNavigationRailView extends FrameLayout {

    private static final String TAG = "GlassNavRail";
    private static final long ANIM_DURATION_MS = 220L;
    public static final long AUTO_HIDE_DELAY_MS = 5000L;

    private final MainActivityDelegate activity;
    private final Boolean isCarOverride;
    private boolean isNight;
    private GlassThemeColors colors;
    private boolean isRight;

    private View scrimView;
    private LinearLayout railContainer;
    private FrameLayout grabberHandle;
    private FrameLayout innerGrabberPill;
    private ImageView ivLauncher;
    private View[] actionButtons;

    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::collapse;
    private float downRawX = 0f;
    private float downRawY = 0f;
    private float initialTranslationY = 0f;
    private boolean isDragging = false;
    private boolean isHorizontalSwipe = false;
    private int touchSlop = 0;

    private float edgeDownX = 0f;
    private float edgeDownY = 0f;
    private boolean edgeTracking = false;
    private boolean edgeBackIntercepted = false;
    private Runnable backActionListenerForTesting = null;

    private boolean isOpen = false;
    private boolean fullscreenHidden = false;
    private View previouslyFocusedView = null;
    private Runnable pendingFocusRunnable = null;
    private ValueAnimator activeAnimator = null;
    private int animGeneration = 0;

    public GlassNavigationRailView(@NonNull Context context) {
        this(context, (MainActivityDelegate) null, null);
    }

    public GlassNavigationRailView(@NonNull Context context, @Nullable MainActivityDelegate activity) {
        this(context, activity, null);
    }

    // Visible for testing automotive host layout
    GlassNavigationRailView(@NonNull Context context, boolean isCar) {
        this(context, null, isCar);
    }

    private GlassNavigationRailView(@NonNull Context context, @Nullable MainActivityDelegate activity, @Nullable Boolean isCarOverride) {
        super(context);
        this.activity = activity;
        this.isCarOverride = isCarOverride;
        init(context);
    }

    public GlassNavigationRailView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.activity = (context instanceof me.aap.fermata.ui.activity.MainActivity) ? MainActivityDelegate.get(context) : null;
        this.isCarOverride = null;
        init(context);
    }

    public boolean isCar() {
        if (isCarOverride != null) return isCarOverride;
        if (activity != null) return activity.isCarActivity();
        return GlassDimensions.isCar(getContext());
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
        scrimView.setBackgroundColor(Color.argb(90, 0, 0, 0));
        scrimView.setVisibility(GONE);
        scrimView.setAlpha(0f);
        scrimView.setOnClickListener(v -> collapse());
        addView(scrimView);

        // 2. Rail Container (Floating overlay with high-opacity frosted glass)
        int railWidth = GlassDimensions.dpToPx(context, 76);
        railContainer = new LinearLayout(context);
        railContainer.setOrientation(LinearLayout.VERTICAL);
        railContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        railContainer.setBackground(GlassDrawables.createGlassRailDrawable(context, GlassDimensions.CARD_RADIUS_DP, isNight));
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

        // 3. Grabber Handle (Touch target envelope with visual pill inside)
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        boolean isCar = isCar();
        int hitWidth = GlassDimensions.dpToPx(context, isCar ? 80 : 64);
        int hitHeight = GlassDimensions.dpToPx(context, 104);
        int visualWidth = GlassDimensions.dpToPx(context, 44);
        int visualHeight = GlassDimensions.dpToPx(context, 88);

        grabberHandle = new FrameLayout(context);
        LayoutParams handleLp = new LayoutParams(hitWidth, hitHeight);
        handleLp.gravity = (isRight ? Gravity.END : Gravity.START) | Gravity.TOP;
        grabberHandle.setLayoutParams(handleLp);
        grabberHandle.setFocusable(true);
        grabberHandle.setClickable(true);

        innerGrabberPill = new FrameLayout(context);
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(visualWidth, visualHeight);
        pillLp.gravity = (isRight ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        innerGrabberPill.setLayoutParams(pillLp);
        innerGrabberPill.setBackground(GlassDrawables.createGlassGrabberSemicircleDrawable(context, 44f, isNight, isRight));
        innerGrabberPill.setElevation(GlassDimensions.dpToPx(context, 18));
        innerGrabberPill.setClipChildren(false);

        // App grid / launcher icon inside handle
        ivLauncher = new ImageView(context);
        int iconPx = GlassDimensions.dpToPx(context, 24);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                iconPx, iconPx, Gravity.CENTER);
        ivLauncher.setLayoutParams(iconLp);
        try {
            Drawable d = ContextCompat.getDrawable(context, R.drawable.ic_glass_rail_launcher);
            if (d != null) {
                ivLauncher.setImageDrawable(d);
            } else {
                ivLauncher.setImageResource(R.drawable.ic_glass_rail_launcher);
            }
        } catch (Throwable ignored) {
            ivLauncher.setImageResource(R.drawable.ic_glass_rail_launcher);
        }
        ivLauncher.setColorFilter(colors.getTextPrimary());
        innerGrabberPill.addView(ivLauncher);

        grabberHandle.addView(innerGrabberPill);
        grabberHandle.setOnClickListener(v -> expand());
        grabberHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    initialTranslationY = grabberHandle.getTranslationY();
                    isDragging = false;
                    isHorizontalSwipe = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - downRawX;
                    float deltaY = event.getRawY() - downRawY;
                    int swipeThreshold = GlassDimensions.dpToPx(getContext(), 30);

                    if (!isDragging && !isHorizontalSwipe) {
                        if (Math.abs(deltaY) > touchSlop && Math.abs(deltaY) > Math.abs(deltaX)) {
                            isDragging = true;
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        } else if (((!isRight && deltaX >= swipeThreshold) || (isRight && deltaX <= -swipeThreshold))
                                && Math.abs(deltaX) > Math.abs(deltaY)) {
                            isHorizontalSwipe = true;
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                    }

                    if (isHorizontalSwipe) {
                        if (!isOpen) {
                            expand();
                        }
                        return true;
                    }

                    if (isDragging) {
                        int parentH = getHeight();
                        int handleH = grabberHandle.getHeight();
                        if (handleH <= 0) handleH = GlassDimensions.dpToPx(getContext(), 104);
                        int minMargin = GlassDimensions.dpToPx(getContext(), 12);
                        int minY = minMargin;
                        int maxY = Math.max(minY, parentH - handleH - minMargin);
                        float targetY = Math.max(minY, Math.min(maxY, initialTranslationY + deltaY));
                        grabberHandle.setTranslationY(targetY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (isHorizontalSwipe) {
                        isHorizontalSwipe = false;
                        return true;
                    }
                    if (isDragging) {
                        int parentH = getHeight();
                        int handleH = grabberHandle.getHeight();
                        if (handleH <= 0) handleH = GlassDimensions.dpToPx(getContext(), 104);
                        int minMargin = GlassDimensions.dpToPx(getContext(), 12);
                        int minY = minMargin;
                        int maxY = Math.max(minY, parentH - handleH - minMargin);
                        float currentY = grabberHandle.getTranslationY();
                        float ratio = (maxY > minY) ? (currentY - minY) / (float) (maxY - minY) : 0.5f;
                        ratio = Math.max(0f, Math.min(1f, ratio));
                        saveYRatio(ratio);
                        isDragging = false;
                    } else {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    isHorizontalSwipe = false;
                    return true;
            }
            return false;
        });
        updateGrabberHandleAccessibility();
        addView(grabberHandle);

        buildActionButtons(context);
    }

    private void updateGrabberHandleAccessibility() {
        if (grabberHandle == null) return;
        String stateStr = isOpen ? " (Expanded)" : " (Collapsed)";
        grabberHandle.setContentDescription(safeGetString(getContext(), R.string.theme_glass, "Glass") + " Navigation Rail" + stateStr);
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
        actionButtons[2] = createButton(context, R.drawable.view_grid, R.string.addons, false, () -> {
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
        updateSelectedRoute();
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
        else if (titleResId == R.string.addons) title = safeGetString(context, titleResId, "Apps");
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
        boolean isCar = isCar();
        btn.setMinimumHeight(GlassDimensions.dpToPx(context, isCar ? 76 : 48));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        lp.topMargin = GlassDimensions.dpToPx(context, 4);
        lp.bottomMargin = GlassDimensions.dpToPx(context, 4);
        btn.setLayoutParams(lp);

        int pad = GlassDimensions.dpToPx(context, 6);
        btn.setPadding(pad, pad, pad, pad);
        btn.setBackground(createRailButtonDrawable(context, isNight, isExit, colors));

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
            previouslyFocusedView = null; // Do not restore focus to old route
            collapse();
            if (action != null) action.run();
        });

        btn.setOnKeyListener((v, keyCode, event) -> {
            // Guard against repeatCount > 0 to execute activation only once on initial key down
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                previouslyFocusedView = null;
                collapse();
                if (action != null) action.run();
                return true;
            }
            return false;
        });

        return btn;
    }

    public static Drawable createRailButtonDrawable(
            Context ctx, boolean isNight, boolean isExit, GlassThemeColors colors) {
        StateListDrawable sld = new StateListDrawable();
        int radius = GlassDimensions.dpToPx(ctx, 16f);
        int strokeWidth = Math.round(GlassDimensions.dpToPx(ctx, 1.5f));
        int accent = isExit ? Color.parseColor("#FF5252") : colors.getActionText();

        // 1. Focused state
        GradientDrawable foc = new GradientDrawable();
        foc.setShape(GradientDrawable.RECTANGLE);
        foc.setCornerRadius(radius);
        foc.setColor(isNight ? 0x38FFFFFF : 0x22000000);
        foc.setStroke(strokeWidth, accent);

        // 2. Pressed state
        GradientDrawable prs = new GradientDrawable();
        prs.setShape(GradientDrawable.RECTANGLE);
        prs.setCornerRadius(radius);
        prs.setColor(isNight ? 0x48FFFFFF : 0x30000000);
        prs.setStroke(strokeWidth, accent);

        // 3. Selected / Activated state (soft accent fill, no border)
        GradientDrawable sel = new GradientDrawable();
        sel.setShape(GradientDrawable.RECTANGLE);
        sel.setCornerRadius(radius);
        int selFill = (accent & 0x00FFFFFF) | (isNight ? 0x2A000000 : 0x20000000);
        sel.setColor(selFill);

        // 4. Normal state (clean, borderless)
        GradientDrawable norm = new GradientDrawable();
        norm.setShape(GradientDrawable.RECTANGLE);
        norm.setCornerRadius(radius);
        norm.setColor(Color.TRANSPARENT);

        sld.addState(new int[]{android.R.attr.state_focused}, foc);
        sld.addState(new int[]{android.R.attr.state_pressed}, prs);
        sld.addState(new int[]{android.R.attr.state_selected}, sel);
        sld.addState(new int[]{android.R.attr.state_activated}, sel);
        sld.addState(new int[]{}, norm);

        return sld;
    }

    public void updateSelectedRoute() {
        if (activity == null || actionButtons == null || actionButtons.length < 5) return;
        ActivityFragment frag = activity.getActiveFragment();
        int activeId = (frag != null) ? frag.getFragmentId() : 0;

        actionButtons[0].setSelected(activeId == R.id.dashboard_fragment);
        actionButtons[1].setSelected(false);
        actionButtons[2].setSelected(activeId == R.id.glass_addons_catalog_fragment);
        actionButtons[3].setSelected(activeId == R.id.settings_fragment);
        actionButtons[4].setSelected(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (height > 0) {
            float density = getResources().getDisplayMetrics().density;
            float heightDp = height / Math.max(0.1f, density);
            boolean isCar = isCar();

            // Dynamic budget allocation for the 5 buttons
            int vPad;
            int containerPad;
            int btnMargin;

            if (isCar) {
                if (heightDp >= 480f) {
                    // Standard AA height: generous overhead
                    vPad = GlassDimensions.dpToPx(getContext(), 10);
                    containerPad = GlassDimensions.dpToPx(getContext(), 6);
                    btnMargin = GlassDimensions.dpToPx(getContext(), 2);
                } else if (heightDp >= 262f) {
                    // Compact AA height (262dp - 480dp): tight overhead (22dp total: vPad 4, pad 2, margin 1)
                    // At H >= 402dp: (H - 22) / 5 >= 76dp target.
                    // At 262dp <= H < 402dp: (H - 22) / 5 >= 48dp accessibility floor.
                    vPad = GlassDimensions.dpToPx(getContext(), 4);
                    containerPad = GlassDimensions.dpToPx(getContext(), 2);
                    btnMargin = GlassDimensions.dpToPx(getContext(), 1);
                } else {
                    // Ultra-low viewport H < 262dp: minimal overhead (4dp total) to maximize button touch area
                    vPad = GlassDimensions.dpToPx(getContext(), 2);
                    containerPad = 0;
                    btnMargin = 0;
                }
            } else {
                if (heightDp >= 320f) {
                    // Compact phone viewport: tighten overhead so all 5 buttons >= 48dp
                    vPad = GlassDimensions.dpToPx(getContext(), 8);
                    containerPad = GlassDimensions.dpToPx(getContext(), 4);
                    btnMargin = GlassDimensions.dpToPx(getContext(), 2);
                } else {
                    // Very low phone viewport
                    vPad = GlassDimensions.dpToPx(getContext(), 4);
                    containerPad = GlassDimensions.dpToPx(getContext(), 2);
                    btnMargin = GlassDimensions.dpToPx(getContext(), 1);
                }
            }

            if (railContainer != null && railContainer.getLayoutParams() instanceof LayoutParams lp) {
                if (lp.topMargin != vPad || lp.bottomMargin != vPad) {
                    lp.topMargin = vPad;
                    lp.bottomMargin = vPad;
                    railContainer.setLayoutParams(lp);
                }
                railContainer.setPadding(0, containerPad, 0, containerPad);
            }

            int totalOverheadPx = (vPad * 2) + (containerPad * 2) + (btnMargin * 10);
            int availHeightPx = height - totalOverheadPx;
            int perBtnHeightPx = Math.max(0, availHeightPx / 5);

            if (actionButtons != null) {
                int targetMinHeightPx = GlassDimensions.dpToPx(getContext(), isCar ? 76 : 48);
                for (View btn : actionButtons) {
                    if (btn != null) {
                        // Clamp minimum height to available budget per button to prevent container clipping on H < 402dp
                        btn.setMinimumHeight(Math.min(targetMinHeightPx, perBtnHeightPx));
                        if (btn.getLayoutParams() instanceof LinearLayout.LayoutParams lp) {
                            if (lp.topMargin != btnMargin || lp.bottomMargin != btnMargin) {
                                lp.topMargin = btnMargin;
                                lp.bottomMargin = btnMargin;
                                btn.setLayoutParams(lp);
                            }
                        }
                    }
                }
            }

            if (isCar) {
                if (heightDp < 262f) {
                    Log.w(TAG, "Automotive viewport height (" + heightDp + "dp) cannot achieve 48dp accessibility floor without conflict. Floor threshold: 262dp. Achieved: " + (perBtnHeightPx / Math.max(0.1f, density)) + "dp.");
                } else if (heightDp < 402f) {
                    Log.w(TAG, "Automotive viewport height (" + heightDp + "dp) cannot fit 5 vertical buttons with 76dp touch target without conflict. Minimum required: 402dp. Achieved: " + (perBtnHeightPx / Math.max(0.1f, density)) + "dp (>= 48dp accessibility floor).");
                }
            } else if (!isCar && heightDp < 240f) {
                Log.w(TAG, "Phone viewport height (" + heightDp + "dp) cannot fit 5 vertical buttons with 48dp minimum touch target without conflict. Minimum required: 240dp.");
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int parentH = bottom - top;
        if (parentH > 0 && grabberHandle != null && !isDragging) {
            int handleH = grabberHandle.getHeight();
            if (handleH <= 0) handleH = GlassDimensions.dpToPx(getContext(), 104);
            int minMargin = GlassDimensions.dpToPx(getContext(), 12);
            int minY = minMargin;
            int maxY = Math.max(minY, parentH - handleH - minMargin);
            float ratio = getSavedYRatio();
            grabberHandle.setTranslationY(minY + ratio * (maxY - minY));
        }
        if (actionButtons != null) {
            float density = getResources().getDisplayMetrics().density;
            boolean isCar = isCar();
            float minTarget = isCar ? 76f : 48f;
            for (View btn : actionButtons) {
                if (btn != null && btn.getHeight() > 0) {
                    float btnHeightDp = btn.getHeight() / Math.max(0.1f, density);
                    if (isCar && btnHeightDp < 48f) {
                        Log.w(TAG, "Action button layout height (" + btnHeightDp + "dp) is below 48dp accessibility floor.");
                    } else if (btnHeightDp < (minTarget - 4f)) {
                        Log.w(TAG, "Action button layout height (" + btnHeightDp + "dp) is below " + minTarget + "dp touch target.");
                    }
                }
            }
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void expand() {
        if (isOpen || fullscreenHidden) return;
        isOpen = true;

        cancelPendingCallbacks();
        final int gen = animGeneration;

        int railWidth = railContainer.getWidth();
        if (railWidth <= 0) railWidth = GlassDimensions.dpToPx(getContext(), 76);
        float startX = isRight ? railWidth : -railWidth;

        scrimView.setVisibility(VISIBLE);
        railContainer.setVisibility(VISIBLE);
        railContainer.setTranslationX(startX);
        grabberHandle.setVisibility(GONE);

        // Look for focus in root window, not just within rail
        previouslyFocusedView = null;
        if (activity != null && activity.getAppActivity() != null) {
            View content = activity.getAppActivity().findViewById(android.R.id.content);
            if (content != null) previouslyFocusedView = content.findFocus();
        }
        if (previouslyFocusedView == null) {
            previouslyFocusedView = getRootView().findFocus();
        }

        updateSelectedRoute();
        updateGrabberHandleAccessibility();
        scheduleAutoHide();

        activeAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeAnimator.setDuration(ANIM_DURATION_MS);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        final float animDistance = startX;
        activeAnimator.addUpdateListener(animation -> {
            if (gen != animGeneration) return;
            float frac = (float) animation.getAnimatedValue();
            scrimView.setAlpha(frac);
            railContainer.setTranslationX(animDistance * (1f - frac));
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (gen != animGeneration) return;
                activeAnimator = null;
                railContainer.setTranslationX(0f);
                scrimView.setAlpha(1f);

                pendingFocusRunnable = () -> {
                    if (isOpen && isAttachedToWindow() && actionButtons != null && actionButtons.length > 0) {
                        View targetFocus = actionButtons[0];
                        for (View btn : actionButtons) {
                            if (btn.isSelected()) {
                                targetFocus = btn;
                                break;
                            }
                        }
                        targetFocus.requestFocus();
                        targetFocus.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
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
        final int gen = animGeneration;

        int railWidth = railContainer.getWidth();
        if (railWidth <= 0) railWidth = GlassDimensions.dpToPx(getContext(), 76);
        final float endX = isRight ? railWidth : -railWidth;

        updateGrabberHandleAccessibility();

        activeAnimator = ValueAnimator.ofFloat(0f, 1f);
        activeAnimator.setDuration(ANIM_DURATION_MS);
        activeAnimator.setInterpolator(new DecelerateInterpolator());
        activeAnimator.addUpdateListener(animation -> {
            if (gen != animGeneration) return;
            float frac = (float) animation.getAnimatedValue();
            scrimView.setAlpha(1f - frac);
            railContainer.setTranslationX(endX * frac);
        });
        activeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (gen != animGeneration) return;
                activeAnimator = null;
                railContainer.setVisibility(GONE);
                scrimView.setVisibility(GONE);
                if (!fullscreenHidden) {
                    grabberHandle.setVisibility(VISIBLE);
                }
                if (previouslyFocusedView != null && previouslyFocusedView.isAttachedToWindow() && previouslyFocusedView.isShown()) {
                    previouslyFocusedView.requestFocus();
                } else if (grabberHandle != null && grabberHandle.isAttachedToWindow() && !fullscreenHidden) {
                    grabberHandle.requestFocus();
                }
                previouslyFocusedView = null;
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
        updateGrabberHandleAccessibility();
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
        railContainer.setBackground(GlassDrawables.createGlassRailDrawable(getContext(), GlassDimensions.CARD_RADIUS_DP, isNight));
        if (innerGrabberPill != null) {
            innerGrabberPill.setBackground(GlassDrawables.createGlassGrabberSemicircleDrawable(getContext(), 44f, isNight, isRight));
        }
        if (ivLauncher != null) {
            ivLauncher.setColorFilter(colors.getTextPrimary());
        }
        updateGrabberHandleAccessibility();
        buildActionButtons(getContext());
    }

    private void cancelPendingCallbacks() {
        animGeneration++;
        cancelAutoHide();
        if (pendingFocusRunnable != null) {
            removeCallbacks(pendingFocusRunnable);
            pendingFocusRunnable = null;
        }
        if (activeAnimator != null) {
            activeAnimator.removeAllListeners();
            activeAnimator.removeAllUpdateListeners();
            activeAnimator.cancel();
            activeAnimator = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isOpen) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                cancelAutoHide();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                scheduleAutoHide();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isOpen) {
            scheduleAutoHide();
        }
        return super.dispatchKeyEvent(event);
    }

    public void scheduleAutoHide() {
        cancelAutoHide();
        if (isOpen) {
            autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS);
        }
    }

    public void cancelAutoHide() {
        autoHideHandler.removeCallbacks(autoHideRunnable);
    }

    public void resetAutoHideTimer() {
        scheduleAutoHide();
    }

    void setBackActionListenerForTesting(@Nullable Runnable listener) {
        this.backActionListenerForTesting = listener;
    }

    void setRightForTesting(boolean isRight) {
        this.isRight = isRight;
    }

    private boolean isInsideGrabberHandle(float y) {
        if (grabberHandle == null || grabberHandle.getVisibility() != VISIBLE) {
            return false;
        }
        float handleY = grabberHandle.getY();
        int handleH = grabberHandle.getHeight();
        if (handleH <= 0) handleH = GlassDimensions.dpToPx(getContext(), 104);
        return y >= handleY && y <= (handleY + handleH);
    }

    private boolean isAtRailEdge(float x) {
        int w = getWidth();
        if (w <= 0) return false;
        int edgeSlop = GlassDimensions.dpToPx(getContext(), 36);
        return !isRight ? (x <= edgeSlop) : (x >= w - edgeSlop);
    }

    private void performBackAction() {
        if (backActionListenerForTesting != null) {
            backActionListenerForTesting.run();
            return;
        }
        if (activity != null) {
            activity.onBackPressed();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isOpen || !isCar()) {
            return super.onInterceptTouchEvent(ev);
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isAtRailEdge(ev.getX()) && !isInsideGrabberHandle(ev.getY())) {
                    edgeDownX = ev.getX();
                    edgeDownY = ev.getY();
                    edgeTracking = true;
                    edgeBackIntercepted = false;
                } else {
                    edgeTracking = false;
                    edgeBackIntercepted = false;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!edgeTracking) {
                    return false;
                }
                float dx = ev.getX() - edgeDownX;
                float dy = ev.getY() - edgeDownY;
                int threshold = GlassDimensions.dpToPx(getContext(), 45);

                if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                    edgeTracking = false;
                    return false;
                }

                boolean inwardSwipe = !isRight ? (dx >= threshold) : (dx <= -threshold);
                if (inwardSwipe && Math.abs(dx) > 1.3f * Math.abs(dy)) {
                    edgeBackIntercepted = true;
                    edgeTracking = false;
                    performBackAction();
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                edgeTracking = false;
                edgeBackIntercepted = false;
                return false;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (edgeBackIntercepted) {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                edgeBackIntercepted = false;
            }
            return true;
        }
        if (!isOpen && isCar()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (isAtRailEdge(ev.getX()) && !isInsideGrabberHandle(ev.getY())) {
                        edgeDownX = ev.getX();
                        edgeDownY = ev.getY();
                        edgeTracking = true;
                        edgeBackIntercepted = false;
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (edgeTracking && !edgeBackIntercepted) {
                        float dx = ev.getX() - edgeDownX;
                        float dy = ev.getY() - edgeDownY;
                        int threshold = GlassDimensions.dpToPx(getContext(), 45);
                        if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                            edgeTracking = false;
                        } else {
                            boolean inwardSwipe = !isRight ? (dx >= threshold) : (dx <= -threshold);
                            if (inwardSwipe && Math.abs(dx) > 1.3f * Math.abs(dy)) {
                                edgeBackIntercepted = true;
                                edgeTracking = false;
                                performBackAction();
                                return true;
                            }
                        }
                    }
                    if (edgeBackIntercepted) {
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (edgeTracking || edgeBackIntercepted) {
                        edgeTracking = false;
                        edgeBackIntercepted = false;
                        return true;
                    }
                    break;
            }
        }
        return super.onTouchEvent(ev);
    }

    private float getSavedYRatio() {
        if (activity != null) {
            float r = activity.getPrefs().getGlassRailYRatioPref();
            return Math.max(0f, Math.min(1f, r));
        }
        return 0.5f;
    }

    private void saveYRatio(float ratio) {
        if (activity != null) {
            activity.getPrefs().setGlassRailYRatioPref(ratio);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelPendingCallbacks();
        cancelAutoHide();
    }
}
