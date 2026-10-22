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

	public static Drawable createGlassRailDrawable(Context context, float radiusDp, boolean isNight) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		GradientDrawable drawable = new GradientDrawable();
		int bgColor = isNight ? 0xFF14181B : 0xFFF4F3F1;
		int borderColor = isNight ? 0x40FFFFFF : 0x28000000;
		drawable.setColor(bgColor);
		drawable.setCornerRadius(radiusDp * density);
		drawable.setStroke(Math.max(1, (int) (1.2f * density)), borderColor);
		return drawable;
	}

	public static Drawable createGlassRailDrawable(Context context, float radiusDp) {
		return createGlassRailDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}

	public static Drawable createGlassGrabberPillDrawable(Context context, float radiusDp, boolean isNight) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		GradientDrawable drawable = new GradientDrawable();
		int bgColor = isNight ? 0xFF14181B : 0xFFF4F3F1;
		drawable.setColor(bgColor);
		drawable.setCornerRadius(radiusDp * density);
		return drawable;
	}

	public static Drawable createGlassGrabberPillDrawable(Context context, float radiusDp) {
		return createGlassGrabberPillDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}

	public static Drawable createGlassGrabberSemicircleDrawable(Context context, float radiusDp, boolean isNight, boolean isRight) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		float r = radiusDp * density;
		GradientDrawable drawable = new GradientDrawable();
		int bgColor = isNight ? 0xFF14181B : 0xFFF4F3F1;
		int borderColor = isNight ? 0x40FFFFFF : 0x28000000;
		drawable.setColor(bgColor);
		drawable.setStroke(Math.max(1, (int) (1.2f * density)), borderColor);
		if (isRight) {
			drawable.setCornerRadii(new float[]{r, r, 0f, 0f, 0f, 0f, r, r});
		} else {
			drawable.setCornerRadii(new float[]{0f, 0f, r, r, r, r, 0f, 0f});
		}
		return drawable;
	}

	public static Drawable createGlassGrabberSemicircleDrawable(Context context, float radiusDp, boolean isRight) {
		return createGlassGrabberSemicircleDrawable(context, radiusDp, GlassThemeColors.isNight(context), isRight);
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

	public static Drawable createGlassPillStateDrawable(Context context, float radiusDp, boolean isNight) {
		GlassThemeColors colors = GlassThemeColors.get(context, isNight);
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();

		GradientDrawable foc = new GradientDrawable();
		foc.setColor(isNight ? 0x24FFFFFF : 0x15000000);
		foc.setCornerRadius(radiusDp * density);
		foc.setStroke(Math.max(1, (int) (1.5f * density)), colors.getActionText());

		GradientDrawable prs = new GradientDrawable();
		prs.setColor(isNight ? 0x33FFFFFF : 0x20000000);
		prs.setCornerRadius(radiusDp * density);
		prs.setStroke(Math.max(1, (int) (1.0f * density)), colors.getChipBorder());

		GradientDrawable norm = new GradientDrawable();
		norm.setColor(colors.getChipBg());
		norm.setCornerRadius(radiusDp * density);
		norm.setStroke(Math.max(1, (int) (1.0f * density)), colors.getChipBorder());

		sld.addState(new int[]{android.R.attr.state_focused}, foc);
		sld.addState(new int[]{android.R.attr.state_pressed}, prs);
		sld.addState(new int[]{}, norm);
		return sld;
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
		int bgColor = isNight ? 0xFF181C1E : 0xFFF4F3F1;
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

	public static Drawable createGlassPreferenceDrawable(Context context, float radiusDp, boolean isNight) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		android.graphics.drawable.StateListDrawable sld = new android.graphics.drawable.StateListDrawable();

		GradientDrawable active = new GradientDrawable();
		active.setColor(isNight ? 0x2500E5FF : 0x1800E5FF);
		active.setCornerRadius(radiusDp * density);
		active.setStroke(Math.max(1, (int) (1.0f * density)), isNight ? 0x8000E5FF : 0x80007A87);

		Drawable normal = createGlassCardDrawable(context, radiusDp, isNight);

		sld.addState(new int[]{android.R.attr.state_focused}, active);
		sld.addState(new int[]{android.R.attr.state_selected}, active);
		sld.addState(new int[]{android.R.attr.state_pressed}, active);
		sld.addState(new int[]{}, normal);
		return sld;
	}

	public static Drawable createGlassPreferenceDrawable(Context context, float radiusDp) {
		return createGlassPreferenceDrawable(context, radiusDp, GlassThemeColors.isNight(context));
	}
}
