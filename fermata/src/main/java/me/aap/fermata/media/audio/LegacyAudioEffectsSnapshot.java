package me.aap.fermata.media.audio;

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

import androidx.annotation.Nullable;

import me.aap.utils.pref.PreferenceStore;

/** Immutable copy of the legacy global state retained for rollback and deferred migration. */
public final class LegacyAudioEffectsSnapshot {
	private final boolean audioEffectsEnabledDefined;
	private final boolean audioEffectsEnabled;
	private final boolean equalizerEnabledDefined;
	private final boolean equalizerEnabled;
	private final boolean equalizerPresetDefined;
	private final int equalizerPreset;
	private final boolean equalizerBandsDefined;
	@Nullable
	private final int[] rawEqualizerBands;
	private final boolean userPresetsDefined;
	private final String[] rawUserPresets;
	private final boolean bassBoostEnabledDefined;
	private final boolean bassBoostEnabled;
	private final boolean bassBoostStrengthDefined;
	private final int bassBoostStrength;
	private final boolean loudnessEnabledDefined;
	private final boolean loudnessEnabled;
	private final boolean loudnessGainDefined;
	private final int loudnessGain;
	private final boolean virtualizerEnabledDefined;
	private final boolean virtualizerEnabled;
	private final boolean virtualizerStrengthDefined;
	private final int virtualizerStrength;
	private final boolean virtualizerModeDefined;
	private final int virtualizerMode;

	LegacyAudioEffectsSnapshot(boolean audioEffectsEnabledDefined, boolean audioEffectsEnabled,
			boolean equalizerEnabledDefined, boolean equalizerEnabled,
			boolean equalizerPresetDefined, int equalizerPreset,
			boolean equalizerBandsDefined, @Nullable int[] rawEqualizerBands,
			boolean userPresetsDefined, String[] rawUserPresets,
			boolean bassBoostEnabledDefined, boolean bassBoostEnabled,
			boolean bassBoostStrengthDefined, int bassBoostStrength,
			boolean loudnessEnabledDefined, boolean loudnessEnabled,
			boolean loudnessGainDefined, int loudnessGain,
			boolean virtualizerEnabledDefined, boolean virtualizerEnabled,
			boolean virtualizerStrengthDefined, int virtualizerStrength,
			boolean virtualizerModeDefined, int virtualizerMode) {
		this.audioEffectsEnabledDefined = audioEffectsEnabledDefined;
		this.audioEffectsEnabled = audioEffectsEnabled;
		this.equalizerEnabledDefined = equalizerEnabledDefined;
		this.equalizerEnabled = equalizerEnabled;
		this.equalizerPresetDefined = equalizerPresetDefined;
		this.equalizerPreset = equalizerPreset;
		this.equalizerBandsDefined = equalizerBandsDefined;
		this.rawEqualizerBands = (rawEqualizerBands == null) ? null : rawEqualizerBands.clone();
		this.userPresetsDefined = userPresetsDefined;
		this.rawUserPresets = rawUserPresets.clone();
		this.bassBoostEnabledDefined = bassBoostEnabledDefined;
		this.bassBoostEnabled = bassBoostEnabled;
		this.bassBoostStrengthDefined = bassBoostStrengthDefined;
		this.bassBoostStrength = bassBoostStrength;
		this.loudnessEnabledDefined = loudnessEnabledDefined;
		this.loudnessEnabled = loudnessEnabled;
		this.loudnessGainDefined = loudnessGainDefined;
		this.loudnessGain = loudnessGain;
		this.virtualizerEnabledDefined = virtualizerEnabledDefined;
		this.virtualizerEnabled = virtualizerEnabled;
		this.virtualizerStrengthDefined = virtualizerStrengthDefined;
		this.virtualizerStrength = virtualizerStrength;
		this.virtualizerModeDefined = virtualizerModeDefined;
		this.virtualizerMode = virtualizerMode;
	}

	static LegacyAudioEffectsSnapshot capture(PreferenceStore store) {
		return new LegacyAudioEffectsSnapshot(
				store.hasPref(AE_ENABLED, false), store.getBooleanPref(AE_ENABLED),
				store.hasPref(EQ_ENABLED, false), store.getBooleanPref(EQ_ENABLED),
				store.hasPref(EQ_PRESET, false), store.getIntPref(EQ_PRESET),
				store.hasPref(EQ_BANDS, false), store.getIntArrayPref(EQ_BANDS),
				store.hasPref(EQ_USER_PRESETS, false), store.getStringArrayPref(EQ_USER_PRESETS),
				store.hasPref(BASS_ENABLED, false), store.getBooleanPref(BASS_ENABLED),
				store.hasPref(BASS_STRENGTH, false), store.getIntPref(BASS_STRENGTH),
				store.hasPref(VOL_BOOST_ENABLED, false), store.getBooleanPref(VOL_BOOST_ENABLED),
				store.hasPref(VOL_BOOST_STRENGTH, false), store.getIntPref(VOL_BOOST_STRENGTH),
				store.hasPref(VIRT_ENABLED, false), store.getBooleanPref(VIRT_ENABLED),
				store.hasPref(VIRT_STRENGTH, false), store.getIntPref(VIRT_STRENGTH),
				store.hasPref(VIRT_MODE, false), store.getIntPref(VIRT_MODE));
	}

	public boolean isPresent() {
		return audioEffectsEnabledDefined || equalizerEnabledDefined || equalizerPresetDefined ||
				equalizerBandsDefined || userPresetsDefined || bassBoostEnabledDefined ||
				bassBoostStrengthDefined || loudnessEnabledDefined || loudnessGainDefined ||
				virtualizerEnabledDefined || virtualizerStrengthDefined || virtualizerModeDefined;
	}

	public boolean requiresNativeTopology() {
		return equalizerBandsDefined || ((rawUserPresets.length > 0) && userPresetsDefined) ||
				(equalizerPresetDefined && (equalizerPreset != 0)) || equalizerEnabled;
	}

	public boolean audioEffectsEnabled() {
		return audioEffectsEnabled;
	}

	public boolean equalizerEnabled() {
		return equalizerEnabled;
	}

	public int equalizerPreset() {
		return equalizerPreset;
	}

	@Nullable
	public int[] rawEqualizerBands() {
		return (rawEqualizerBands == null) ? null : rawEqualizerBands.clone();
	}

	public String[] rawUserPresets() {
		return rawUserPresets.clone();
	}

	public boolean bassBoostEnabled() {
		return bassBoostEnabled;
	}

	public int bassBoostStrength() {
		return bassBoostStrength;
	}

	public boolean loudnessEnabled() {
		return loudnessEnabled;
	}

	public int loudnessGain() {
		return loudnessGain;
	}

	public boolean virtualizerEnabled() {
		return virtualizerEnabled;
	}

	public int virtualizerStrength() {
		return virtualizerStrength;
	}

	public int virtualizerMode() {
		return virtualizerMode;
	}

	boolean audioEffectsEnabledDefined() {
		return audioEffectsEnabledDefined;
	}

	boolean equalizerEnabledDefined() {
		return equalizerEnabledDefined;
	}

	boolean equalizerPresetDefined() {
		return equalizerPresetDefined;
	}

	boolean equalizerBandsDefined() {
		return equalizerBandsDefined;
	}

	boolean userPresetsDefined() {
		return userPresetsDefined;
	}

	boolean bassBoostEnabledDefined() {
		return bassBoostEnabledDefined;
	}

	boolean bassBoostStrengthDefined() {
		return bassBoostStrengthDefined;
	}

	boolean loudnessEnabledDefined() {
		return loudnessEnabledDefined;
	}

	boolean loudnessGainDefined() {
		return loudnessGainDefined;
	}

	boolean virtualizerEnabledDefined() {
		return virtualizerEnabledDefined;
	}

	boolean virtualizerStrengthDefined() {
		return virtualizerStrengthDefined;
	}

	boolean virtualizerModeDefined() {
		return virtualizerModeDefined;
	}

}
