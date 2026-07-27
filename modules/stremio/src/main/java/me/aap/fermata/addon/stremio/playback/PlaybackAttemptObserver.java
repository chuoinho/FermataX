package me.aap.fermata.addon.stremio.playback;

/** Optional diagnostics/UI observer. Implementations must not retain transport secrets. */
public interface PlaybackAttemptObserver {
	PlaybackAttemptObserver NONE = new PlaybackAttemptObserver() {};

	default void onStateChanged(PlaybackAttempt attempt, PlaybackAttemptState previous) {}

	default void onStaleCallback(long operationId, PlaybackAttemptState event) {}
}
