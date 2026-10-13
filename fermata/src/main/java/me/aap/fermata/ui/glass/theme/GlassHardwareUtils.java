package me.aap.fermata.ui.glass.theme;

import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;

/**
 * GPU hardware acceleration and outline clipping utilities ported and adapted
 * from CarStream (com.carstream.tech.launcher.Ui).
 */
public final class GlassHardwareUtils {

	private GlassHardwareUtils() {
	}

	/**
	 * Sets hardware-accelerated rounded outline provider for the given view.
	 * Avoids software canvas anti-aliasing seams and rendering glitches on complex views
	 * (e.g. VideoView, SurfaceView, MapView, WebView).
	 */
	public static void clipRound(View view, float cornerRadiusPx) {
		if (view == null) return;
		view.setOutlineProvider(new ViewOutlineProvider() {
			@Override
			public void getOutline(View v, Outline outline) {
				outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerRadiusPx);
			}
		});
		view.setClipToOutline(true);
	}

	/**
	 * Creates a solid rounded GradientDrawable.
	 */
	public static GradientDrawable round(@ColorInt int color, float cornerRadiusPx) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(cornerRadiusPx);
		return d;
	}

	/**
	 * Creates a rounded GradientDrawable with border stroke.
	 */
	public static GradientDrawable roundStroke(@ColorInt int color, float cornerRadiusPx,
			int strokeWidthPx, @ColorInt int strokeColor) {
		GradientDrawable d = round(color, cornerRadiusPx);
		d.setStroke(strokeWidthPx, strokeColor);
		return d;
	}
}
