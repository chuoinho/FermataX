package me.aap.fermata.ui.view;

import me.aap.fermata.media.audio.AudioEffectsProfile;

/** Pure geometry shared by the read-only equalizer curve and its unit tests. */
final class EqualizerCurveGeometry {
	private static final double MIN_LOG_FREQUENCY = Math.log(AudioEffectsProfile.CANONICAL_FREQ_HZ[0]);
	private static final double LOG_FREQUENCY_SPAN = Math.log(
			AudioEffectsProfile.CANONICAL_FREQ_HZ[AudioEffectsProfile.CANONICAL_FREQ_HZ.length - 1]) -
			MIN_LOG_FREQUENCY;

	private EqualizerCurveGeometry() {
	}

	static float frequencyPosition(int frequencyHz) {
		return (float) ((Math.log(frequencyHz) - MIN_LOG_FREQUENCY) / LOG_FREQUENCY_SPAN);
	}

	static float gainPosition(int gainDb) {
		int clamped = Math.max(AudioEffectsProfile.MIN_CANONICAL_DB,
				Math.min(AudioEffectsProfile.MAX_CANONICAL_DB, gainDb));
		return (float) (AudioEffectsProfile.MAX_CANONICAL_DB - clamped) /
				(AudioEffectsProfile.MAX_CANONICAL_DB - AudioEffectsProfile.MIN_CANONICAL_DB);
	}
}
