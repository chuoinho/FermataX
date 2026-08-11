package me.aap.fermata.ui.policy;

import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.ui.fragment.ActivityFragment;

public final class PlaybackUiPolicy {
	private PlaybackUiPolicy() {
	}

	public static boolean shouldShowAudioPlayerBar(MainActivityDelegate activity) {
		MediaEngine engine = activity.getMediaServiceBinder().getCurrentEngine();
		PlayableItem source = getAudioSource(engine);
		if ((source == null) || engine.isVideoModeRequired()) return false;

		ActivityFragment fragment = activity.getActiveFragment();
		return shouldShowAudioPlayerBar(true, false, fragment != null,
				(fragment == null) ? 0 : fragment.getFragmentId(), R.id.dashboard_fragment);
	}

	static boolean shouldShowAudioPlayerBar(boolean hasAudioSource, boolean videoModeRequired,
										boolean hasActiveFragment, int activeFragmentId,
										int dashboardFragmentId) {
		return hasAudioSource && !videoModeRequired && hasActiveFragment &&
				(activeFragmentId != dashboardFragmentId);
	}

	public static boolean goToCurrentAudioSource(MainActivityDelegate activity) {
		PlayableItem source = getAudioSource(activity.getMediaServiceBinder().getCurrentEngine());
		if ((source != null) && activity.goToItem(source)) return true;

		PlayableItem current = activity.getMediaServiceBinder().getCurrentItem();
		return (current != null) && !current.isVideo() && activity.goToItem(current);
	}

	public static boolean isActiveListOnCurrentAudioPath(MainActivityDelegate activity) {
		PlayableItem source = getAudioSource(activity.getMediaServiceBinder().getCurrentEngine());
		ActivityFragment fragment = activity.getActiveFragment();
		if ((source == null) || !(fragment instanceof MediaLibFragment media)) return false;
		MediaLibFragment.ListAdapter adapter = media.getAdapter();
		BrowsableItem current = (adapter == null) ? null : adapter.getParent();
		return isSameOrAncestor(current, source.getParent());
	}

	static boolean isSameOrAncestor(@Nullable BrowsableItem current,
			@Nullable BrowsableItem descendant) {
		if ((current == null) || (descendant == null)) return false;
		BrowsableItem item = descendant;
		for (int depth = 0; depth < 64; depth++) {
			if (current.equals(item)) return true;
			BrowsableItem parent = item.getParent();
			if ((parent == null) || (parent == item)) return false;
			item = parent;
		}
		return false;
	}

	@Nullable
	private static PlayableItem getAudioSource(@Nullable MediaEngine engine) {
		if (engine == null) return null;
		PlayableItem source = engine.getSource();
		return ((source != null) && !source.isVideo()) ? source : null;
	}
}
