package me.aap.fermata.media.audio;

/** Defines whether a session-bound Equalizer can accept profile changes while active. */
enum EqualizerUpdateMode {
	STANDARD_LIVE,
	INITIAL_ONLY,
	UNAVAILABLE
}
