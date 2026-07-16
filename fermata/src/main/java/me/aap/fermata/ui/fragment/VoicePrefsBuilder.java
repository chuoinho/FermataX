package me.aap.fermata.ui.fragment;

import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_LANG;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;

final class VoicePrefsBuilder {
	private VoicePrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet parent) {
		if (activity.isCarActivityNotMirror()) return;

		PreferenceSet voice = parent.subSet(o -> o.title = R.string.voice_control);
		voice.addBooleanPref(o -> {
			o.title = R.string.enable;
			o.pref = VOICE_CONTROl_ENABLED;
			o.store = activity.getPrefs();
		});
		voice.addBooleanPref(o -> {
			o.title = R.string.voice_control_fb;
			o.subtitle = R.string.voice_control_sub_long;
			o.pref = VOICE_CONTROl_FB;
			o.store = activity.getPrefs();
			o.visibility = PrefCondition.create(activity.getPrefs(), VOICE_CONTROl_ENABLED);
		});
		voice.addStringPref(o -> {
			o.title = R.string.voice_control_subst;
			o.subtitle = R.string.voice_control_subst_sub;
			o.hint = R.string.voice_control_subst_hint;
			o.pref = VOICE_CONTROL_SUBST;
			o.store = activity.getPrefs();
			o.maxLines = 10;
			o.visibility = PrefCondition.create(activity.getPrefs(), VOICE_CONTROl_ENABLED);
		});
		voice.addTtsLocalePref(o -> {
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.pref = VOICE_CONTROL_LANG;
			o.store = activity.getPrefs();
			o.formatSubtitle = true;
			o.visibility = PrefCondition.create(activity.getPrefs(), VOICE_CONTROl_ENABLED);
		});
	}
}
