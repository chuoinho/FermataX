package me.aap.fermata.ui.view;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import me.aap.fermata.R;
import me.aap.fermata.ui.policy.ControlPanelGeometryPolicy;

/**
 * @author Andrey Pavlenko
 */
public class ControlPanelSeekView extends AppCompatSeekBar {
	private static final int TRANSPORT_COUNT = 5;
	private static final float DEFAULT_TRANSPORT_START = 0.19F;
	private static final float DEFAULT_TRANSPORT_END = 0.81F;
	private static final int[] TRANSPORT_IDS = {
			R.id.control_prev, R.id.control_rw, R.id.control_play_pause,
			R.id.control_ff, R.id.control_next};
	private ConstraintSet constraints;
	private ConstraintSet constraintsNoSeek;
	private int defaultTransportPadding = -1;
	private int defaultTransportTopMargin = -1;

	public ControlPanelSeekView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public ControlPanelSeekView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (isEnabled() == enabled) return;
		applyConstraints(enabled);
		super.setEnabled(enabled);
		ControlPanelView panel = getPanel();
		panel.computeSize();
		post(this::reflowTransportGeometry);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Do not re-apply a full ConstraintSet here: it also carries default visibility and can
		// overwrite live playback state. Drop resource-derived caches for the next seek-state change
		// and only reconcile geometry for the current configuration.
		constraints = null;
		constraintsNoSeek = null;
		post(() -> {
			if (getParent() instanceof ControlPanelView panel) reflowAfterConfigurationChange(panel);
		});
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (w != oldw) post(this::reflowTransportGeometry);
	}

	static void reflowAfterConfigurationChange(ControlPanelView panel) {
		panel.computeSize();
		ControlPanelSeekView seek = panel.findViewById(R.id.seek_bar);
		if (seek != null) seek.reflowTransportGeometry();
	}

	void reflowTransportGeometry() {
		if (!(getParent() instanceof ControlPanelView panel)) return;
		View play = panel.findViewById(R.id.control_play_pause);
		if (!(play.getLayoutParams() instanceof ConstraintLayout.LayoutParams playParams)) return;
		if (defaultTransportPadding < 0) defaultTransportPadding = play.getPaddingLeft();
		if (defaultTransportTopMargin < 0) defaultTransportTopMargin = playParams.topMargin;

		float startPercent = guidePercent(panel, R.id.control_panel_transport_start,
				DEFAULT_TRANSPORT_START);
		float endPercent = guidePercent(panel, R.id.control_panel_transport_end,
				DEFAULT_TRANSPORT_END);
		int defaultSize = getResources().getDimensionPixelSize(R.dimen.control_panel_transport_height);
		int width = getWidth();
		if (width <= 0) width = panel.getWidth();
		int buttonSize = ControlPanelGeometryPolicy.getTransportButtonSize(width,
				startPercent, endPercent, defaultSize, TRANSPORT_COUNT);
		int padding = ControlPanelGeometryPolicy.getTransportPadding(
				buttonSize, defaultTransportPadding);
		int topMargin = ControlPanelGeometryPolicy.getTransportTopMargin(
				buttonSize, defaultSize, defaultTransportTopMargin);

		for (int id : TRANSPORT_IDS) {
			View button = panel.findViewById(id);
			if (!(button.getLayoutParams() instanceof ConstraintLayout.LayoutParams params)) continue;
			params.height = buttonSize;
			params.topMargin = topMargin;
			button.setLayoutParams(params);
			button.setPadding(padding, padding, padding, padding);
		}
	}

	private void applyConstraints(boolean enabled) {
		ControlPanelView panel = getPanel();
		View showHide = panel.findViewById(R.id.show_hide_bars);
		View menu = panel.findViewById(R.id.control_menu_button);
		View prev = panel.findViewById(R.id.control_prev);
		View next = panel.findViewById(R.id.control_next);
		View favorite = panel.findViewById(R.id.control_favorite);

		if (enabled) {
			if (constraints == null) constraints = load(R.layout.control_panel_view);
			constraints.applyTo(panel);
			prev.setNextFocusLeftId(R.id.control_next);
			showHide.setNextFocusLeftId(R.id.control_menu_button);
			next.setNextFocusRightId(R.id.control_prev);
			favorite.setNextFocusRightId(R.id.control_menu_button);
			menu.setNextFocusRightId(R.id.show_hide_bars);
		} else {
			if (constraintsNoSeek == null) constraintsNoSeek = load(R.layout.control_panel_view2);
			constraintsNoSeek.applyTo(panel);
			prev.setNextFocusLeftId(R.id.show_hide_bars);
			showHide.setNextFocusLeftId(R.id.control_menu_button);
			next.setNextFocusRightId(R.id.control_prev);
			favorite.setNextFocusRightId(R.id.control_menu_button);
			menu.setNextFocusRightId(R.id.show_hide_bars);
		}
	}

	private float guidePercent(ControlPanelView panel, int id, float fallback) {
		View guide = panel.findViewById(id);
		if (guide.getLayoutParams() instanceof ConstraintLayout.LayoutParams params) {
			float percent = params.guidePercent;
			if ((percent >= 0F) && (percent <= 1F)) return percent;
		}
		return fallback;
	}

	private ConstraintSet load(@LayoutRes int layout) {
		Context ctx = getContext();
		ConstraintLayout l = new ConstraintLayout(ctx);
		inflate(ctx, layout, l);
		ConstraintSet cs = new ConstraintSet();
		cs.clone(l);
		return cs;
	}

	private ControlPanelView getPanel() {
		return (ControlPanelView) getParent();
	}
}
