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
            recyclerView.setBackgroundColor(0xFF0A0F1D);
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

        view.setBackground(GlassDrawables.createGlassCardDrawable(ctx, 20f, isNight));
    }
}
