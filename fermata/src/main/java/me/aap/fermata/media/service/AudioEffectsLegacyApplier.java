package me.aap.fermata.media.service;

import static me.aap.fermata.media.pref.MediaPrefs.AE_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.BASS_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.BASS_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_BANDS;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_PRESET;
import static me.aap.fermata.media.pref.MediaPrefs.EQ_USER_PRESETS;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_MODE;
import static me.aap.fermata.media.pref.MediaPrefs.VIRT_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.VOL_BOOST_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.VOL_BOOST_STRENGTH;
import static me.aap.fermata.media.pref.MediaPrefs.getUserPresetBands;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/** Applies the legacy track, folder, and playback audio-effect preferences in precedence order. */
final class AudioEffectsLegacyApplier {
	private AudioEffectsLegacyApplier() {
	}

	static void apply(MediaEngine engine, PlaybackControlPrefs controlPrefs,
			PreferenceStore... stores) {
		AudioEffects effects = engine.getAudioEffects();
		if (effects == null) return;

		Equalizer equalizer = effects.getEqualizer();
		Virtualizer virtualizer = effects.getVirtualizer();
		BassBoost bassBoost = effects.getBassBoost();
		LoudnessEnhancer loudnessEnhancer = effects.getLoudnessEnhancer();

		for (PreferenceStore store : stores) {
			if (!store.getBooleanPref(AE_ENABLED)) continue;
			applyEqualizer(equalizer, controlPrefs, store);
			applyVirtualizer(virtualizer, store);
			applyBassBoost(bassBoost, store);
			applyLoudnessEnhancer(loudnessEnhancer, store);
			return;
		}

		setEnabled(equalizer, false);
		setEnabled(virtualizer, false);
		setEnabled(bassBoost, false);
		setEnabled(loudnessEnhancer, false);
	}

	private static void applyEqualizer(Equalizer equalizer, PlaybackControlPrefs controlPrefs,
			PreferenceStore store) {
		if (equalizer == null) return;
		if (!store.getBooleanPref(EQ_ENABLED)) {
			equalizer.setEnabled(false);
			return;
		}

		try {
			short presetCount = equalizer.getNumberOfPresets();
			int preset = store.getIntPref(EQ_PRESET);

			if ((preset > 0) && (preset <= presetCount)) {
				equalizer.setEnabled(true);
				equalizer.usePreset((short) (preset - 1));
				return;
			}

			int[] bands = null;
			if (preset < 0) {
				String[] userPresets = controlPrefs.getStringArrayPref(EQ_USER_PRESETS);
				int userPreset = -preset - 1;
				if ((userPreset >= 0) && (userPreset < userPresets.length)) {
					bands = getUserPresetBands(userPresets[userPreset]);
				}
			} else {
				bands = store.getIntArrayPref(EQ_BANDS);
			}

			if (bands == null) {
				equalizer.setEnabled(false);
				return;
			}

			equalizer.setEnabled(true);
			applyBands(equalizer, bands);
		} catch (Exception ex) {
			Log.e(ex, "Failed to configure Equalizer");
		}
	}

	private static void applyVirtualizer(Virtualizer virtualizer, PreferenceStore store) {
		if (virtualizer == null) return;
		if (!store.getBooleanPref(VIRT_ENABLED)) {
			virtualizer.setEnabled(false);
			return;
		}

		try {
			virtualizer.setEnabled(true);
			virtualizer.setStrength((short) store.getIntPref(VIRT_STRENGTH));
			virtualizer.forceVirtualizationMode(store.getIntPref(VIRT_MODE));
		} catch (Exception ex) {
			Log.e(ex, "Failed to configure Virtualizer");
		}
	}

	private static void applyBassBoost(BassBoost bassBoost, PreferenceStore store) {
		if (bassBoost == null) return;
		if (!bassBoost.getStrengthSupported() || !store.getBooleanPref(BASS_ENABLED)) {
			bassBoost.setEnabled(false);
			return;
		}

		try {
			bassBoost.setEnabled(true);
			bassBoost.setStrength((short) store.getIntPref(BASS_STRENGTH));
		} catch (Exception ex) {
			Log.e(ex, "Failed to configure BassBoost");
		}
	}

	private static void applyLoudnessEnhancer(LoudnessEnhancer loudnessEnhancer,
			PreferenceStore store) {
		if (loudnessEnhancer == null) return;
		if (!store.getBooleanPref(VOL_BOOST_ENABLED)) {
			loudnessEnhancer.setEnabled(false);
			return;
		}

		try {
			loudnessEnhancer.setEnabled(true);
			loudnessEnhancer.setTargetGain(store.getIntPref(VOL_BOOST_STRENGTH) * 10);
		} catch (Exception ex) {
			Log.e(ex, "Failed to configure LoudnessEnhancer");
		}
	}

	private static void applyBands(Equalizer equalizer, int[] bands) {
		short[] levels = clampBandLevels(bands, equalizer.getNumberOfBands(),
				equalizer.getBandLevelRange());
		for (short band = 0; band < levels.length; band++) {
			equalizer.setBandLevel(band, levels[band]);
		}
	}

	static short[] clampBandLevels(int[] bands, short bandCount, short[] levelRange) {
		if ((bands == null) || (bandCount <= 0) || (levelRange == null) ||
				(levelRange.length < 2)) return new short[0];

		int min = Math.min(levelRange[0], levelRange[1]);
		int max = Math.max(levelRange[0], levelRange[1]);
		int count = Math.min(bands.length, bandCount);
		short[] levels = new short[count];

		for (int band = 0; band < count; band++) {
			levels[band] = (short) Math.max(min, Math.min(max, bands[band]));
		}
		return levels;
	}

	private static void setEnabled(@androidx.annotation.Nullable android.media.audiofx.AudioEffect effect,
			boolean enabled) {
		if (effect != null) effect.setEnabled(enabled);
	}
}
