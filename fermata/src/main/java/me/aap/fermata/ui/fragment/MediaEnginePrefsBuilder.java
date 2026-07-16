package me.aap.fermata.ui.fragment;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_DEFAULT;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_SYSTEM;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_SCANNER_VLC;

import me.aap.fermata.R;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceView;

final class MediaEnginePrefsBuilder {
	private MediaEnginePrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet parent, MediaLibPrefs mediaPrefs,
						 boolean isCar) {
		PrefCondition<BooleanSupplier> exoCondition =
				PrefCondition.create(mediaPrefs, MediaLibPrefs.EXO_ENABLED);
		PrefCondition<BooleanSupplier> vlcCondition =
				PrefCondition.create(mediaPrefs, MediaLibPrefs.VLC_ENABLED);
		Consumer<PreferenceView.ListOpts> initEngineList = o -> {
			if (o.visibility == null) o.visibility = exoCondition.or(vlcCondition);
			o.values = new int[]{R.string.engine_mp_name, R.string.engine_exo_name,
					R.string.engine_vlc_name};
			o.valuesMap = new int[]{MEDIA_ENG_MP, MEDIA_ENG_EXO, MEDIA_ENG_VLC};
			o.valuesFilter = i -> {
				if (i == 1) return exoCondition.get();
				if (i == 2) return vlcCondition.get();
				return true;
			};
		};

		PreferenceSet engines = parent.subSet(o -> o.title = R.string.engine_prefs);
		engines.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.EXO_ENABLED;
			o.title = R.string.enable_exoplayer;
		});
		engines.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.VLC_ENABLED;
			o.title = R.string.enable_vlcplayer;
		});
		engines.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.AUDIO_ENGINE;
			o.title = R.string.preferred_audio_engine;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.initList = initEngineList;
		});
		engines.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.VIDEO_ENGINE;
			o.title = R.string.preferred_video_engine;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.initList = initEngineList;
		});
		engines.addListPref(o -> {
			o.store = mediaPrefs;
			o.removeDefault = false;
			o.pref = MediaLibPrefs.MEDIA_SCANNER;
			o.title = R.string.preferred_media_scanner;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = vlcCondition;
			o.values = new int[]{R.string.preferred_media_scanner_default,
					R.string.preferred_media_scanner_system, R.string.engine_vlc_name};
			o.valuesMap = new int[]{MEDIA_SCANNER_DEFAULT, MEDIA_SCANNER_SYSTEM, MEDIA_SCANNER_VLC};
		});

		PreferenceSet video = parent.subSet(o -> o.title = R.string.video_settings);
		video.addListPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaLibPrefs.VIDEO_SCALE;
			o.title = R.string.video_scaling;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.video_scaling_best, R.string.video_scaling_fill,
					R.string.video_scaling_orig, R.string.video_scaling_4, R.string.video_scaling_16};
		});
		video.addListPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaLibPrefs.HW_ACCEL;
			o.title = R.string.hw_accel;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.hw_accel_auto, R.string.hw_accel_full,
					R.string.hw_accel_decoding, R.string.hw_accel_disabled};
			o.visibility = vlcCondition;
		});
		video.addListPref(o -> {
			o.store = activity.getPrefs();
			o.pref = MainActivityPrefs.CLOCK_POS;
			o.title = R.string.clock_pos;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.clock_pos_none, R.string.clock_pos_left,
					R.string.clock_pos_right, R.string.clock_pos_center};
		});
		video.addBooleanPref(o -> {
			o.store = activity.getPrefs();
			o.pref = MainActivityPrefs.SYS_BARS_ON_VIDEO_TOUCH;
			o.title = R.string.sys_bars_on_video_touch;
		});
		video.addBooleanPref(o -> {
			o.store = activity.getPrefs();
			o.pref = MainActivityPrefs.LANDSCAPE_VIDEO;
			o.title = R.string.play_video_landscape;
		});
		video.addBooleanPref(o -> {
			o.store = activity.getPrefs();
			o.pref = MainActivityPrefs.CHANGE_BRIGHTNESS;
			o.title = R.string.change_brightness;
		});
		video.addIntPref(o -> {
			o.store = activity.getPrefs();
			o.pref = MainActivityPrefs.BRIGHTNESS;
			o.title = R.string.video_brightness;
			o.subtitle = R.string.change_brightness_sub;
			o.seekMin = 0;
			o.seekMax = 255;
			o.visibility = PrefCondition.create(activity.getPrefs(),
					MainActivityPrefs.CHANGE_BRIGHTNESS);
		});

		PreferenceSet audio = video.subSet(o -> {
			o.title = R.string.audio;
			o.visibility = vlcCondition;
		});
		SettingsFragment.addAudioPrefs(audio, mediaPrefs, isCar);

		video.addIntPref(o -> {
			o.store = mediaPrefs;
			o.pref = MediaPrefs.WATCHED_THRESHOLD;
			o.title = R.string.watched_threshold;
			o.subtitle = R.string.watched_threshold_sub;
			o.seekMin = 0;
			o.seekMax = 100;
			o.seekScale = 5;
		});
	}
}
