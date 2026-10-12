package me.aap.fermata.ui.glass.catalog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Frosted glass card view holder for individual catalog items.
 */
public class GlassAddonCardHolder extends RecyclerView.ViewHolder {

    public final ImageView iconView;
    public final TextView titleView;
    public final TextView descView;

    public GlassAddonCardHolder(@NonNull View itemView, @NonNull ImageView iconView,
                                @NonNull TextView titleView, @NonNull TextView descView) {
        super(itemView);
        this.iconView = iconView;
        this.titleView = titleView;
        this.descView = descView;
    }

    public static GlassAddonCardHolder create(@NonNull Context ctx, boolean isNight) {
        GlassThemeColors colors = GlassThemeColors.get(ctx, isNight);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        int pad = GlassDimensions.dpToPx(ctx, 16);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(GlassDrawables.createGlassCardDrawable(ctx, 20f, isNight));
        card.setClickable(true);
        card.setFocusable(true);

        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = GlassDimensions.dpToPx(ctx, 6);
        lp.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(lp);

        // Icon
        ImageView icon = new ImageView(ctx);
        int iconSize = GlassDimensions.dpToPx(ctx, 44);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.bottomMargin = GlassDimensions.dpToPx(ctx, 10);
        icon.setLayoutParams(iconLp);
        card.addView(icon);

        // Title
        TextView title = new TextView(ctx);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colors.getTextPrimary());
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        title.setLayoutParams(titleLp);
        card.addView(title);

        // Subtitle / Description
        TextView desc = new TextView(ctx);
        desc.setTextSize(11f);
        desc.setTextColor(colors.getTextSecondary());
        desc.setGravity(Gravity.CENTER);
        desc.setMaxLines(2);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = GlassDimensions.dpToPx(ctx, 4);
        desc.setLayoutParams(descLp);
        card.addView(desc);

        return new GlassAddonCardHolder(card, icon, title, desc);
    }

    public void bind(@NonNull GlassAddonsCatalogFragment.CatalogItem item, boolean isNight) {
        titleView.setText(item.title);
        descView.setText(item.description);

        if (item.iconRes != 0) {
            iconView.setImageResource(item.iconRes);
            if (item.brandColor != 0) {
                iconView.setImageTintList(ColorStateList.valueOf(item.brandColor));
            } else {
                iconView.setImageTintList(null);
            }
        }
    }
}
