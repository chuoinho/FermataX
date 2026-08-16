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

	/**
	 * Resolves the body mode before a local playback request is handed to the engine. Entering VIDEO
	 * while still in FRAME prevents the first decoded frame from being rendered in the old list
	 * viewport. Existing split/fullscreen intent is preserved. Custom engine providers own their
	 * presentation lifecycle (for example Web/YouTube) and are never preflighted here.
	 */
	public static BodyLayout.Mode getModeOnPlayRequest(BodyLayout.Mode currentMode,
			MediaLib.PlayableItem item, boolean customEngineProvider) {
		return getModeOnPlayRequest(currentMode, item != null && item.isVideo(), customEngineProvider);
	}

	static BodyLayout.Mode getModeOnPlayRequest(BodyLayout.Mode currentMode, boolean videoItem,
			boolean customEngineProvider) {
		if (!videoItem || customEngineProvider) return currentMode;
		return currentMode == BodyLayout.Mode.FRAME ? BodyLayout.Mode.VIDEO : currentMode;
	}

	/**
	 * A viewport preflight is speculative until the service accepts the play request. Rejected
	 * requests restore the original layout, except when the already-current engine still requires
	 * fullscreen video; that existing playback remains authoritative.
	 */
	public static BodyLayout.Mode getModeAfterRejectedPlayRequest(BodyLayout.Mode originalMode,
			BodyLayout.Mode requestedMode, boolean currentVideoModeRequired) {
		if (currentVideoModeRequired) return BodyLayout.Mode.VIDEO;
		return (requestedMode != originalMode) ? originalMode : requestedMode;
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

	/**
	 * Navigating to another route always leaves fullscreen in FRAME on every host. PHONE/AA may
	 * render FRAME differently, but route semantics must not fork by host type.
	 */
	public static BodyLayout.Mode getModeAfterLeavingVideo(boolean ignoredCarActivity) {
		return BodyLayout.Mode.FRAME;
	}

	public static boolean shouldKeepExternalVideoMode(RuntimeHostMode hostMode,
												 boolean presentationActive, boolean hasEngine, boolean videoModeRequired,
												 boolean splitModeSupported, boolean mainFragment,
												 int fragmentId) {
		if ((hostMode == null) || !presentationActive || !hasEngine || !videoModeRequired ||
				splitModeSupported || !mainFragment) return false;
		return (fragmentId == R.id.youtube_fragment) || (fragmentId == R.id.web_browser_fragment);
	}

	private static boolean isSameRoot(MediaLibFragment f, MediaLib.PlayableItem i) {
		var adapter = f.getAdapter();
		if (adapter == null) return false;
		var parent = adapter.getParent();
		return (parent != null) && parent.getRoot().equals(i.getRoot());
	}
}
