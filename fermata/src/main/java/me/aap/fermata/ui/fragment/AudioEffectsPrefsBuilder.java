package me.aap.fermata.ui.fragment;

import android.content.Context;

import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.ui.UiUtils;

/** Builds the engine-independent Settings entry for the unified audio profile. */
final class AudioEffectsPrefsBuilder {
	private AudioEffectsPrefsBuilder() {
	}

	static void add(Context context, PreferenceSet parent, AudioEffectsProfileRepository profiles,
			AudioEffectsDraft draft, MediaSessionCallback callback) {
		if (profiles.consumeLegacyNativePresetMigrationNotice()) {
			UiUtils.showInfo(context, R.string.legacy_preset_migration_notice);
		}
		PreferenceSet effects = parent.subSet(o -> o.title = R.string.audio_equalizer);
		effects.addView(o -> o.view = () -> new me.aap.fermata.ui.view.AudioEffectsScreenView(
				context, draft, callback));
	}
}
