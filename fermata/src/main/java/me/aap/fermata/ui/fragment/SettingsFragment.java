package me.aap.fermata.ui.fragment;

import static me.aap.fermata.media.pref.MediaPrefs.SUB_LANG;
import static me.aap.fermata.media.pref.MediaPrefs.SUB_SIZE;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.AddonRegistry;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.pref.DiagnosticsPreferences;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PrefCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.PreferenceViewAdapter;

/**
 * @author Andrey Pavlenko
 */
public class SettingsFragment extends MainActivityFragment
		implements MainActivityListener, PreferenceStore.Listener {
	private PreferenceViewAdapter adapter;
	private final List<AddonPrefsBuilder> addonPrefsBuilders = new ArrayList<>();
	private MainActivityDelegate activityDelegate;
	private boolean viewActive;
	private long viewGeneration;
	private DiagnosticsPreferences diagnosticsPreferences;

	@Override
	public int getFragmentId() {
		return R.id.settings_fragment;
	}

	@Override
	public CharSequence getTitle() {
		if (adapter != null) {
			var set = adapter.getPreferenceSet();
			if (set.getParent() != null) {
				var o = set.get();
				return (o.ctitle != null) ? o.ctitle : getResources().getString(o.title);
			}
		}
		return getResources().getString(R.string.settings);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return inflater.inflate(me.aap.utils.R.layout.pref_list_view, container, false);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (adapter != null) outState.putInt("id", adapter.getPreferenceSet().getId());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		super.onViewCreated(view, state);
		viewActive = true;
		long generation = ++viewGeneration;
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
			if (!viewActive || (generation != viewGeneration) || !isAdded() ||
					(getView() != view)) return;
			activityDelegate = a;
			adapter = createAdapter(a);
			a.addBroadcastListener(this);
			a.getPrefs().addBroadcastListener(this);

			RecyclerView listView = view.findViewById(me.aap.utils.R.id.prefs_list_view);
			listView.setHasFixedSize(true);
			listView.setLayoutManager(new LinearLayoutManager(getContext()));
			listView.setAdapter(adapter);

			if (state != null) {
				PreferenceSet p = adapter.getPreferenceSet().find(state.getInt("id", ID_NULL));
				if (p != null) adapter.setPreferenceSet(p);
			}
		});
	}

	@Override
	public void onDestroyView() {
		viewActive = false;
		viewGeneration++;
		cleanUp(activityDelegate);
		super.onDestroyView();
	}

	private void cleanUp(MainActivityDelegate a) {
		viewActive = false;
		if (a != null) {
			a.removeBroadcastListener(this);
			a.getPrefs().removeBroadcastListener(this);
		}
		activityDelegate = null;
		for (AddonPrefsBuilder builder : addonPrefsBuilders) builder.close();
		addonPrefsBuilders.clear();
		if (adapter != null) adapter.onDestroy();
		adapter = null;
		diagnosticsPreferences = null;
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) cleanUp(a);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		MainActivityDelegate a = activityDelegate;
		if (!viewActive || (adapter == null) || (a == null)) return;
		if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) adapter.setSize(a.getTextIconSize());
	}

	@Override
	public boolean isRootPage() {
		return (adapter == null) || (adapter.getPreferenceSet().getParent() == null);
	}

	@Override
	public boolean onBackPressed() {
		if (adapter == null) return false;
		PreferenceSet p = adapter.getPreferenceSet().getParent();
		if (p == null) return false;
		adapter.setPreferenceSet(p);
		return true;
	}

	public static void addDelayPrefs(PreferenceSet set, PreferenceStore store,
																	 Pref<IntSupplier> pref,
																	 @StringRes int title, ChangeableCondition visibility) {
		set.addIntPref(o -> {
			o.store = store;
			o.pref = pref;
			o.title = title;
			o.seekMin = -5000;
			o.seekMax = 5000;
			o.seekScale = 50;
			o.ems = 3;
			o.visibility = visibility;
		});
	}

	public static void addSubSizePrefs(PreferenceSet set, PreferenceStore store,
																		 ChangeableCondition visibility) {
		if (store instanceof MediaLib.PlayableItem p && !p.isVideo()) return;
		set.addFloatPref(o -> {
			o.store = store;
			o.pref = SUB_SIZE;
			o.title = R.string.subtitles_size;
			o.scale = 0.05f;
			o.seekMin = 10;
			o.seekMax = 40;
			o.visibility = visibility;
		});
	}

	public static void addAudioPrefs(PreferenceSet set, PreferenceStore store, boolean isCar) {
		addDelayPrefs(set, store, MediaLibPrefs.AUDIO_DELAY, R.string.audio_delay, null);

		if (BuildConfig.AUTO) {
			addDelayPrefs(set, store, MediaLibPrefs.AUDIO_DELAY_AA, R.string.audio_delay_aa, null);
		}

		if (!isCar) {
			set.addStringPref(o -> {
				Locale locale = Locale.getDefault();
				o.store = store;
				o.pref = MediaLibPrefs.AUDIO_LANG;
				o.title = R.string.preferred_audio_lang;
				o.stringHint = locale.getLanguage() + ' ' + locale.getISO3Language();
			});
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.AUDIO_KEY;
				o.title = R.string.preferred_audio_key;
				o.stringHint = "studio1 studio2 default";
			});
		}
	}

	public static void addSubtitlePrefs(Context ctx, PreferenceSet set, PreferenceStore store,
																			boolean isCar) {
		set.addBooleanPref(o -> {
			o.store = store;
			o.pref = MediaLibPrefs.SUB_ENABLED;
			o.title = R.string.display_subtitles;
		});

		var enabled = PrefCondition.create(store, MediaLibPrefs.SUB_ENABLED);
		addDelayPrefs(set, store, MediaLibPrefs.SUB_DELAY, R.string.subtitle_delay, enabled.copy());
		addSubSizePrefs(set, store, enabled.copy());

		if (!isCar) {
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.SUB_LANG;
				o.title = R.string.preferred_sub_lang;
				o.stringHint = SUB_LANG.getDefaultValue().get();
				o.visibility = enabled.copy();
			});
			set.addStringPref(o -> {
				o.store = store;
				o.pref = MediaLibPrefs.SUB_KEY;
				o.title = R.string.preferred_sub_key;
				o.stringHint = "full, forced";
				o.visibility = enabled.copy();
			});
		}
	}

	private PreferenceViewAdapter createAdapter(MainActivityDelegate a) {
		MediaLibPrefs mediaPrefs = a.getMediaServiceBinder().getLib().getPrefs();
		boolean isCar = a.isCarActivityNotMirror();
		PreferenceSet set = new PreferenceSet();
		PreferenceSet sub1;

		sub1 = set.subSet(o -> o.title = R.string.interface_prefs);

		if (BuildConfig.AUTO && a.isCarActivityNotMirror()) {
			InterfacePrefsBuilder.addAndroidAuto(a, sub1);
		} else {
			if (BuildConfig.AUTO) {
				InterfacePrefsBuilder.addAndroidAuto(a,
						sub1.subSet(o -> o.title = R.string.interface_prefs_aa));
			}
			InterfacePrefsBuilder.add(a, sub1, MainActivityPrefs.THEME_MAIN,
					MainActivityPrefs.SHOW_PG_UP_DOWN, null,
					MainActivityPrefs.NAV_BAR_POS, MainActivityPrefs.NAV_BAR_SIZE,
					MainActivityPrefs.TOOL_BAR_SIZE, MainActivityPrefs.CONTROL_PANEL_SIZE,
					MainActivityPrefs.TEXT_ICON_SIZE);
		}

		sub1.addBooleanPref(o -> {
			o.store = mediaPrefs;
			o.pref = BrowsableItemPrefs.SHOW_TRACK_ICONS;
			o.title = R.string.show_track_icons;
		});
		sub1.addListPref(o -> {
			o.store = a.getPrefs();
			o.pref = MainActivityPrefs.LOCALE;
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.removeDefault = false;
			o.cmp = String::compareTo;
			o.stringValues = CollectionUtils.mapToArray(MainActivityPrefs.Lang.getValues(),
					v -> v.locale.getDisplayName(), String[]::new);
		});
		if (!a.isCarActivityNotMirror()) {
			sub1.addButton(o -> {
				o.title = R.string.initial_setup_reopen;
				o.subtitle = R.string.initial_setup_reopen_sub;
				o.onClick = () -> a.showFragment(R.id.initial_setup_fragment);
			});
		}
		KeyBindingPrefsBuilder.add(sub1);

		PreferenceSet playback = set.subSet(o -> o.title = R.string.playback_settings);
		PlaybackPrefsBuilder.add(a, playback, mediaPrefs);
		MediaEnginePrefsBuilder.add(a, playback, mediaPrefs, isCar);

		VoicePrefsBuilder.add(a, set);

		DashboardPrefsBuilder.add(a, set, this::refreshPrefs);
		addAddons(set);

		diagnosticsPreferences = new DiagnosticsPreferences(a.getContext());
		diagnosticsPreferences.disableIfExpired(System.currentTimeMillis());
		PreferenceSet diagnostics = set.subSet(o -> {
			o.title = R.string.diagnostics;
			o.subtitle = R.string.diagnostics_sub;
		});
		long diagnosticsGeneration = viewGeneration;
		BooleanSupplier diagnosticsUiActive = () -> viewActive &&
				(viewGeneration == diagnosticsGeneration) && (activityDelegate == a) && isAdded();
		diagnostics.addBooleanPref(o -> {
			o.store = diagnosticsPreferences.getStore();
			o.pref = DiagnosticsPreferences.DETAILED_ENABLED;
			o.title = R.string.detailed_diagnostics;
			long remaining = diagnosticsPreferences.getRemainingMillis(System.currentTimeMillis());
			if (remaining > 0L) {
				long hours = Math.max(1L, (remaining + 3_599_999L) / 3_600_000L);
				o.csubtitle = getString(R.string.detailed_diagnostics_sub) + " " +
						hours + " " + getString(R.string.hours);
			} else {
				o.subtitle = R.string.detailed_diagnostics_sub;
			}
		});
		diagnostics.addButton(o -> {
			o.title = R.string.export_diagnostic_report;
			o.subtitle = R.string.export_diagnostic_report_sub;
			o.onClick = () -> SettingsDiagnosticsManager.exportReport(a, diagnosticsUiActive);
		});
		diagnostics.addButton(o -> {
			o.title = R.string.clear_diagnostic_data;
			o.subtitle = R.string.clear_diagnostic_data_sub;
			o.onClick = () -> SettingsDiagnosticsManager.clear(a, diagnosticsUiActive);
		});

		if (!a.isCarActivityNotMirror()) {
			sub1 = set.subSet(o -> o.title = R.string.other);
			PreferenceSet backup = sub1.subSet(o -> {
				o.title = R.string.backup_restore;
				o.subtitle = R.string.backup_restore_sub;
			});
			backup.addButton(o -> {
				o.title = R.string.backup_all_data;
				o.subtitle = R.string.backup_all_data_sub;
				o.onClick = () -> SettingsBackupManager.backupAllData(a);
			});
			backup.addButton(o -> {
				o.title = R.string.restore_backup;
				o.subtitle = R.string.restore_backup_sub;
				o.onClick = () -> SettingsBackupManager.restoreBackup(a);
			});
		}

		return new PreferenceViewAdapter(set) {
			@Override
			public void setPreferenceSet(PreferenceSet set) {
				super.setPreferenceSet(set);
				a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			}
		};
	}

	private void refreshPrefs(PreferenceSet set) {
		if (adapter != null) adapter.setPreferenceSet(set);
	}

	private void addAddons(PreferenceSet set) {
		AddonManager amgr = FermataApplication.get().getAddonManager();
		PreferenceSet sub = set.subSet(o -> o.title = R.string.addons);
		PreferenceStore store = FermataApplication.get().getPreferenceStore();

		for (AddonInfo addon : AddonRegistry.get().getAll()) {
			if (!addon.hasSettings) continue;
			AddonPrefsBuilder b = new AddonPrefsBuilder(amgr, addon, store, this::getContext);
			addonPrefsBuilders.add(b);
			PreferenceSet sub1 = sub.subSet(b);
			sub1.configure(b::configure);
		}
	}
}
