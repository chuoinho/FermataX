package me.aap.fermata.media.net;

import java.util.function.Consumer;

import me.aap.utils.async.FutureSupplier;

/**
 * Opt-in contract for items whose short-lived remote location must be prepared before playback.
 * Existing playable items do not implement this interface and keep their current engine path.
 */
public interface RemotePlaybackItem {
	PlaybackRequestProfile getPlaybackRequestProfile();

	/** P2P resumes after decoder/surface readiness so the first range request stays bounded. */
	default boolean deferInitialSeekUntilFirstFrame() {
		PlaybackRequestProfile profile = getPlaybackRequestProfile();
		return (profile != null) && profile.getRequiredEngineCapabilities().contains(
				PlaybackRequestProfile.EngineCapability.P2P_STREAMING);
	}

	FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback();

	/** Optional progress channel. Existing remote items keep the original preparation path. */
	default FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback(
			Consumer<RemotePlaybackProgress> progress) {
		return prepareRemotePlayback();
	}
}
