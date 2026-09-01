package me.aap.fermata.ui.fragment;

import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_AUTO;
import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_BINAURAL;
import static android.media.audiofx.Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL;

import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;

/** Builds the engine-independent Settings entry for the unified audio profile. */
final class AudioEffectsPrefsBuilder {
	private AudioEffectsPrefsBuilder() {
	}

	static void add(PreferenceSet parent, AudioEffectsProfileRepository profiles) {
		PreferenceStore store = profiles.getStore();
		PreferenceSet effects = parent.subSet(o -> o.title = R.string.audio_equalizer);
		effects.addBooleanPref(o -> {
			o.store = store;
			o.pref = AudioEffectsProfileRepository.ENABLED;
			o.title = R.string.enable;
		});

		PrefCondition<BooleanSupplier> profileEnabled = PrefCondition.create(store,
				AudioEffectsProfileRepository.ENABLED);
		PreferenceSet equalizer = effects.subSet(o -> {
			o.title = R.string.equalier;
			o.visibility = profileEnabled.copy();
		});
		equalizer.addBooleanPref(o -> {
			o.store = store;
			o.pref = AudioEffectsProfileRepository.EQUALIZER_ENABLED;
			o.title = R.string.enable;
		});
		ChangeableCondition equalizerEnabled = PrefCondition.create(store,
				AudioEffectsProfileRepository.EQUALIZER_ENABLED).and(profileEnabled.copy());
		for (int i = 0; i < AudioEffectsProfile.CANONICAL_FREQ_HZ.length; i++) {
			int band = i;
			int frequency = AudioEffectsProfile.CANONICAL_FREQ_HZ[band];
			equalizer.addIntPref(o -> {
				o.store = store;
				o.pref = AudioEffectsProfileRepository.CANONICAL_CURVE_DB[band];
				o.ctitle = frequencyLabel(frequency);
				o.seekMin = -15;
				o.seekMax = 15;
				o.visibility = equalizerEnabled.copy();
			});
		}

		PreferenceSet bass = effects.subSet(o -> {
			o.title = R.string.bass_boost;
			o.visibility = profileEnabled.copy();
		});
		bass.addBooleanPref(o -> {
			o.store = store;
			o.pref = AudioEffectsProfileRepository.BASS_BOOST_ENABLED;
			o.title = R.string.enable;
		});
		addStrength(bass, store, AudioEffectsProfileRepository.BASS_BOOST_STRENGTH,
				PrefCondition.create(store, AudioEffectsProfileRepository.BASS_BOOST_ENABLED)
						.and(profileEnabled.copy()));

		PreferenceSet loudness = effects.subSet(o -> {
			o.title = R.string.vol_boost;
			o.visibility = profileEnabled.copy();
		});
		loudness.addBooleanPref(o -> {
			o.store = store;
			o.pref = AudioEffectsProfileRepository.LOUDNESS_ENABLED;
			o.title = R.string.enable;
		});
		addStrength(loudness, store, AudioEffectsProfileRepository.LOUDNESS_GAIN,
				PrefCondition.create(store, AudioEffectsProfileRepository.LOUDNESS_ENABLED)
						.and(profileEnabled.copy()));

		PreferenceSet virtualizer = effects.subSet(o -> {
			o.title = R.string.virtualizer;
			o.visibility = profileEnabled.copy();
		});
		virtualizer.addBooleanPref(o -> {
			o.store = store;
			o.pref = AudioEffectsProfileRepository.VIRTUALIZER_ENABLED;
			o.title = R.string.enable;
		});
		ChangeableCondition virtualizerEnabled = PrefCondition.create(store,
				AudioEffectsProfileRepository.VIRTUALIZER_ENABLED).and(profileEnabled.copy());
		addStrength(virtualizer, store, AudioEffectsProfileRepository.VIRTUALIZER_STRENGTH,
				virtualizerEnabled.copy());
		virtualizer.addListPref(o -> {
			o.store = store;
			o.pref = AudioEffectsProfileRepository.VIRTUALIZER_MODE;
			o.title = R.string.string_format;
			o.formatTitle = true;
			o.values = new int[]{R.string.auto, R.string.binaural, R.string.transaural};
			o.valuesMap = new int[]{VIRTUALIZATION_MODE_AUTO, VIRTUALIZATION_MODE_BINAURAL,
					VIRTUALIZATION_MODE_TRANSAURAL};
			o.visibility = virtualizerEnabled;
		});
	}

	private static void addStrength(PreferenceSet parent, PreferenceStore store,
			me.aap.utils.pref.PreferenceStore.Pref<me.aap.utils.function.IntSupplier> pref,
			ChangeableCondition visibility) {
		parent.addIntPref(o -> {
			o.store = store;
			o.pref = pref;
			o.title = R.string.strength;
			o.seekMin = 0;
			o.seekMax = 1_000;
			o.visibility = visibility;
		});
	}

	private static String frequencyLabel(int frequency) {
		return (frequency >= 1_000) ? (frequency / 1_000) + " kHz" : frequency + " Hz";
	}
}
