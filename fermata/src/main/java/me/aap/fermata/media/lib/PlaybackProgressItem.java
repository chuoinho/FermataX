package me.aap.fermata.media.lib;

import me.aap.utils.async.FutureSupplier;

/**
 * Optional contract for addons that own durable playback progress outside the common preferences.
 */
public interface PlaybackProgressItem {
	/** Returns a non-negative position, or {@code -1} to use the common preference fallback. */
	long getResumePosition();

	/** Persists a normalized position. Completed items receive position zero. */
	FutureSupplier<Void> savePlaybackProgress(long position, boolean completed);
}
