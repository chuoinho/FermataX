package me.aap.fermata.media.audio;

import java.util.EnumSet;

/** A session-bound native effect chain. It owns no preferences or playback engine. */
interface AudioEffectsBackend {
	EnumSet<AudioEffectCapability> getCapabilities();

	void apply(AudioEffectsProfile profile);

	void bypass();

	void release();
}
