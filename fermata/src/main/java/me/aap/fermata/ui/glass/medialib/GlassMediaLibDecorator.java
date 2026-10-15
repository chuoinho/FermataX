package me.aap.fermata.ui.glass.medialib;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;

/**
 * Decorator that styles MediaLib screens (TV, Radio, Folders, Podcasts, etc.)
 * with frosted glass cards and a radial background gradient in Glassmorphism theme.
 */
import me.aap.fermata.R;

public final class GlassMediaLibDecorator {

    private GlassMediaLibDecorator() {}

    private static class DecoratorState implements View.OnLayoutChangeListener, RecyclerView.OnChildAttachStateChangeListener {
        final View root;
        boolean isNight;

        DecoratorState(View root, boolean isNight) {
            this.root = root;
            this.isNight = isNight;
        }

        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0) {
                root.setBackground(GlassDrawables.createBackgroundGradient(w, h, isNight));
            }
        }

        @Override
        public void onChildViewAttachedToWindow(@NonNull View view) {
            decorateItem(view, isNight);
        }

        @Override
        public void onChildViewDetachedFromWindow(@NonNull View view) {}
    }

    public static void apply(@NonNull View root, @NonNull RecyclerView recyclerView, boolean isNight) {
        Context ctx = root.getContext();

        int rw = root.getWidth();
        int rh = root.getHeight();
        if (rw > 0 && rh > 0) {
            root.setBackground(GlassDrawables.createBackgroundGradient(rw, rh, isNight));
        } else {
            root.setBackgroundColor(isNight ? 0xFF07090D : 0xFFF8FAFC);
        }

        recyclerView.setClipToPadding(false);
        int pad = GlassDimensions.dpToPx(ctx, 12);
        recyclerView.setPadding(pad, pad, pad, pad);

        DecoratorState state = (DecoratorState) recyclerView.getTag(R.id.dashboard_settings);
        if (state == null) {
            state = new DecoratorState(root, isNight);
            recyclerView.setTag(R.id.dashboard_settings, state);
            root.addOnLayoutChangeListener(state);
            recyclerView.addOnChildAttachStateChangeListener(state);
        } else {
            state.isNight = isNight;
        }

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            decorateItem(recyclerView.getChildAt(i), isNight);
        }
    }

    public static void decorateItem(@NonNull View view, boolean isNight) {
        Context ctx = view.getContext();
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            int marginH = GlassDimensions.dpToPx(ctx, 6);
            int marginV = GlassDimensions.dpToPx(ctx, 4);
            mlp.setMargins(marginH, marginV, marginH, marginV);
            view.setLayoutParams(mlp);
        }

        int padH = GlassDimensions.dpToPx(ctx, 12);
        int padV = GlassDimensions.dpToPx(ctx, 8);
        view.setPadding(padH, padV, padH, padV);

        me.aap.fermata.ui.glass.theme.GlassThemeColors gc =
                me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, isNight);
        android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();

        android.graphics.drawable.GradientDrawable sel = new android.graphics.drawable.GradientDrawable();
        sel.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        int selFill = (gc.getActionText() & 0x00FFFFFF) | (isNight ? 0x2A000000 : 0x20000000);
        sel.setColor(selFill);
        sel.setStroke(Math.round(GlassDimensions.dpToPx(ctx, 1.5f)), gc.getActionText());

        android.graphics.drawable.GradientDrawable foc = new android.graphics.drawable.GradientDrawable();
        foc.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        foc.setCornerRadius(GlassDimensions.dpToPx(ctx, 16f));
        foc.setColor(isNight ? 0x24FFFFFF : 0x15000000);
        foc.setStroke(Math.round(GlassDimensions.dpToPx(ctx, 1.5f)), gc.getActionText());

        android.graphics.drawable.GradientDrawable prs = new android.graphics.drawable.GradientDrawable();
        prs.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        prs.setCornerRadius(GlassDimensions.dpToPx(ctx, 16f));
        prs.setColor(isNight ? 0x33FFFFFF : 0x20000000);
        prs.setStroke(Math.round(GlassDimensions.dpToPx(ctx, 1f)), gc.getCardBorder());

        android.graphics.drawable.Drawable norm = GlassDrawables.createGlassCardDrawable(ctx, 16f, isNight);

        sld.addState(new int[]{android.R.attr.state_selected}, sel);
        sld.addState(new int[]{android.R.attr.state_activated}, sel);
        sld.addState(new int[]{android.R.attr.state_focused}, foc);
        sld.addState(new int[]{android.R.attr.state_pressed}, prs);
        sld.addState(new int[]{}, norm);

        view.setBackground(sld);

        if (view instanceof me.aap.fermata.ui.view.MediaItemView miv) {
            android.widget.TextView title = miv.getTitle();
            if (title != null) {
                title.setTextColor(gc.getTextPrimary());
                title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            }
            android.widget.TextView subtitle = miv.getSubtitle();
            if (subtitle != null) {
                subtitle.setTextColor(gc.getTextSecondary());
                subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            }
            android.widget.ImageView icon = miv.getIcon();
            if (icon != null) {
                icon.setColorFilter(gc.getTextPrimary());
            }
        }
    }
}
