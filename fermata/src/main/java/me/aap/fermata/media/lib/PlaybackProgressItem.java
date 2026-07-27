package me.aap.fermata.media.lib;

import me.aap.utils.async.FutureSupplier;

/**
 * Optional contract for addons that own durable playback progress outside the common preferences.
 */
public interface PlaybackProgressItem {
	enum ProgressMode {
		LEGACY,
		MANAGED
	}

	/** Returns a non-negative position, or {@code -1} to use the common preference fallback. */
	long getResumePosition();

	/** Persists a normalized position. Completed items receive position zero. */
	FutureSupplier<Void> savePlaybackProgress(long position, boolean completed);

	/**
	 * Persists progress owned by a concrete media-session generation. Legacy implementations keep
	 * their existing behavior; generation-aware addons override this method.
	 */
	default FutureSupplier<Void> savePlaybackProgress(long position, boolean completed,
			long playbackGeneration) {
		return savePlaybackProgress(position, completed);
	}

	/**
	 * Selects the base-owned progress coordinator. Existing implementations remain legacy until
	 * they explicitly opt in and pass their addon characterization tests.
	 */
	default ProgressMode getPlaybackProgressMode() {
		return ProgressMode.LEGACY;
	}
}
