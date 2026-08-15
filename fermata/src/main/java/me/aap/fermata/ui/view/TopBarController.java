package me.aap.fermata.ui.view;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.policy.ChromePolicy;
import me.aap.fermata.ui.policy.ItemRoutePolicy;
import me.aap.fermata.ui.policy.ToolBarTitlePolicy;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Single Fermata authority for common top-bar state.
 *
 * <p>Fragment/tool mediators may contribute actions and geometry, but Back visibility and the
 * canonical title are resolved here for every runtime host.</p>
 */
public final class TopBarController {
	private TopBarController() {
	}

	public static void refresh(MainActivityDelegate activity) {
		refresh(activity, activity.getActiveFragment());
	}

	public static void refresh(MainActivityDelegate activity,
											 @Nullable ActivityFragment fragment) {
		if (fragment == null) return;
		ToolBarView toolBar = activity.getToolBar();
		if (toolBar == null) return;

		View back = toolBar.findViewById(me.aap.utils.R.id.tool_bar_back_button);
		if (back != null) back.setVisibility(ChromePolicy.getTopBackVisibility(activity, fragment));

		TextView title = toolBar.findViewById(me.aap.utils.R.id.tool_bar_title);
		if (title != null) title.setText(resolveTitle(activity, fragment));
	}

	@NonNull
	public static CharSequence resolveTitle(MainActivityDelegate activity,
															 ActivityFragment fragment) {
		PlaybackSnapshot snapshot = activity.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem item = snapshot.getItem();
		int playbackOwnerFragmentId = (item == null) ? 0 :
				ItemRoutePolicy.getPlaybackOwnerFragmentId(item);
		return ToolBarTitlePolicy.resolve(fragment.getFragmentId(), playbackOwnerFragmentId,
				fragment.getTitle(), snapshot.getDisplayTitle(), snapshot.getPreparationStatus());
	}
}
