package me.aap.fermata.ui.glass.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;

/**
 * Decorator that transforms standard preference rows on Android Auto
 * into 64dp frosted glass cards with 20dp rounded corners.
 */
public class GlassSettingsDecorator {

    public static void apply(@NonNull RecyclerView recyclerView, boolean isNight) {
        Context ctx = recyclerView.getContext();
        recyclerView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0) {
                recyclerView.setBackground(GlassDrawables.createBackgroundGradient(w, h, isNight));
            }
        });
        int rw = recyclerView.getWidth();
        int rh = recyclerView.getHeight();
        if (rw > 0 && rh > 0) {
            recyclerView.setBackground(GlassDrawables.createBackgroundGradient(rw, rh, isNight));
        } else {
            recyclerView.setBackgroundColor(isNight ? 0xFF07090D : 0xFFF8FAFC);
        }
        recyclerView.setClipToPadding(false);
        int pad = GlassDimensions.dpToPx(ctx, 12);
        recyclerView.setPadding(pad, pad, pad, pad);

        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                decorateRow(view, isNight);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            decorateRow(recyclerView.getChildAt(i), isNight);
        }
    }

    public static void decorateRow(@NonNull View view, boolean isNight) {
        Context ctx = view.getContext();

        int minHeight = GlassDimensions.dpToPx(ctx, 64);
        view.setMinimumHeight(minHeight);

        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            int marginH = GlassDimensions.dpToPx(ctx, 8);
            int marginV = GlassDimensions.dpToPx(ctx, 4);
            mlp.setMargins(marginH, marginV, marginH, marginV);
            view.setLayoutParams(mlp);
        }

        int padH = GlassDimensions.dpToPx(ctx, 16);
        int padV = GlassDimensions.dpToPx(ctx, 8);
        view.setPadding(padH, padV, padH, padV);

        me.aap.fermata.ui.glass.theme.GlassThemeColors gc =
                me.aap.fermata.ui.glass.theme.GlassThemeColors.get(ctx, isNight);
        view.setBackground(GlassDrawables.createGlassCardDrawable(ctx, 20f, isNight));
        styleViewTree(view, gc, isNight);
    }

    private static void styleViewTree(View view, me.aap.fermata.ui.glass.theme.GlassThemeColors gc, boolean isNight) {
        if (view instanceof android.widget.TextView tv) {
            if (tv.getId() == android.R.id.title) {
                tv.setTextColor(gc.getTextPrimary());
                tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            } else if (tv.getId() == android.R.id.summary) {
                tv.setTextColor(gc.getTextSecondary());
            } else {
                tv.setTextColor(gc.getTextPrimary());
            }
        } else if (view instanceof androidx.appcompat.widget.SwitchCompat sc) {
            sc.setThumbTintList(android.content.res.ColorStateList.valueOf(gc.getActionText()));
            sc.setTrackTintList(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{isNight ? 0x6638BDF8 : 0x660284C7, isNight ? 0x33FFFFFF : 0x20000000}
            ));
        } else if (view instanceof android.widget.SeekBar sb) {
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(gc.getActionText()));
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(gc.getActionText()));
        } else if (view instanceof android.widget.ImageView iv) {
            if (iv.getId() != android.R.id.icon) {
                iv.setColorFilter(gc.getTextSecondary());
            }
        }

        if (view instanceof ViewGroup vg) {
            for (int i = 0, count = vg.getChildCount(); i < count; i++) {
                styleViewTree(vg.getChildAt(i), gc, isNight);
            }
        }
    }
}
