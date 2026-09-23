package me.aap.fermata.ui.smarttop;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/** Creates the rounded content, border and ripple used by the SmartTop root background. */
final class SmartTopCardBackgroundFactory {
	private SmartTopCardBackgroundFactory() {
	}

	static RenderedBackground create(Context context, SmartTopBackground.Kind kind) {
		float density = context.getResources().getDisplayMetrics().density;
		float radius = 14F * density;
		SmartTopCardBackgroundDrawable content =
				new SmartTopCardBackgroundDrawable(density, kind);

		GradientDrawable mask = new GradientDrawable();
		mask.setShape(GradientDrawable.RECTANGLE);
		mask.setCornerRadius(radius);
		mask.setColor(Color.WHITE);

		TypedArray attrs = context.obtainStyledAttributes(
				new int[]{com.google.android.material.R.attr.rippleColor});
		ColorStateList rippleColor = attrs.getColorStateList(0);
		attrs.recycle();
		if (rippleColor == null) rippleColor = ColorStateList.valueOf(0x33FFFFFF);
		return new RenderedBackground(content, new RippleDrawable(rippleColor, content, mask));
	}

	record RenderedBackground(
			SmartTopCardBackgroundDrawable content,
			RippleDrawable ripple) {
	}
}
