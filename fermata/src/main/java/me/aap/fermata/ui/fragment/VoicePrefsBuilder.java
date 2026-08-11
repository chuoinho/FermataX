package me.aap.fermata.ui.fragment;

import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_LANG;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_AUTO_LANG;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROL_SUBST;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_ENABLED;
import static me.aap.fermata.ui.activity.MainActivityPrefs.VOICE_CONTROl_FB;

import android.view.KeyEvent;

import java.util.concurrent.TimeoutException;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;

final class VoicePrefsBuilder {
	private VoicePrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet parent) {
		PreferenceSet voice = parent.subSet(o -> o.title = R.string.voice_control);
		voice.addBooleanPref(o -> {
			o.title = R.string.enable;
			o.pref = VOICE_CONTROl_ENABLED;
			o.store = activity.getPrefs();
		});
		addVehicleTrigger(activity, voice);
		if (activity.isCarActivityNotMirror()) return;

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
		voice.addBooleanPref(o -> {
			o.title = R.string.voice_control_auto_language;
			o.subtitle = R.string.voice_control_auto_language_sub;
			o.pref = VOICE_CONTROL_AUTO_LANG;
			o.store = activity.getPrefs();
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

	private static void addVehicleTrigger(MainActivityDelegate activity, PreferenceSet voice) {
		var router = activity.getMediaSessionCallback().getHardwareInputRouter();
		int binding = router.getVoiceTriggerBinding();
		String current = (binding == KeyEvent.KEYCODE_UNKNOWN) ?
				activity.getString(R.string.voice_trigger_unassigned) :
				KeyEvent.keyCodeToString(binding) + " (" + binding + ')';
		voice.addButton(o -> {
			o.title = R.string.voice_trigger_assign;
			o.csubtitle = activity.getString(R.string.voice_trigger_current, current);
			o.onClick = () -> {
				UiUtils.showInfo(activity.getContext(), R.string.voice_trigger_capture_started);
				router.beginVoiceTriggerCapture(activity).onCompletion((keyCode, error) -> {
					if (error == null) {
						UiUtils.showInfo(activity.getContext(), activity.getString(
								R.string.voice_trigger_capture_success,
								KeyEvent.keyCodeToString(keyCode), keyCode));
					} else if (error instanceof TimeoutException) {
						UiUtils.showInfo(activity.getContext(), R.string.voice_trigger_capture_timeout);
					} else if (!isCancellation(error)) {
						UiUtils.showInfo(activity.getContext(), error.getLocalizedMessage());
					}
				});
			};
		});
		voice.addButton(o -> {
			o.title = R.string.voice_trigger_clear;
			o.onClick = () -> {
				router.clearVoiceTriggerBinding();
				UiUtils.showInfo(activity.getContext(), R.string.voice_trigger_cleared);
			};
		});
	}
}
