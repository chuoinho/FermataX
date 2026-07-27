package me.aap.fermata.media.net;

import java.util.function.Consumer;

/**
 * Optional lifecycle bridge for remote items that own cancellable playback attempts.
 * Media engines and the session remain unaware of the addon implementing this contract.
 */
public interface RemotePlaybackLifecycleItem {
	void onPlaybackAttemptActivated(long requestRevision, Consumer<Throwable> failureHandler);

	default void onPlaybackAttemptPlayerReady(long requestRevision) {}

	default void onPlaybackAttemptFirstFrame(long requestRevision) {}

	default void onPlaybackAttemptStarted(long requestRevision) {}

	default void onPlaybackAttemptPaused(long requestRevision) {}

	default void onPlaybackAttemptEnded(long requestRevision) {}

	default boolean onPlaybackAttemptFallback(long requestRevision) {
		return true;
	}

	default void onPlaybackAttemptFailed(long requestRevision, Throwable error) {}

	default void onPlaybackAttemptCancelled(long requestRevision) {}
}
