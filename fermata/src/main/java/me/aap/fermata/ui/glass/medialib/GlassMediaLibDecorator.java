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
public final class GlassMediaLibDecorator {

    private GlassMediaLibDecorator() {}

    public static void apply(@NonNull View root, @NonNull RecyclerView recyclerView, boolean isNight) {
        Context ctx = root.getContext();

        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0) {
                root.setBackground(GlassDrawables.createBackgroundGradient(w, h, isNight));
            }
        });
        int rw = root.getWidth();
        int rh = root.getHeight();
        if (rw > 0 && rh > 0) {
            root.setBackground(GlassDrawables.createBackgroundGradient(rw, rh, isNight));
        } else {
            root.setBackgroundColor(0xFF0A0F1D);
        }

        recyclerView.setClipToPadding(false);
        int pad = GlassDimensions.dpToPx(ctx, 12);
        recyclerView.setPadding(pad, pad, pad, pad);

        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                decorateItem(view, isNight);
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {}
        });

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
        view.setBackground(GlassDrawables.createGlassCardDrawable(ctx, 16f, isNight));
    }
}
