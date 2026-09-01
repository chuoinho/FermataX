package me.aap.fermata.media.audio;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

/**
 * The sole persistence authority for the portable audio-effects profile and its legacy snapshot.
 * It deliberately has no playback, engine, or audio-session dependency.
 */
public final class AudioEffectsProfileRepository {
	private static final String PREFIX = "AUDIO_EFFECTS_PROFILE_";
	static final Pref<IntSupplier> SCHEMA_VERSION = intPref("SCHEMA_VERSION", 0);
	public static final Pref<BooleanSupplier> ENABLED = booleanPref("ENABLED", false);
	public static final Pref<BooleanSupplier> EQUALIZER_ENABLED = booleanPref("EQUALIZER_ENABLED", false);
	public static final Pref<IntSupplier> PREAMP_DB = intPref("PREAMP_DB", 0);
	public static final Pref<BooleanSupplier> BASS_BOOST_ENABLED = booleanPref("BASS_BOOST_ENABLED", false);
	public static final Pref<IntSupplier> BASS_BOOST_STRENGTH = intPref("BASS_BOOST_STRENGTH", 0);
	public static final Pref<BooleanSupplier> LOUDNESS_ENABLED = booleanPref("LOUDNESS_ENABLED", false);
	public static final Pref<IntSupplier> LOUDNESS_GAIN = intPref("LOUDNESS_GAIN", 0);
	public static final Pref<BooleanSupplier> VIRTUALIZER_ENABLED = booleanPref("VIRTUALIZER_ENABLED", false);
	public static final Pref<IntSupplier> VIRTUALIZER_STRENGTH = intPref("VIRTUALIZER_STRENGTH", 0);
	public static final Pref<IntSupplier> VIRTUALIZER_MODE = intPref("VIRTUALIZER_MODE",
			VIRTUALIZATION_MODE_AUTO);
	public static final Pref<IntSupplier>[] CANONICAL_CURVE_DB = createCurvePrefs();

	private static final Pref<IntSupplier> MIGRATION_STATE = intPref("MIGRATION_STATE",
			MigrationState.NONE.ordinal());
	private static final Pref<BooleanSupplier> LEGACY_PRESENT = booleanPref("LEGACY_PRESENT", false);
	private static final Pref<BooleanSupplier> LEGACY_AE_DEFINED = booleanPref("LEGACY_AE_DEFINED", false);
	private static final Pref<BooleanSupplier> LEGACY_AE_ENABLED = booleanPref("LEGACY_AE_ENABLED", false);
	private static final Pref<BooleanSupplier> LEGACY_EQ_ENABLED_DEFINED = booleanPref("LEGACY_EQ_ENABLED_DEFINED", false);
	private static final Pref<BooleanSupplier> LEGACY_EQ_ENABLED = booleanPref("LEGACY_EQ_ENABLED", false);
	private static final Pref<BooleanSupplier> LEGACY_EQ_PRESET_DEFINED = booleanPref("LEGACY_EQ_PRESET_DEFINED", false);
	private static final Pref<IntSupplier> LEGACY_EQ_PRESET = intPref("LEGACY_EQ_PRESET", 0);
	private static final Pref<BooleanSupplier> LEGACY_EQ_BANDS_DEFINED = booleanPref("LEGACY_EQ_BANDS_DEFINED", false);
	private static final Pref<Supplier<int[]>> LEGACY_EQ_BANDS = intArrayPref("LEGACY_EQ_BANDS");
	private static final Pref<BooleanSupplier> LEGACY_USER_PRESETS_DEFINED = booleanPref("LEGACY_USER_PRESETS_DEFINED", false);
	private static final Pref<Supplier<String[]>> LEGACY_USER_PRESETS = stringArrayPref("LEGACY_USER_PRESETS");
	private static final Pref<BooleanSupplier> LEGACY_BASS_ENABLED_DEFINED = booleanPref("LEGACY_BASS_ENABLED_DEFINED", false);
	private static final Pref<BooleanSupplier> LEGACY_BASS_ENABLED = booleanPref("LEGACY_BASS_ENABLED", false);
	private static final Pref<BooleanSupplier> LEGACY_BASS_STRENGTH_DEFINED = booleanPref("LEGACY_BASS_STRENGTH_DEFINED", false);
	private static final Pref<IntSupplier> LEGACY_BASS_STRENGTH = intPref("LEGACY_BASS_STRENGTH", 0);
	private static final Pref<BooleanSupplier> LEGACY_LOUDNESS_ENABLED_DEFINED = booleanPref("LEGACY_LOUDNESS_ENABLED_DEFINED", false);
	private static final Pref<BooleanSupplier> LEGACY_LOUDNESS_ENABLED = booleanPref("LEGACY_LOUDNESS_ENABLED", false);
	private static final Pref<BooleanSupplier> LEGACY_LOUDNESS_GAIN_DEFINED = booleanPref("LEGACY_LOUDNESS_GAIN_DEFINED", false);
	private static final Pref<IntSupplier> LEGACY_LOUDNESS_GAIN = intPref("LEGACY_LOUDNESS_GAIN", 0);
	private static final Pref<BooleanSupplier> LEGACY_VIRTUALIZER_ENABLED_DEFINED = booleanPref("LEGACY_VIRTUALIZER_ENABLED_DEFINED", false);
	private static final Pref<BooleanSupplier> LEGACY_VIRTUALIZER_ENABLED = booleanPref("LEGACY_VIRTUALIZER_ENABLED", false);
	private static final Pref<BooleanSupplier> LEGACY_VIRTUALIZER_STRENGTH_DEFINED = booleanPref("LEGACY_VIRTUALIZER_STRENGTH_DEFINED", false);
	private static final Pref<IntSupplier> LEGACY_VIRTUALIZER_STRENGTH = intPref("LEGACY_VIRTUALIZER_STRENGTH", 0);
	private static final Pref<BooleanSupplier> LEGACY_VIRTUALIZER_MODE_DEFINED = booleanPref("LEGACY_VIRTUALIZER_MODE_DEFINED", false);
	private static final Pref<IntSupplier> LEGACY_VIRTUALIZER_MODE = intPref("LEGACY_VIRTUALIZER_MODE",
			VIRTUALIZATION_MODE_AUTO);

	private final PreferenceStore store;

	public AudioEffectsProfileRepository(PreferenceStore store) {
		this.store = store;
	}

	public PreferenceStore getStore() {
		ensureInitialized();
		return store;
	}

	public AudioEffectsProfile load() {
		ensureInitialized();
		int[] curve = new int[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
		for (int i = 0; i < curve.length; i++) curve[i] = store.getIntPref(CANONICAL_CURVE_DB[i]);
		return new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
				store.getBooleanPref(ENABLED), store.getBooleanPref(EQUALIZER_ENABLED), curve,
				store.getIntPref(PREAMP_DB), store.getBooleanPref(BASS_BOOST_ENABLED),
				store.getIntPref(BASS_BOOST_STRENGTH), store.getBooleanPref(LOUDNESS_ENABLED),
				store.getIntPref(LOUDNESS_GAIN), store.getBooleanPref(VIRTUALIZER_ENABLED),
				store.getIntPref(VIRTUALIZER_STRENGTH), store.getIntPref(VIRTUALIZER_MODE));
	}

	public void save(AudioEffectsProfile profile) {
		if (profile.schemaVersion() != AudioEffectsProfile.SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported audio-effects profile schema");
		}
		ensureInitialized();
		LegacyAudioEffectsSnapshot legacy = readLegacySnapshot();
		try (PreferenceStore.Edit edit = store.editPreferenceStore(false)) {
			writeProfile(edit, profile);
			if (legacy.isPresent()) edit.setIntPref(MIGRATION_STATE, MigrationState.DORMANT.ordinal());
		}
	}

	public MigrationState getMigrationState() {
		ensureInitialized();
		int value = store.getIntPref(MIGRATION_STATE);
		MigrationState[] values = MigrationState.values();
		return ((value >= 0) && (value < values.length)) ? values[value] : MigrationState.NONE;
	}

	public LegacyAudioEffectsSnapshot getLegacySnapshot() {
		ensureInitialized();
		return readLegacySnapshot();
	}

	private void ensureInitialized() {
		if (store.hasPref(SCHEMA_VERSION, false)) {
			int version = store.getIntPref(SCHEMA_VERSION);
			if (version == AudioEffectsProfile.SCHEMA_VERSION) return;
			if (version > AudioEffectsProfile.SCHEMA_VERSION) {
				throw new IllegalStateException("Audio-effects profile schema is newer than this app");
			}
		}
		LegacyAudioEffectsSnapshot legacy = LegacyAudioEffectsSnapshot.capture(store);
		MigrationState state = migrationStateFor(legacy);
		try (PreferenceStore.Edit edit = store.editPreferenceStore(false)) {
			writeProfile(edit, profileFrom(legacy));
			writeLegacySnapshot(edit, legacy);
			edit.setIntPref(SCHEMA_VERSION, AudioEffectsProfile.SCHEMA_VERSION);
			edit.setIntPref(MIGRATION_STATE, state.ordinal());
		}
	}

	private static AudioEffectsProfile profileFrom(LegacyAudioEffectsSnapshot legacy) {
		AudioEffectsProfile defaults = AudioEffectsProfile.defaults();
		boolean pendingTopology = legacy.requiresNativeTopology();
		return new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
				legacy.audioEffectsEnabled(), legacy.equalizerEnabled() && !pendingTopology,
				AudioEffectsProfile.flatCurveDb(), defaults.preampDb(), legacy.bassBoostEnabled(),
				legacy.bassBoostStrength(), legacy.loudnessEnabled(), legacy.loudnessGain(),
				legacy.virtualizerEnabled(), legacy.virtualizerStrength(), legacy.virtualizerMode());
	}

	private static MigrationState migrationStateFor(LegacyAudioEffectsSnapshot legacy) {
		if (!legacy.isPresent()) return MigrationState.NONE;
		return legacy.requiresNativeTopology() ? MigrationState.PENDING_NATIVE_TOPOLOGY :
				MigrationState.DORMANT;
	}

	private static void writeProfile(PreferenceStore.Edit edit, AudioEffectsProfile profile) {
		edit.setIntPref(SCHEMA_VERSION, AudioEffectsProfile.SCHEMA_VERSION);
		edit.setBooleanPref(ENABLED, profile.enabled());
		edit.setBooleanPref(EQUALIZER_ENABLED, profile.equalizerEnabled());
		edit.setIntPref(PREAMP_DB, profile.preampDb());
		edit.setBooleanPref(BASS_BOOST_ENABLED, profile.bassBoostEnabled());
		edit.setIntPref(BASS_BOOST_STRENGTH, profile.bassBoostStrength());
		edit.setBooleanPref(LOUDNESS_ENABLED, profile.loudnessEnabled());
		edit.setIntPref(LOUDNESS_GAIN, profile.loudnessGain());
		edit.setBooleanPref(VIRTUALIZER_ENABLED, profile.virtualizerEnabled());
		edit.setIntPref(VIRTUALIZER_STRENGTH, profile.virtualizerStrength());
		edit.setIntPref(VIRTUALIZER_MODE, profile.virtualizerMode());
		int[] curve = profile.canonicalCurveDb();
		for (int i = 0; i < curve.length; i++) edit.setIntPref(CANONICAL_CURVE_DB[i], curve[i]);
	}

	private LegacyAudioEffectsSnapshot readLegacySnapshot() {
		if (!store.getBooleanPref(LEGACY_PRESENT)) {
			return new LegacyAudioEffectsSnapshot(false, false, false, false, false, 0,
					false, null, false, new String[0], false, false, false, 0,
					false, false, false, 0, false, false, false, 0, false,
					VIRTUALIZATION_MODE_AUTO);
		}
		boolean bandsDefined = store.getBooleanPref(LEGACY_EQ_BANDS_DEFINED);
		return new LegacyAudioEffectsSnapshot(
				store.getBooleanPref(LEGACY_AE_DEFINED), store.getBooleanPref(LEGACY_AE_ENABLED),
				store.getBooleanPref(LEGACY_EQ_ENABLED_DEFINED), store.getBooleanPref(LEGACY_EQ_ENABLED),
				store.getBooleanPref(LEGACY_EQ_PRESET_DEFINED), store.getIntPref(LEGACY_EQ_PRESET),
				bandsDefined, bandsDefined ? store.getIntArrayPref(LEGACY_EQ_BANDS) : null,
				store.getBooleanPref(LEGACY_USER_PRESETS_DEFINED),
				store.getStringArrayPref(LEGACY_USER_PRESETS),
				store.getBooleanPref(LEGACY_BASS_ENABLED_DEFINED), store.getBooleanPref(LEGACY_BASS_ENABLED),
				store.getBooleanPref(LEGACY_BASS_STRENGTH_DEFINED), store.getIntPref(LEGACY_BASS_STRENGTH),
				store.getBooleanPref(LEGACY_LOUDNESS_ENABLED_DEFINED), store.getBooleanPref(LEGACY_LOUDNESS_ENABLED),
				store.getBooleanPref(LEGACY_LOUDNESS_GAIN_DEFINED), store.getIntPref(LEGACY_LOUDNESS_GAIN),
				store.getBooleanPref(LEGACY_VIRTUALIZER_ENABLED_DEFINED),
				store.getBooleanPref(LEGACY_VIRTUALIZER_ENABLED),
				store.getBooleanPref(LEGACY_VIRTUALIZER_STRENGTH_DEFINED),
				store.getIntPref(LEGACY_VIRTUALIZER_STRENGTH),
				store.getBooleanPref(LEGACY_VIRTUALIZER_MODE_DEFINED),
				store.getIntPref(LEGACY_VIRTUALIZER_MODE));
	}

	private static void writeLegacySnapshot(PreferenceStore.Edit edit,
			LegacyAudioEffectsSnapshot legacy) {
		edit.setBooleanPref(LEGACY_PRESENT, legacy.isPresent());
		edit.setBooleanPref(LEGACY_AE_DEFINED, legacy.audioEffectsEnabledDefined());
		edit.setBooleanPref(LEGACY_AE_ENABLED, legacy.audioEffectsEnabled());
		edit.setBooleanPref(LEGACY_EQ_ENABLED_DEFINED, legacy.equalizerEnabledDefined());
		edit.setBooleanPref(LEGACY_EQ_ENABLED, legacy.equalizerEnabled());
		edit.setBooleanPref(LEGACY_EQ_PRESET_DEFINED, legacy.equalizerPresetDefined());
		edit.setIntPref(LEGACY_EQ_PRESET, legacy.equalizerPreset());
		edit.setBooleanPref(LEGACY_EQ_BANDS_DEFINED, legacy.equalizerBandsDefined());
		int[] bands = legacy.rawEqualizerBands();
		if (bands != null) edit.setIntArrayPref(LEGACY_EQ_BANDS, bands);
		edit.setBooleanPref(LEGACY_USER_PRESETS_DEFINED, legacy.userPresetsDefined());
		edit.setStringArrayPref(LEGACY_USER_PRESETS, legacy.rawUserPresets());
		edit.setBooleanPref(LEGACY_BASS_ENABLED_DEFINED, legacy.bassBoostEnabledDefined());
		edit.setBooleanPref(LEGACY_BASS_ENABLED, legacy.bassBoostEnabled());
		edit.setBooleanPref(LEGACY_BASS_STRENGTH_DEFINED, legacy.bassBoostStrengthDefined());
		edit.setIntPref(LEGACY_BASS_STRENGTH, legacy.bassBoostStrength());
		edit.setBooleanPref(LEGACY_LOUDNESS_ENABLED_DEFINED, legacy.loudnessEnabledDefined());
		edit.setBooleanPref(LEGACY_LOUDNESS_ENABLED, legacy.loudnessEnabled());
		edit.setBooleanPref(LEGACY_LOUDNESS_GAIN_DEFINED, legacy.loudnessGainDefined());
		edit.setIntPref(LEGACY_LOUDNESS_GAIN, legacy.loudnessGain());
		edit.setBooleanPref(LEGACY_VIRTUALIZER_ENABLED_DEFINED, legacy.virtualizerEnabledDefined());
		edit.setBooleanPref(LEGACY_VIRTUALIZER_ENABLED, legacy.virtualizerEnabled());
		edit.setBooleanPref(LEGACY_VIRTUALIZER_STRENGTH_DEFINED, legacy.virtualizerStrengthDefined());
		edit.setIntPref(LEGACY_VIRTUALIZER_STRENGTH, legacy.virtualizerStrength());
		edit.setBooleanPref(LEGACY_VIRTUALIZER_MODE_DEFINED, legacy.virtualizerModeDefined());
		edit.setIntPref(LEGACY_VIRTUALIZER_MODE, legacy.virtualizerMode());
	}

	@SuppressWarnings("unchecked")
	private static Pref<IntSupplier>[] createCurvePrefs() {
		Pref<IntSupplier>[] prefs = new Pref[AudioEffectsProfile.CANONICAL_FREQ_HZ.length];
		for (int i = 0; i < prefs.length; i++) {
			prefs[i] = intPref("EQ_" + AudioEffectsProfile.CANONICAL_FREQ_HZ[i] + "_DB", 0);
		}
		return prefs;
	}

	private static Pref<BooleanSupplier> booleanPref(String name, boolean defaultValue) {
		return Pref.b(PREFIX + name, defaultValue).withInheritance(false);
	}

	private static Pref<IntSupplier> intPref(String name, int defaultValue) {
		return Pref.i(PREFIX + name, defaultValue).withInheritance(false);
	}

	private static Pref<Supplier<int[]>> intArrayPref(String name) {
		return Pref.ia(PREFIX + name, () -> null).withInheritance(false);
	}

	private static Pref<Supplier<String[]>> stringArrayPref(String name) {
		return Pref.sa(PREFIX + name, new String[0]).withInheritance(false);
	}
}
