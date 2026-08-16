package me.aap.fermata.ui.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.policy.ControlPanelSizingPolicy;

/**
 * Weighted transport cells keep their full touch/focus area, while the glyph and the primary
 * background are sized from the cell actually allocated by ConstraintLayout. This avoids tiny
 * fixed-padding glyphs on narrow phones and oversized/non-square primary surfaces on wide screens.
 */
public class AdaptiveTransportButton extends me.aap.utils.ui.view.ImageButton {
	@Nullable
	private Drawable baseBackground;
	private boolean constructed;

	public AdaptiveTransportButton(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, androidx.appcompat.R.attr.imageButtonStyle);
	}

	public AdaptiveTransportButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		constructed = true;
		baseBackground = getBackground();
	}

	@Override
	public void setBackgroundResource(int resId) {
		super.setBackgroundResource(resId);
		if (!constructed) return;
		baseBackground = getBackground();
		applyAdaptiveGeometry(getWidth(), getHeight());
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		applyAdaptiveGeometry(w, h);
	}

	private void applyAdaptiveGeometry(int width, int height) {
		if ((width <= 0) || (height <= 0)) return;
		ControlPanelSizingPolicy.Geometry geometry =
				ControlPanelSizingPolicy.resolve(width, height);
		setPadding(geometry.horizontalPadding(), geometry.verticalPadding(),
				geometry.horizontalPadding(), geometry.verticalPadding());

		if (getId() != R.id.control_play_pause) return;
		Drawable background = baseBackground;
		if (background == null) {
			background = getBackground();
			baseBackground = background;
		}
		if (background == null) return;

		int insetX = geometry.backgroundInsetX();
		int insetY = geometry.backgroundInsetY();
		super.setBackground(new InsetDrawable(background, insetX, insetY, insetX, insetY));
	}
}
