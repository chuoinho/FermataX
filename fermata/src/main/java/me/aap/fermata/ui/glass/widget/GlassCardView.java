package me.aap.fermata.ui.glass.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.glass.theme.GlassDimensions;
import me.aap.fermata.ui.glass.theme.GlassDrawables;
import me.aap.fermata.ui.glass.theme.GlassHardwareUtils;

/**
 * Reusable Glass Card container view built purely in Java.
 * Features translucent frosted glass background, 1dp subtle border, and GPU hardware outline clipping.
 */
public class GlassCardView extends FrameLayout {

	private float cornerRadiusDp = GlassDimensions.CARD_RADIUS_DP;

	public GlassCardView(@NonNull Context context) {
		super(context);
		init(context);
	}

	public GlassCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public GlassCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		updateGlassBackground();
	}

	public void setCornerRadiusDp(float radiusDp) {
		this.cornerRadiusDp = radiusDp;
		updateGlassBackground();
	}

	public void updateGlassBackground() {
		Context context = getContext();
		boolean isNight = (context.getResources().getConfiguration().uiMode &
				Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		setBackground(GlassDrawables.createGlassCardDrawable(context, cornerRadiusDp, isNight));
		float density = context.getResources().getDisplayMetrics().density;
		GlassHardwareUtils.clipRound(this, cornerRadiusDp * density);
	}
}
