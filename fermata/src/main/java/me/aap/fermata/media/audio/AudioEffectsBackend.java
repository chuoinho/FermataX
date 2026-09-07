package me.aap.fermata.media.audio;

import java.util.EnumSet;

/** A session-bound native effect chain. It owns no preferences or playback engine. */
interface AudioEffectsBackend {
	EnumSet<AudioEffectCapability> getCapabilities();

	default EqualizerUpdateMode getEqualizerUpdateMode() {
		return EqualizerUpdateMode.STANDARD_LIVE;
	}

	/** Returns whether the current Equalizer profile was accepted by this session. */
	boolean apply(AudioEffectsProfile profile, boolean applyEqualizer);

	void bypass();

	void release();
}
