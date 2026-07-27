package me.aap.fermata.addon.stremio.playback;

/** Finite lifecycle of one selected Stremio playback choice. */
public enum PlaybackAttemptState {
	CREATED,
	RESOLVING,
	PREPARING,
	DATA_READY,
	PLAYER_READY,
	FIRST_FRAME,
	PLAYING,
	ENDED,
	FAILED,
	CANCELLED;

	public boolean isTerminal() {
		return (this == ENDED) || (this == FAILED) || (this == CANCELLED);
	}
}
