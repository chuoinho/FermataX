package me.aap.fermata.ui.view;

import me.aap.fermata.media.service.PlaybackSnapshot;

/**
 * Optional fragment capability for page-backed routes whose visible page decides whether playback
 * metadata may replace the route title. The common top-bar controller remains the renderer.
 */
public interface TopBarPlaybackContext {
	boolean usePlaybackTitle(PlaybackSnapshot snapshot);
}
