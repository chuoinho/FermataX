package me.aap.fermata.media.audio;

import androidx.annotation.Nullable;

/** Reads one legacy native system preset without exposing Android audio-effect APIs to storage. */
interface LegacyEqualizerPresetResolver {
	/** @return the legacy band levels in millibels, or {@code null} when they cannot be read safely. */
	@Nullable int[] resolveSystemPreset(int legacyPreset);
}
