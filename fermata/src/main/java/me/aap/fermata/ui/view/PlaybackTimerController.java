package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;

import com.google.android.material.textview.MaterialTextView;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.Consumer;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;

final class PlaybackTimerController {
	private final ControlPanelView controlPanel;
	private final int textAppearance;
	private final Consumer<MainActivityDelegate> openMenu;
	private TextView timerView;

	PlaybackTimerController(ControlPanelView controlPanel, @StyleRes int textAppearance,
							Consumer<MainActivityDelegate> openMenu) {
		this.controlPanel = controlPanel;
		this.textAppearance = textAppearance;
		this.openMenu = openMenu;
	}

	void refresh(MainActivityDelegate activity) {
		int time = activity.getMediaSessionCallback().getPlaybackTimer();
		if (time <= 0) {
			removeTimerView();
			return;
		}

		TextView timer = getOrCreateTimerView(activity);
		if (controlPanel.getVisibility() != VISIBLE) {
			timer.setVisibility(GONE);
			return;
		}

		try (SharedTextBuilder text = SharedTextBuilder.get()) {
			TextUtils.timeToString(text, time);
			timer.setText(text);
		}

		timer.setVisibility(VISIBLE);
		activity.postDelayed(() -> refresh(activity), 1000);
	}

	private TextView getOrCreateTimerView(MainActivityDelegate activity) {
		if (timerView != null) return timerView;

		Context context = controlPanel.getContext();
		timerView = new MaterialTextView(context);
		((ViewGroup) controlPanel.getParent()).addView(timerView);
		timerView.setBackgroundResource(R.drawable.playback_timer_bg);
		timerView.setTextAppearance(textAppearance);
		ViewGroup.LayoutParams params = timerView.getLayoutParams();

		if (params instanceof LayoutParams constraintParams) {
			constraintParams.startToStart = PARENT_ID;
			constraintParams.endToEnd = PARENT_ID;
			constraintParams.bottomToTop = controlPanel.getId();
			constraintParams.resolveLayoutDirection(ControlPanelView.LAYOUT_DIRECTION_LTR);
		}

		timerView.setOnClickListener(view -> openMenu.accept(activity));
		return timerView;
	}

	private void removeTimerView() {
		if (timerView == null) return;
		((ViewGroup) controlPanel.getParent()).removeView(timerView);
		timerView = null;
	}
}
