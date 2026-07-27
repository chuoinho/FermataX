package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

/**
 * Opt-in contract for video items whose subtitle choice is owned by their content screen.
 * Other playable items keep the existing player-menu behavior.
 */
public interface ContentSubtitleSelectionItem {
	String getSubtitleSelectionKey();

	@Nullable
	Long getPreferredSubtitleTrackId();

	String getPreferredSubtitleLanguagePattern();

	boolean areSubtitlesDisabled();
}
