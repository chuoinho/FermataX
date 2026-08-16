package me.aap.fermata.ui.view;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * Leading player-bar region shared by PHONE and automotive layouts.
 *
 * <p>On automotive it remains the Back/edge-touch target. PHONE does not expose a second
 * hide-playerbar action here: the icon is removed and the region/time label are passive.</p>
 */
public class ControlPanelLeadingActionView extends LinearLayout {
	public ControlPanelLeadingActionView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		applyHostPolicy();
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		post(this::applyHostPolicy);
	}

	private void applyHostPolicy() {
		if (usesAutomotivePresentation()) return;

		View icon = findViewById(R.id.show_hide_bars_icon);
		if (icon != null) icon.setVisibility(GONE);
		setPressed(false);
		setClickable(false);
		setFocusable(false);
		setOnClickListener(null);
		setOnTouchListener(null);

		View time = findViewById(R.id.seek_time);
		if (time != null) {
			time.setPressed(false);
			time.setClickable(false);
			time.setFocusable(false);
			time.setOnClickListener(null);
			time.setOnTouchListener(null);
		}
	}

	private boolean usesAutomotivePresentation() {
		try {
			return MainActivityDelegate.get(getContext()).getRuntimeHostMode()
					.usesAutomotivePresentation();
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
