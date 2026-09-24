package me.aap.fermata.ui.fragment;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.pref.PreferenceSet;

final class PlaybackPrefsBuilder {
	private PlaybackPrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet parent, MediaLibPrefs mediaPrefs,
			AudioEffectsProfileRepository profiles, AudioEffectsDraft draft) {
		AudioEffectsPrefsBuilder.add(activity.getContext(), parent, profiles, draft,
				activity.getMediaSessionCallback());

		if (BuildConfig.AUTO) {
			SettingsFragment.addDelayPrefs(parent, mediaPrefs, MediaLibPrefs.AUDIO_DELAY_AA,
					R.string.audio_delay_aa, null);
		} else {
			SettingsFragment.addDelayPrefs(parent, mediaPrefs, MediaLibPrefs.AUDIO_DELAY,
					R.string.audio_delay, null);
		}

		parent.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.PLAY_NEXT;
			o.title = R.string.play_next_on_completion;
		});
	}
}
