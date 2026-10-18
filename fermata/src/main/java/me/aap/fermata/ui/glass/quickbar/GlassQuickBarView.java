package me.aap.fermata.ui.glass.quickbar;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Automotive Quick Navigation Dock dedicated 100% to customizable shortcuts.
 * Adapts between 4 and 6 slots based on actual container width:
 * - Container width < 500dp: displays slots 0-3.
 * - Container width >= 500dp: displays slots 0-5.
 * Empty slots show [+] and "Add" / "Thêm"; assigned slots display icon and title.
 */
public class GlassQuickBarView extends LinearLayout {

    public interface Listener {
        void onShortcutClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut);
        void onEmptySlotClicked(int slotIndex);
        void onShortcutLongClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut);
    }

    private boolean isNight;
    private GlassThemeColors colors;
    private GlassQuickBarManager manager;
    private Listener listener;
    private int activeSlotCount = 4;

    private View[] slotViews;

    public GlassQuickBarView(@NonNull Context context) {
        this(context, null);
    }

    public GlassQuickBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlassQuickBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);

        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER);
        boolean isCar = GlassDimensions.isCar(context);
        int padH = GlassDimensions.dpToPx(context, 4);
        int padV = isCar ? 0 : GlassDimensions.dpToPx(context, 4);
        setPadding(padH, padV, padH, padV);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > 0) {
            float widthDp = width / getResources().getDisplayMetrics().density;
            // 4 slots on narrow container (< 550dp), 6 slots when space allows (>= 550dp)
            int targetSlots = (widthDp < 550f) ? 4 : 6;
            if (targetSlots != activeSlotCount) {
                activeSlotCount = targetSlots;
                renderSlots();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public int getActiveSlotCount() {
        return activeSlotCount;
    }

    public void setActiveSlotCount(int count) {
        if ((count == 4 || count == 6) && count != activeSlotCount) {
            activeSlotCount = count;
            renderSlots();
        }
    }

    public void init(@NonNull GlassQuickBarManager manager, @Nullable Listener listener) {
        this.manager = manager;
        this.listener = listener;
        renderSlots();
    }

    public void refresh() {
        if (manager != null) {
            manager.reload();
        }
        renderSlots();
    }

    public void refreshAppearance() {
        Context context = getContext();
        this.isNight = GlassThemeColors.isNight(context);
        this.colors = GlassThemeColors.get(context, isNight);
        renderSlots();
    }

    private void renderSlots() {
        removeAllViews();
        if (manager == null) return;
        Context context = getContext();
        slotViews = new View[activeSlotCount];

        boolean isCar = GlassDimensions.isCar(context);

        for (int i = 0; i < activeSlotCount; i++) {
            final int slotIndex = i;
            final GlassQuickShortcut shortcut = manager.getSlot(i);

            LinearLayout slotLayout = new LinearLayout(context);
            slotLayout.setOrientation(LinearLayout.VERTICAL);
            slotLayout.setGravity(Gravity.CENTER);
            int itemPadding = GlassDimensions.dpToPx(context, 4);
            slotLayout.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);

            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            if (i > 0) lp.leftMargin = GlassDimensions.dpToPx(context, 6);
            slotLayout.setLayoutParams(lp);
            slotLayout.setMinimumHeight(GlassDimensions.dpToPx(context, isCar ? 76 : 48));

            slotLayout.setBackground(GlassDrawables.createGlassPillStateDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));
            slotLayout.setFocusable(true);
            slotLayout.setFocusableInTouchMode(false);

            if (shortcut == null) {
                // Empty customizable slot: [+] Add
                TextView tvPlus = new TextView(context);
                tvPlus.setText("+");
                tvPlus.setTextSize(18f);
                tvPlus.setTypeface(Typeface.DEFAULT_BOLD);
                tvPlus.setTextColor(colors.getTextSecondary());
                tvPlus.setGravity(Gravity.CENTER);
                slotLayout.addView(tvPlus);

                String addText = safeGetString(context, R.string.glass_quick_add, "Add");
                TextView tvLabel = new TextView(context);
                tvLabel.setText(addText);
                tvLabel.setTextSize(10f);
                tvLabel.setTextColor(colors.getTextSecondary());
                tvLabel.setGravity(Gravity.CENTER);
                slotLayout.addView(tvLabel);

                String addDesc = addText + " (Slot " + (i + 1) + ")";
                slotLayout.setContentDescription(addDesc);

                slotLayout.setOnClickListener(v -> {
                    if (listener != null) listener.onEmptySlotClicked(slotIndex);
                });
            } else {
                // Assigned shortcut slot
                ImageView ivIcon = new ImageView(context);
                int iconSize = GlassDimensions.dpToPx(context, 22);
                ivIcon.setLayoutParams(new LayoutParams(iconSize, iconSize));
                int iconRes = shortcut.getIconResId();
                if ("addon".equals(shortcut.getType()) || shortcut.getId().startsWith("addon:")) {
                    me.aap.fermata.addon.AddonInfo info = GlassQuickBarManager.resolveAddonInfo(shortcut, context);
                    if (info != null && info.icon != 0) {
                        iconRes = info.icon;
                    }
                }
                if (iconRes != 0) {
                    ivIcon.setImageResource(iconRes);
                } else {
                    ivIcon.setImageResource(R.drawable.view_grid);
                }
                ivIcon.setColorFilter(colors.getTextPrimary());
                slotLayout.addView(ivIcon);

                TextView tvLabel = new TextView(context);
                tvLabel.setText(shortcut.getTitle());
                tvLabel.setTextSize(11f);
                tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
                tvLabel.setTextColor(colors.getTextPrimary());
                tvLabel.setGravity(Gravity.CENTER);
                tvLabel.setMaxLines(1);
                slotLayout.addView(tvLabel);

                slotLayout.setContentDescription(shortcut.getTitle() + " (Slot " + (i + 1) + ")");

                slotLayout.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onShortcutClicked(slotIndex, shortcut);
                    }
                });

                slotLayout.setOnLongClickListener(v -> {
                    if (listener != null) {
                        listener.onShortcutLongClicked(slotIndex, shortcut);
                        return true;
                    }
                    return false;
                });
            }

            slotViews[i] = slotLayout;
            addView(slotLayout);
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
}
