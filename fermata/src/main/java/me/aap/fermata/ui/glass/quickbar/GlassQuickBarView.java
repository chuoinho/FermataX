package me.aap.fermata.ui.glass.quickbar;

import android.content.Context;
import android.graphics.Color;
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
 * 6-Slot Automotive Quick Navigation Bar.
 * Slots 0-3: User customizable (default: empty with [+] Add Shortcut).
 * Slot 4: Fixed Addons Catalog.
 * Slot 5: Fixed Exit.
 */
public class GlassQuickBarView extends LinearLayout {

    public interface Listener {
        void onShortcutClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut);
        void onEmptySlotClicked(int slotIndex);
        void onShortcutLongClicked(int slotIndex, @NonNull GlassQuickShortcut shortcut);
        void onAddonsCatalogClicked();
        void onExitClicked();
    }

    private final boolean isNight;
    private final GlassThemeColors colors;
    private GlassQuickBarManager manager;
    private Listener listener;
    private int activeSlotCount = 6;

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
        int padding = GlassDimensions.dpToPx(context, 4);
        setPadding(padding, padding, padding, padding);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width > 0) {
            float widthDp = width / getResources().getDisplayMetrics().density;
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
        renderSlots();
    }

    private void renderSlots() {
        removeAllViews();
        if (manager == null) return;
        Context context = getContext();
        slotViews = new View[activeSlotCount];

        for (int i = 0; i < activeSlotCount; i++) {
            final int slotIndex = i;
            final GlassQuickShortcut shortcut;
            final boolean isAddonCatalog = (activeSlotCount == 4 && i == 2) || (activeSlotCount == 6 && i == 4);
            final boolean isExit = (activeSlotCount == 4 && i == 3) || (activeSlotCount == 6 && i == 5);

            if (isAddonCatalog) {
                shortcut = GlassQuickBarManager.SLOT_ADDON_CATALOG;
            } else if (isExit) {
                shortcut = GlassQuickBarManager.SLOT_EXIT;
            } else {
                shortcut = manager.getCustomShortcut(i);
            }

            LinearLayout slotLayout = new LinearLayout(context);
            slotLayout.setOrientation(LinearLayout.VERTICAL);
            slotLayout.setGravity(Gravity.CENTER);
            int itemPadding = GlassDimensions.dpToPx(context, 4);
            slotLayout.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);

            LayoutParams lp = new LayoutParams(0, GlassDimensions.dpToPx(context, 58), 1.0f);
            if (i > 0) lp.leftMargin = GlassDimensions.dpToPx(context, 6);
            slotLayout.setLayoutParams(lp);

            slotLayout.setBackground(GlassDrawables.createGlassPillDrawable(context, GlassDimensions.PILL_RADIUS_DP, isNight));

            if (shortcut == null) {
                // Empty customizable slot: [+] Add
                TextView tvPlus = new TextView(context);
                tvPlus.setText("+");
                tvPlus.setTextSize(18f);
                tvPlus.setTypeface(Typeface.DEFAULT_BOLD);
                tvPlus.setTextColor(colors.getTextSecondary());
                tvPlus.setGravity(Gravity.CENTER);
                slotLayout.addView(tvPlus);

                TextView tvLabel = new TextView(context);
                tvLabel.setText("Add");
                tvLabel.setTextSize(10f);
                tvLabel.setTextColor(colors.getTextSecondary());
                tvLabel.setGravity(Gravity.CENTER);
                slotLayout.addView(tvLabel);

                slotLayout.setOnClickListener(v -> {
                    if (listener != null) listener.onEmptySlotClicked(slotIndex);
                });
            } else {
                // Assigned or fixed slot
                ImageView ivIcon = new ImageView(context);
                int iconSize = GlassDimensions.dpToPx(context, 22);
                ivIcon.setLayoutParams(new LayoutParams(iconSize, iconSize));
                if (shortcut.getIconResId() != 0) {
                    ivIcon.setImageResource(shortcut.getIconResId());
                } else {
                    ivIcon.setImageResource(R.drawable.view_grid);
                }
                slotLayout.addView(ivIcon);

                TextView tvLabel = new TextView(context);
                tvLabel.setText(shortcut.getTitle());
                tvLabel.setTextSize(11f);
                tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
                if (isExit) {
                    tvLabel.setTextColor(Color.parseColor("#FF5252")); // Soft red for exit
                } else {
                    tvLabel.setTextColor(colors.getTextPrimary());
                }
                tvLabel.setGravity(Gravity.CENTER);
                tvLabel.setMaxLines(1);
                slotLayout.addView(tvLabel);

                slotLayout.setOnClickListener(v -> {
                    if (listener == null) return;
                    if (isAddonCatalog) {
                        listener.onAddonsCatalogClicked();
                    } else if (isExit) {
                        listener.onExitClicked();
                    } else {
                        listener.onShortcutClicked(slotIndex, shortcut);
                    }
                });

                if (!isAddonCatalog && !isExit) {
                    slotLayout.setOnLongClickListener(v -> {
                        if (listener != null) {
                            listener.onShortcutLongClicked(slotIndex, shortcut);
                            return true;
                        }
                        return false;
                    });
                }
            }

            slotViews[i] = slotLayout;
            addView(slotLayout);
        }
    }
}
