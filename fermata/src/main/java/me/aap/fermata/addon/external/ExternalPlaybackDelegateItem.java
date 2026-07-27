package me.aap.fermata.addon.external;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * Keeps the requesting addon's identity while exposing the handler-owned playback descriptor.
 * Engines may inspect the delegate, but must continue reporting the wrapper as their source.
 */
public interface ExternalPlaybackDelegateItem {
	@NonNull
	PlayableItem getExternalPlaybackDelegate();
}
