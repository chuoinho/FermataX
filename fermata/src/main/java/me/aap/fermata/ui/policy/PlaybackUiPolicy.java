package me.aap.fermata.ui.policy;

import androidx.annotation.Nullable;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;

public final class PlaybackUiPolicy {
	private PlaybackUiPolicy() {
	}

	public static boolean shouldShowAudioPlayerBar(MainActivityDelegate activity) {
		MediaEngine engine = activity.getMediaServiceBinder().getCurrentEngine();
		PlayableItem source = getAudioSource(engine);
		if ((source == null) || engine.isVideoModeRequired()) return false;

		ActivityFragment fragment = activity.getActiveFragment();
		return shouldShowAudioPlayerBar(true, false,
				fragment != null,
				(fragment == null) ? 0 : fragment.getFragmentId(),
				ItemRoutePolicy.getFragmentId(source));
	}

	static boolean shouldShowAudioPlayerBar(boolean hasAudioSource, boolean videoModeRequired,
										boolean hasActiveFragment, int activeFragmentId,
										int sourceFragmentId) {
		return hasAudioSource && !videoModeRequired && hasActiveFragment &&
				(activeFragmentId == sourceFragmentId);
	}

	public static boolean goToCurrentAudioSource(MainActivityDelegate activity) {
		PlayableItem source = getAudioSource(activity.getMediaServiceBinder().getCurrentEngine());
		if ((source != null) && activity.goToItem(source)) return true;

		PlayableItem current = activity.getMediaServiceBinder().getCurrentItem();
		return (current != null) && !current.isVideo() && activity.goToItem(current);
	}

	@Nullable
	private static PlayableItem getAudioSource(@Nullable MediaEngine engine) {
		if (engine == null) return null;
		PlayableItem source = engine.getSource();
		return ((source != null) && !source.isVideo()) ? source : null;
	}
}
