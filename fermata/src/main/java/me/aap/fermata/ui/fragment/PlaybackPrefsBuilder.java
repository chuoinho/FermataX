package me.aap.fermata.ui.fragment;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore.Pref;

final class PlaybackPrefsBuilder {
	private PlaybackPrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet parent, MediaLibPrefs mediaPrefs) {
		PreferenceSet playback = parent.subSet(o -> o.title = R.string.playback_settings);
		playback.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.PLAY_NEXT;
			o.title = R.string.play_next_on_completion;
		});

		PreferenceSet controls = parent.subSet(o -> o.title = R.string.playback_control);
		int[] timeUnits = new int[]{R.string.time_unit_second, R.string.time_unit_minute,
				R.string.time_unit_percent};
		addSeekControls(controls, activity, timeUnits);

		controls.addBooleanPref(o -> {
			o.store = activity.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PLAY_PAUSE_STOP;
			o.title = R.string.play_pause_stop;
		});

		PreferenceSet video = controls.subSet(o -> o.title = R.string.video_control);
		video.addIntPref(o -> {
			o.store = activity.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_START_DELAY;
			o.title = R.string.video_control_start_delay;
			o.seekMax = 60;
		});
		video.addIntPref(o -> {
			o.store = activity.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_TOUCH_DELAY;
			o.title = R.string.video_control_touch_delay;
			o.seekMax = 60;
		});
		video.addIntPref(o -> {
			o.store = activity.getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.VIDEO_CONTROL_SEEK_DELAY;
			o.title = R.string.video_control_seek_delay;
			o.seekMax = 60;
		});

		if (BuildConfig.AUTO) {
			video.addBooleanPref(o -> {
				o.store = activity.getPlaybackControlPrefs();
				o.pref = PlaybackControlPrefs.VIDEO_AA_SHOW_STATUS;
				o.title = R.string.video_aa_show_status;
			});
		}
	}

	private static void addSeekControls(PreferenceSet controls, MainActivityDelegate activity,
										int[] timeUnits) {
		addSeekControl(controls, activity, timeUnits, R.string.rw_ff_click,
				PlaybackControlPrefs.RW_FF_TIME, PlaybackControlPrefs.RW_FF_TIME_UNIT);
		addSeekControl(controls, activity, timeUnits, R.string.rw_ff_long_click,
				PlaybackControlPrefs.RW_FF_LONG_TIME, PlaybackControlPrefs.RW_FF_LONG_TIME_UNIT);
		addSeekControl(controls, activity, timeUnits, R.string.prev_next_long_click,
				PlaybackControlPrefs.PREV_NEXT_LONG_TIME,
				PlaybackControlPrefs.PREV_NEXT_LONG_TIME_UNIT);
	}

	private static void addSeekControl(PreferenceSet controls, MainActivityDelegate activity,
									 int[] timeUnits, int title,
									 Pref<IntSupplier> time, Pref<IntSupplier> unit) {
		PreferenceSet set = controls.subSet(o -> o.title = title);
		set.addIntPref(o -> {
			o.store = activity.getPlaybackControlPrefs();
			o.pref = time;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		set.addListPref(o -> {
			o.store = activity.getPlaybackControlPrefs();
			o.pref = unit;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});
	}
}
