package me.aap.fermata.media.audio;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;

import java.util.Arrays;
import java.util.Objects;

/**
 * Portable audio-effect configuration. The curve is expressed in whole dB at canonical
 * frequencies; mapping it to a device Equalizer belongs to a later playback phase.
 */
public final class AudioEffectsProfile {
	public static final int SCHEMA_VERSION = 1;
	public static final int[] CANONICAL_FREQ_HZ = {
			31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000
	};
	private static final int[] FLAT_CURVE_DB = new int[CANONICAL_FREQ_HZ.length];
	private final int schemaVersion;
	private final boolean enabled;
	private final boolean equalizerEnabled;
	private final int[] canonicalCurveDb;
	private final int preampDb;
	private final boolean bassBoostEnabled;
	private final int bassBoostStrength;
	private final boolean loudnessEnabled;
	private final int loudnessGain;
	private final boolean virtualizerEnabled;
	private final int virtualizerStrength;
	private final int virtualizerMode;

	public AudioEffectsProfile(int schemaVersion, boolean enabled, boolean equalizerEnabled,
			int[] canonicalCurveDb, int preampDb, boolean bassBoostEnabled,
			int bassBoostStrength, boolean loudnessEnabled, int loudnessGain,
			boolean virtualizerEnabled, int virtualizerStrength, int virtualizerMode) {
		if (canonicalCurveDb.length != CANONICAL_FREQ_HZ.length) {
			throw new IllegalArgumentException("Expected " + CANONICAL_FREQ_HZ.length + " EQ bands");
		}
		this.schemaVersion = schemaVersion;
		this.enabled = enabled;
		this.equalizerEnabled = equalizerEnabled;
		this.canonicalCurveDb = canonicalCurveDb.clone();
		this.preampDb = preampDb;
		this.bassBoostEnabled = bassBoostEnabled;
		this.bassBoostStrength = bassBoostStrength;
		this.loudnessEnabled = loudnessEnabled;
		this.loudnessGain = loudnessGain;
		this.virtualizerEnabled = virtualizerEnabled;
		this.virtualizerStrength = virtualizerStrength;
		this.virtualizerMode = virtualizerMode;
	}

	public static AudioEffectsProfile defaults() {
		return new AudioEffectsProfile(SCHEMA_VERSION, false, false, flatCurveDb(), 0,
				false, 0, false, 0, false, 0, VIRTUALIZATION_MODE_AUTO);
	}

	public static int[] flatCurveDb() {
		return FLAT_CURVE_DB.clone();
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public boolean enabled() {
		return enabled;
	}

	public boolean equalizerEnabled() {
		return equalizerEnabled;
	}

	public int[] canonicalCurveDb() {
		return canonicalCurveDb.clone();
	}

	public int preampDb() {
		return preampDb;
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

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof AudioEffectsProfile profile)) return false;
		return (schemaVersion == profile.schemaVersion) && (enabled == profile.enabled) &&
				(equalizerEnabled == profile.equalizerEnabled) && (preampDb == profile.preampDb) &&
				(bassBoostEnabled == profile.bassBoostEnabled) &&
				(bassBoostStrength == profile.bassBoostStrength) &&
				(loudnessEnabled == profile.loudnessEnabled) &&
				(loudnessGain == profile.loudnessGain) &&
				(virtualizerEnabled == profile.virtualizerEnabled) &&
				(virtualizerStrength == profile.virtualizerStrength) &&
				(virtualizerMode == profile.virtualizerMode) &&
				Arrays.equals(canonicalCurveDb, profile.canonicalCurveDb);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(schemaVersion, enabled, equalizerEnabled, preampDb,
				bassBoostEnabled, bassBoostStrength, loudnessEnabled, loudnessGain,
				virtualizerEnabled, virtualizerStrength, virtualizerMode);
		return 31 * result + Arrays.hashCode(canonicalCurveDb);
	}
}
