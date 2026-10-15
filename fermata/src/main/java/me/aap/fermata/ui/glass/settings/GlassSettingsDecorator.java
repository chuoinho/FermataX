package me.aap.fermata.ui.glass.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassThemeColors;

/**
 * Decorator that transforms standard preference rows on Android Auto
 * into 64dp frosted glass cards with 20dp rounded corners.
 */
import me.aap.fermata.R;

public class GlassSettingsDecorator {

    private static class DecoratorState implements View.OnLayoutChangeListener, RecyclerView.OnChildAttachStateChangeListener {
        boolean isNight;

        DecoratorState(boolean isNight) {
            this.isNight = isNight;
        }

        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            int w = r - l;
            int h = b - t;
            if (w > 0 && h > 0) {
                v.setBackground(GlassDrawables.createBackgroundGradient(w, h, isNight));
            }
        }

        @Override
        public void onChildViewAttachedToWindow(@NonNull View view) {
            decorateRow(view, isNight);
        }

        @Override
        public void onChildViewDetachedFromWindow(@NonNull View view) {}
    }

    public static void apply(@NonNull RecyclerView recyclerView, boolean isNight) {
        Context ctx = recyclerView.getContext();
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

        DecoratorState state = (DecoratorState) recyclerView.getTag(R.id.dashboard_settings);
        if (state == null) {
            state = new DecoratorState(isNight);
            recyclerView.setTag(R.id.dashboard_settings, state);
            recyclerView.addOnLayoutChangeListener(state);
            recyclerView.addOnChildAttachStateChangeListener(state);
        } else {
            state.isNight = isNight;
        }

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

        GlassThemeColors gc = GlassThemeColors.get(ctx, isNight);
        view.setBackground(GlassDrawables.createGlassCardDrawable(ctx, 20f, isNight));
        styleViewTree(view, gc, isNight);
    }

    private static void styleViewTree(View view, GlassThemeColors gc, boolean isNight) {
        if (!view.isEnabled()) {
            if (view instanceof TextView tv) {
                tv.setTextColor(gc.getDisabledText());
            }
        } else if (view instanceof SwitchCompat sc) {
            ColorStateList thumbColors = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{}
                    },
                    new int[]{
                            gc.getActionText(),
                            gc.getDisabledText(),
                            isNight ? 0xFF94A3B8 : 0xFF64748B
                    }
            );
            int checkedTrackColor = (gc.getActionText() & 0x00FFFFFF) | 0x66000000;
            ColorStateList trackColors = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{}
                    },
                    new int[]{
                            checkedTrackColor,
                            0x15000000,
                            isNight ? 0x33FFFFFF : 0x20000000
                    }
            );
            sc.setThumbTintList(thumbColors);
            sc.setTrackTintList(trackColors);
        } else if (view instanceof CompoundButton cb) {
            ColorStateList buttonColors = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{}
                    },
                    new int[]{
                            gc.getActionText(),
                            gc.getDisabledText(),
                            gc.getTextSecondary()
                    }
            );
            cb.setButtonTintList(buttonColors);
            cb.setTextColor(gc.getTextPrimary());
        } else if (view instanceof SeekBar sb) {
            sb.setThumbTintList(ColorStateList.valueOf(gc.getActionText()));
            sb.setProgressTintList(ColorStateList.valueOf(gc.getActionText()));
            sb.setProgressBackgroundTintList(ColorStateList.valueOf(isNight ? 0x33FFFFFF : 0x20000000));
        } else if (view instanceof EditText et) {
            et.setTextColor(gc.getInputText());
            et.setHintTextColor(gc.getInputHint());
        } else if (view instanceof TextView tv) {
            int id = tv.getId();
            if (id == me.aap.utils.R.id.pref_title || id == android.R.id.title) {
                tv.setTextColor(gc.getTextPrimary());
                tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            } else if (id == me.aap.utils.R.id.pref_subtitle || id == android.R.id.summary) {
                tv.setTextColor(gc.getTextSecondary());
            } else if (id == me.aap.utils.R.id.pref_value) {
                tv.setTextColor(gc.getActionText());
            } else {
                tv.setTextColor(gc.getTextPrimary());
            }
        } else if (view instanceof ImageView iv) {
            int id = iv.getId();
            boolean isArt = iv.getDrawable() instanceof BitmapDrawable;
            if (!isArt) {
                if (id == me.aap.utils.R.id.pref_icon || id == android.R.id.icon) {
                    iv.setColorFilter(gc.getActionText());
                } else {
                    iv.setColorFilter(gc.getTextSecondary());
                }
            }
        }

        if (view instanceof ViewGroup vg) {
            for (int i = 0, count = vg.getChildCount(); i < count; i++) {
                styleViewTree(vg.getChildAt(i), gc, isNight);
            }
        }
    }
}
