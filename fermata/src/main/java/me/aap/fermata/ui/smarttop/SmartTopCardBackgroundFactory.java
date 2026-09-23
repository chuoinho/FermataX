package me.aap.fermata.ui.smarttop;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import me.aap.fermata.R;

/** Creates the rounded content, border and ripple used by the SmartTop root background. */
final class SmartTopCardBackgroundFactory {
	private SmartTopCardBackgroundFactory() {
	}

	static RenderedBackground create(Context context, SmartTopBackground.Kind kind) {
		float density = context.getResources().getDisplayMetrics().density;
		float defaultRadius = 14F * density;
		int defaultBorderColor = 0x857AA7FF;

		TypedArray attrs = context.obtainStyledAttributes(new int[]{
				me.aap.utils.R.attr.cornerRadius,
				com.google.android.material.R.attr.boxStrokeColor,
				com.google.android.material.R.attr.rippleColor
		});
		float radius = attrs.getDimension(0, defaultRadius);
		int borderColor = attrs.getColor(1, defaultBorderColor);
		ColorStateList rippleColor = attrs.getColorStateList(2);
		attrs.recycle();

		SmartTopCardBackgroundDrawable content =
				new SmartTopCardBackgroundDrawable(density, kind, radius, borderColor);

		GradientDrawable mask = new GradientDrawable();
		mask.setShape(GradientDrawable.RECTANGLE);
		mask.setCornerRadius(radius);
		mask.setColor(Color.WHITE);

		if (rippleColor == null) rippleColor = ColorStateList.valueOf(0x33FFFFFF);
		return new RenderedBackground(content, new RippleDrawable(rippleColor, content, mask));
	}

	record RenderedBackground(
			SmartTopCardBackgroundDrawable content,
			RippleDrawable ripple) {
	}
}
