package me.aap.fermata.ui.glass.theme;

import android.content.Context;

public final class GlassDimensions {
	public static final float CARD_RADIUS_DP = 28f;
	public static final float PILL_RADIUS_DP = 20f;
	public static final float ICON_RADIUS_DP = 16f;
	public static final float MIN_TOUCH_TARGET_DP = 56f;
	public static final float DELEGATED_TOUCH_TARGET_DP = 64f;

	private GlassDimensions() {
	}

	public static int dpToPx(Context context, float dp) {
		float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
		return Math.round(dp * density);
	}

	public static boolean isCar(Context context) {
		if (context == null) return false;
		try {
			me.aap.fermata.ui.activity.MainActivityDelegate a = me.aap.fermata.ui.activity.MainActivityDelegate.get(context);
			return a != null && a.isCarActivity();
		} catch (Throwable ignored) {
			return false;
		}
	}
}
