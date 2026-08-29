package me.aap.fermata.ui.view;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.PlaybackSnapshot;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.DashboardFragment;
import me.aap.fermata.ui.policy.ItemRoutePolicy;
import me.aap.fermata.ui.policy.TopBarPolicy;
import me.aap.fermata.ui.policy.TopBarPolicy.State;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Single Fermata authority for common top-bar state.
 *
 * <p>Fragment/tool mediators may contribute actions and geometry, but Back visibility and the
 * canonical title are resolved and rendered here for every runtime host.</p>
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
		if (!applyVisibility(toolBar, fragment)) return;

		State state = resolveState(activity, fragment);
		View back = toolBar.findViewById(me.aap.utils.R.id.tool_bar_back_button);
		if ((back != null) && (back.getVisibility() != state.backVisibility()))
			back.setVisibility(state.backVisibility());
		TextView title = toolBar.findViewById(me.aap.utils.R.id.tool_bar_title);
		if ((title != null) && !TextUtils.equals(title.getText(), state.title()))
			title.setText(state.title());
	}

	/** Applies the fragment-wide chrome rule after a mediator restores its own visibility. */
	public static boolean applyVisibility(ToolBarView toolBar, ActivityFragment fragment) {
		int visibility = TopBarPolicy.resolveTopBarVisibility(fragment.getFragmentId());
		if (toolBar.getVisibility() != visibility) toolBar.setVisibility(visibility);
		return visibility == View.VISIBLE;
	}

	@NonNull
	public static State resolveState(MainActivityDelegate activity, ActivityFragment fragment) {
		PlaybackSnapshot snapshot = activity.getMediaSessionCallback().getPlaybackSnapshot();
		PlayableItem item = snapshot.getItem();
		int playbackOwnerFragmentId = (item == null) ? 0 :
				ItemRoutePolicy.getPlaybackOwnerFragmentId(item);
		if ((fragment instanceof TopBarPlaybackContext context) &&
				!context.usePlaybackTitle(snapshot)) playbackOwnerFragmentId = 0;
		return TopBarPolicy.resolve(activity.getRuntimeHostMode(),
				fragment instanceof DashboardFragment, fragment.getFragmentId(),
				playbackOwnerFragmentId, fragment.getTitle(), snapshot.getDisplayTitle(),
				snapshot.getPreparationStatus());
	}

	@NonNull
	public static CharSequence resolveTitle(MainActivityDelegate activity,
			ActivityFragment fragment) {
		return resolveState(activity, fragment).title();
	}

	public static int resolveBackVisibility(MainActivityDelegate activity,
			ActivityFragment fragment) {
		return resolveState(activity, fragment).backVisibility();
	}
}
