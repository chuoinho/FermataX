package me.aap.fermata.ui.glass.theme;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

public final class GlassDrawables {

	private GlassDrawables() {
	}

	public static Drawable createGlassCardDrawable(Context context, float radiusDp, boolean isNight) {
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(colors.getCardBg());
		drawable.setCornerRadius(radiusDp * density);
		drawable.setStroke(Math.max(1, (int) (1.0f * density)), colors.getCardBorder());
		return drawable;
	}

	public static Drawable createGlassCardDrawable(Context context, float radiusDp) {
		return createGlassCardDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}

	public static Drawable createGlassPillDrawable(Context context, float radiusDp, boolean isNight) {
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(colors.getChipBg());
		drawable.setCornerRadius(radiusDp * density);
		drawable.setStroke(Math.max(1, (int) (1.0f * density)), colors.getChipBorder());
		return drawable;
	}

	public static Drawable createGlassPillDrawable(Context context, float radiusDp) {
		return createGlassPillDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}

	public static Drawable createGlassInputDrawable(Context context, float radiusDp, boolean isNight) {
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(colors.getInputBg());
		drawable.setCornerRadius(radiusDp * density);
		drawable.setStroke(Math.max(1, (int) (1.0f * density)), colors.getInputBorder());
		return drawable;
	}

	public static Drawable createGlassInputDrawable(Context context, float radiusDp) {
		return createGlassInputDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}

	public static Drawable createGlassDialogDrawable(Context context, float radiusDp, boolean isNight) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		GradientDrawable drawable = new GradientDrawable();
		int bgColor = isNight ? 0xF0181C1E : 0xF8F4F3F1;
		int borderColor = isNight ? 0x33FFFFFF : 0x20000000;
		drawable.setColor(bgColor);
		drawable.setCornerRadius(radiusDp * density);
		drawable.setStroke(Math.max(1, (int) (1.0f * density)), borderColor);
		return drawable;
	}

	public static Drawable createGlassDialogDrawable(Context context, float radiusDp) {
		return createGlassDialogDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}

	public static Drawable createBackgroundGradient(int width, int height, boolean isNight) {
		GlassThemeColors colors = isNight ? GlassThemeColors.get(null, true) : GlassThemeColors.get(null, false);
		GradientDrawable gradient = new GradientDrawable();
		gradient.setGradientType(GradientDrawable.RADIAL_GRADIENT);
		gradient.setGradientCenter(0.2f, 0.2f);
		float radius = Math.max(1, Math.max(width, height)) * 1.2f;
		gradient.setGradientRadius(radius);
		gradient.setColors(new int[]{colors.getNenA(), colors.getNenB(), colors.getNenC()});
		return gradient;
	}

	public static Drawable createGlassItemHighlightDrawable(Context context, boolean isNight) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();

		GradientDrawable active = new GradientDrawable();
		active.setColor(isNight ? 0x2500E5FF : 0x1800E5FF);
		active.setCornerRadius(12f * density);
		active.setStroke(Math.max(1, (int) (1.0f * density)), isNight ? 0x8000E5FF : 0x80007A87);

		GradientDrawable normal = new GradientDrawable();
		normal.setColor(android.graphics.Color.TRANSPARENT);

		sld.addState(new int[]{android.R.attr.state_focused}, active);
		sld.addState(new int[]{android.R.attr.state_selected}, active);
		sld.addState(new int[]{android.R.attr.state_pressed}, active);
		sld.addState(new int[]{}, normal);
		return sld;
	}

	public static Drawable createGlassItemHighlightDrawable(Context context) {
		return createGlassItemHighlightDrawable(context, GlassThemeColors.isNight(context));
	}
}
