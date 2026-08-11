package me.aap.fermata.ui.view;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

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
	private final ControlPanelContentInsetCoordinator contentInsets =
			new ControlPanelContentInsetCoordinator();
	private boolean videoMode;

	ControlPanelPresentationView(ControlPanelView panel) {
		this.panel = panel;
		audioBackground = panel.getBackground();
	}

	void setVideoMode(boolean videoMode) {
		this.videoMode = videoMode;
		if (videoMode) panel.setBackgroundResource(R.drawable.control_panel_video_panel_bg);
		else panel.setBackground(audioBackground);
		onPanelVisibilityChanged(panel.getVisibility());
	}

	void onPanelVisibilityChanged(int visibility) {
		if (!(panel.getParent() instanceof View parent)) return;
		if (parent instanceof android.view.ViewGroup host)
			contentInsets.setPanelVisible(host, visibility == VISIBLE);
		View scrim = parent.findViewById(R.id.control_panel_scrim);
		if (scrim != null) scrim.setVisibility(videoMode && (visibility == VISIBLE) ? VISIBLE : GONE);
	}

	void release() {
		contentInsets.release();
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
