package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.policy.ItemRoutePolicy;
import me.aap.fermata.ui.policy.PlaybackPresentationOwner.Identity;
import me.aap.fermata.ui.policy.ToolBarTitlePolicy;
import me.aap.utils.ui.fragment.ActivityFragment;

final class ControlPanelPresentationView {
	private final ControlPanelView panel;
	private final Drawable audioBackground;
	private final ConstraintSet audioHostConstraints = new ConstraintSet();
	private final ConstraintSet videoHostConstraints = new ConstraintSet();
	private boolean videoMode;

	ControlPanelPresentationView(ControlPanelView panel) {
		this.panel = panel;
		audioBackground = panel.getBackground();
	}

	void setVideoMode(boolean videoMode) {
		this.videoMode = videoMode;
		if (videoMode) panel.setBackgroundResource(R.drawable.control_panel_video_panel_bg);
		else panel.setBackground(audioBackground);
		if (!(panel.getParent() instanceof ConstraintLayout host)) {
			panel.post(() -> {
				if (panel.getParent() instanceof ConstraintLayout attachedHost) {
					applyVideoMode(attachedHost);
				}
			});
			return;
		}
		applyVideoMode(host);
	}

	private void applyVideoMode(ConstraintLayout host) {
		ConstraintSet constraints = videoMode ? videoHostConstraints : audioHostConstraints;
		constraints.clone(host);
		if (videoMode) {
			constraints.connect(R.id.body_layout, ConstraintSet.BOTTOM,
					ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
			constraints.clear(R.id.control_panel, ConstraintSet.TOP);
			constraints.setTranslationZ(R.id.control_panel, toIntPx(panel.getContext(), 8));
		} else {
			constraints.connect(R.id.body_layout, ConstraintSet.BOTTOM,
					R.id.control_panel, ConstraintSet.TOP);
			constraints.connect(R.id.control_panel, ConstraintSet.TOP,
					R.id.body_layout, ConstraintSet.BOTTOM);
			constraints.setTranslationZ(R.id.control_panel, 0F);
		}
		constraints.applyTo(host);
		enforceHostLayout(host);
		onPanelVisibilityChanged(panel.getVisibility());
	}

	void onPanelVisibilityChanged(int visibility) {
		if (!(panel.getParent() instanceof View parent)) return;
		if (videoMode && (parent instanceof ConstraintLayout host)) enforceHostLayout(host);
		View scrim = parent.findViewById(R.id.control_panel_scrim);
		if (scrim != null) scrim.setVisibility(videoMode && (visibility == VISIBLE) ? VISIBLE : GONE);
	}

	private void enforceHostLayout(ConstraintLayout host) {
		ConstraintLayout.LayoutParams body = (ConstraintLayout.LayoutParams)
				host.findViewById(R.id.body_layout).getLayoutParams();
		ConstraintLayout.LayoutParams controls = (ConstraintLayout.LayoutParams) panel.getLayoutParams();
		if (videoMode) {
			body.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
			body.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
			controls.topToBottom = ConstraintLayout.LayoutParams.UNSET;
		} else {
			body.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
			body.bottomToTop = R.id.control_panel;
			controls.topToBottom = R.id.body_layout;
		}
		host.findViewById(R.id.body_layout).setLayoutParams(body);
		panel.setLayoutParams(controls);
	}

	void updateVideoTitle(MainActivityDelegate activity) {
		if (!activity.getRuntimeHostMode().usesAutomotivePresentation()) return;
		TextView title = activity.getToolBar().findViewById(me.aap.utils.R.id.tool_bar_title);
		if (title == null) return;
		ActivityFragment fragment = activity.getActiveFragment();
		if (fragment == null) return;
		PlaybackSnapshot snapshot = activity.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem item = snapshot.getItem();
		int ownerId = (item == null) ? 0 : ItemRoutePolicy.getPlaybackOwnerFragmentId(item);
		title.setText(ToolBarTitlePolicy.resolve(fragment.getFragmentId(), ownerId,
				fragment.getTitle(), snapshot.getDisplayTitle(), snapshot.getPreparationStatus()));
	}

	Identity currentIdentity(MainActivityDelegate activity) {
		MediaEngine engine = activity.getMediaServiceBinder().getCurrentEngine();
		PlaybackSnapshot snapshot = activity.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem item = snapshot.getItem();
		if ((item == null) && (engine != null)) item = engine.getSource();
		int addonId = (item == null) ? 0 : ItemRoutePolicy.getPlaybackOwnerFragmentId(item);
		int engineId = (engine == null) ? 0 : engine.getId();
		String itemId = (item == null) ? "" : item.getOrigId();
		return new Identity(addonId, engineId, itemId);
	}
}
