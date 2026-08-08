package me.aap.fermata.media.lib;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/** A collection-facing item whose canonical counterpart owns playback. */
public interface PlaybackPresentationItem {
	@NonNull
	PlayableItem getCanonicalPlaybackItem();
}
