package me.aap.fermata.ui.policy;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.VideoView;

public final class PlaybackLayoutPolicy {
	private PlaybackLayoutPolicy() {
	}

	public static boolean shouldShowSplit(MediaLibFragment f, MediaEngine eng,
																MediaSessionCallback cb, VideoView vv) {
		if ((f == null) || (eng == null)) return false;
		MediaLib.PlayableItem i = eng.getSource();
		return shouldShowSplit(true, true, i != null, (i != null) && i.isVideo(),
				eng.isSplitModeSupported(), cb.getVideoView() == vv,
				(i != null) && isSameRoot(f, i));
	}

	static boolean shouldShowSplit(boolean hasFragment, boolean hasEngine, boolean hasSource,
													 boolean sourceIsVideo, boolean splitSupported,
													 boolean usesBodyVideoView, boolean sameRoot) {
		return hasFragment && hasEngine && hasSource && sourceIsVideo && splitSupported &&
				usesBodyVideoView && sameRoot;
	}

	public static BodyLayout.Mode getModeOnPlayableChanged(BodyLayout.Mode currentMode,
																				 MediaLib.PlayableItem newItem,
																				 MediaEngine eng) {
		if ((newItem == null) || !newItem.isVideo() || (eng == null) || !eng.isSplitModeSupported()) {
			return BodyLayout.Mode.FRAME;
		}
		return getModeOnPlayableChanged(currentMode, true, true, true, true,
				eng.isVideoModeRequired());
	}

	static BodyLayout.Mode getModeOnPlayableChanged(BodyLayout.Mode currentMode, boolean hasItem,
																 boolean itemIsVideo, boolean hasEngine,
																 boolean splitSupported, boolean videoModeRequired) {
		if (!hasItem || !itemIsVideo || !hasEngine || !splitSupported) {
			return BodyLayout.Mode.FRAME;
		}

		if (!videoModeRequired) return BodyLayout.Mode.FRAME;
		return currentMode == BodyLayout.Mode.FRAME ? BodyLayout.Mode.VIDEO : currentMode;
	}

	public static boolean shouldRefreshVideoInCurrentMode(BodyLayout.Mode currentMode,
																					MediaLib.PlayableItem newItem,
																					MediaEngine eng) {
		if ((currentMode == BodyLayout.Mode.FRAME) || (newItem == null) || !newItem.isVideo() ||
				(eng == null) || !eng.isSplitModeSupported()) return false;
		return shouldRefreshVideoInCurrentMode(currentMode, true, true, true, true,
				eng.isVideoModeRequired());
	}

	static boolean shouldRefreshVideoInCurrentMode(BodyLayout.Mode currentMode, boolean hasItem,
																	 boolean itemIsVideo, boolean hasEngine,
																	 boolean splitSupported, boolean videoModeRequired) {
		return (currentMode != BodyLayout.Mode.FRAME) && hasItem && itemIsVideo && hasEngine &&
				splitSupported && videoModeRequired;
	}

	public static BodyLayout.Mode getModeAfterLeavingVideo(boolean carActivity) {
		return carActivity ? BodyLayout.Mode.FRAME : BodyLayout.Mode.BOTH;
	}

	public static boolean shouldKeepExternalVideoMode(boolean auto, boolean carActivity,
																			 boolean hasEngine, boolean videoModeRequired,
																			 boolean splitModeSupported, boolean mainFragment,
																			 int fragmentId) {
		if (!auto && !carActivity) return false;
		if (!hasEngine || !videoModeRequired || splitModeSupported || !mainFragment) return false;
		return (fragmentId == R.id.youtube_fragment) || (fragmentId == R.id.web_browser_fragment);
	}

	private static boolean isSameRoot(MediaLibFragment f, MediaLib.PlayableItem i) {
		var adapter = f.getAdapter();
		if (adapter == null) return false;
		var parent = adapter.getParent();
		return (parent != null) && parent.getRoot().equals(i.getRoot());
	}
}
