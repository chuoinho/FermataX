package me.aap.fermata.media.audio;

/** Describes how legacy audio-effect data relates to the unified profile. */
public enum MigrationState {
	NONE,
	PENDING_NATIVE_TOPOLOGY,
	MIGRATED,
	DORMANT
}
