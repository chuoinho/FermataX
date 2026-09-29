package me.aap.fermata.ui.fragment;

import static me.aap.fermata.media.pref.MediaPrefs.SUB_LANG;
import static me.aap.fermata.media.pref.MediaPrefs.SUB_SIZE;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import me.aap.utils.ui.UiUtils;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
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
import me.aap.fermata.media.audio.AudioEffectsDraft;
import me.aap.fermata.media.audio.AudioEffectsProfile;
import me.aap.fermata.media.audio.AudioEffectsProfileRepository;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.pref.DiagnosticsPreferences;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.util.Utils;
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
	private AudioEffectsDraft audioEffectsDraft;
	private AudioEffectsProfileRepository audioEffectsProfiles;
	@Nullable
	private ReadinessPrefsBuilder readinessPrefsBuilder;

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
		if (audioEffectsDraft != null) {
			outState.putBundle("audio_effects_draft", audioEffectsState(audioEffectsDraft.snapshot()));
		}
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
			restoreAudioEffectsDraft(state);
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
		if (audioEffectsDraft != null) {
			audioEffectsDraft.discard();
			audioEffectsDraft.close();
		}
		audioEffectsDraft = null;
		audioEffectsProfiles = null;
		if (adapter != null) adapter.onDestroy();
		adapter = null;
		diagnosticsPreferences = null;
		if (readinessPrefsBuilder != null) readinessPrefsBuilder.close();
		readinessPrefsBuilder = null;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (readinessPrefsBuilder != null) readinessPrefsBuilder.refresh();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && (readinessPrefsBuilder != null)) readinessPrefsBuilder.refresh();
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
		discardSelection();
		adapter.setPreferenceSet(p);
		return true;
	}

	@Override
	public void discardSelection() {
		if (audioEffectsDraft != null) audioEffectsDraft.discard();
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
		PreferenceSet set = new PreferenceSet();
		Context ctx = a.getContext();

		addSectionHeader(set, ctx, R.string.interface_prefs);
		InterfacePrefsBuilder.add(a, set);

		addSectionHeader(set, ctx, R.string.playback_settings);
		audioEffectsProfiles = new AudioEffectsProfileRepository(
				FermataApplication.get().getPreferenceStore());
		audioEffectsDraft = new AudioEffectsDraft(audioEffectsProfiles);
		PlaybackPrefsBuilder.add(a, set, mediaPrefs, audioEffectsProfiles, audioEffectsDraft);

		addSectionHeader(set, ctx, R.string.addons);
		addAddons(set);

		if (ReadinessPrefsBuilder.shouldAdd(a.getRuntimeHostMode())) {
			addSectionHeader(set, ctx, R.string.readiness_title);
			long readinessGeneration = viewGeneration;
			BooleanSupplier readinessUiActive = () -> viewActive && !isHidden() &&
					(viewGeneration == readinessGeneration) && (activityDelegate == a) && isAdded();
			readinessPrefsBuilder = new ReadinessPrefsBuilder(a, readinessUiActive);
			readinessPrefsBuilder.addTo(set);
		}

		diagnosticsPreferences = new DiagnosticsPreferences(a.getContext());
		diagnosticsPreferences.disableIfExpired(System.currentTimeMillis());
		addSectionHeader(set, ctx, R.string.diagnostics);
		long diagnosticsGeneration = viewGeneration;
		BooleanSupplier diagnosticsUiActive = () -> viewActive &&
				(viewGeneration == diagnosticsGeneration) && (activityDelegate == a) && isAdded();
		set.addBooleanPref(o -> {
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
		set.addButton(o -> {
			o.title = R.string.vehicle_key_test;
			o.subtitle = R.string.vehicle_key_test_sub;
			o.onClick = () -> SettingsDiagnosticsManager.testVehicleKeys(a, diagnosticsUiActive);
		});
		KeyBindingPrefsBuilder.add(set);
		set.addButton(o -> {
			o.title = R.string.export_diagnostic_report;
			o.subtitle = R.string.export_diagnostic_report_sub;
			o.onClick = () -> SettingsDiagnosticsManager.exportReport(a, diagnosticsUiActive);
		});
		set.addButton(o -> {
			o.title = R.string.clear_diagnostic_data;
			o.subtitle = R.string.clear_diagnostic_data_sub;
			o.onClick = () -> SettingsDiagnosticsManager.clear(a, diagnosticsUiActive);
		});

		if (!a.isCarActivityNotMirror()) {
			addSectionHeader(set, ctx, R.string.backup_restore);
			set.addButton(o -> {
				o.title = R.string.backup_all_data;
				o.subtitle = R.string.backup_all_data_sub;
				o.onClick = () -> SettingsBackupManager.backupAllData(a);
			});
			set.addButton(o -> {
				o.title = R.string.restore_backup;
				o.subtitle = R.string.restore_backup_sub;
				o.onClick = () -> SettingsBackupManager.restoreBackup(a);
			});

			addSectionHeader(set, ctx, R.string.about_support);
			set.addButton(o -> {
				o.title = R.string.app_version;
				o.csubtitle = "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
				o.onClick = () -> Utils.openUrl(ctx, "https://github.com/chuoinho/FermataX/releases");
			});
			set.addButton(o -> {
				o.title = R.string.support_kofi_title;
				o.subtitle = R.string.support_kofi_sub;
				o.icon = R.drawable.coffee;
				o.onClick = () -> Utils.openUrl(ctx, "https://ko-fi.com/fermatax");
			});
			set.addButton(o -> {
				o.title = R.string.github_repo_title;
				o.subtitle = R.string.github_repo_sub;
				o.onClick = () -> Utils.openUrl(ctx, "https://github.com/chuoinho/FermataX");
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

	private void restoreAudioEffectsDraft(Bundle state) {
		if ((state == null) || (audioEffectsDraft == null)) return;
		Bundle saved = state.getBundle("audio_effects_draft");
		if (saved == null) return;
		int[] curve = saved.getIntArray("curve");
		if ((curve == null) || (curve.length != AudioEffectsProfile.CANONICAL_FREQ_HZ.length)) return;
		audioEffectsDraft.restore(new AudioEffectsProfile(AudioEffectsProfile.SCHEMA_VERSION,
			saved.getBoolean("enabled"), saved.getBoolean("equalizer_enabled"), curve,
			saved.getInt("preamp"), saved.getBoolean("bass_enabled"), saved.getInt("bass_strength"),
			saved.getBoolean("loudness_enabled"), saved.getInt("loudness_gain"),
			saved.getBoolean("virtualizer_enabled"), saved.getInt("virtualizer_strength"),
			saved.getInt("virtualizer_mode")));
	}

	private static Bundle audioEffectsState(AudioEffectsProfile profile) {
		Bundle state = new Bundle();
		state.putBoolean("enabled", profile.enabled());
		state.putBoolean("equalizer_enabled", profile.equalizerEnabled());
		state.putIntArray("curve", profile.canonicalCurveDb());
		state.putInt("preamp", profile.preampDb());
		state.putBoolean("bass_enabled", profile.bassBoostEnabled());
		state.putInt("bass_strength", profile.bassBoostStrength());
		state.putBoolean("loudness_enabled", profile.loudnessEnabled());
		state.putInt("loudness_gain", profile.loudnessGain());
		state.putBoolean("virtualizer_enabled", profile.virtualizerEnabled());
		state.putInt("virtualizer_strength", profile.virtualizerStrength());
		state.putInt("virtualizer_mode", profile.virtualizerMode());
		return state;
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

	private static void addSectionHeader(PreferenceSet set, Context ctx, @StringRes int titleRes) {
		set.addView(o -> {
			o.view = () -> {
				TextView tv = new TextView(ctx);
				int padStart = (int) UiUtils.toPx(ctx, 14);
				int padTop = (int) UiUtils.toPx(ctx, 18);
				int padBottom = (int) UiUtils.toPx(ctx, 6);
				tv.setPaddingRelative(padStart, padTop, padStart, padBottom);
				tv.setText(titleRes);
				tv.setTextSize(13f);
				tv.setAllCaps(true);
				tv.setTypeface(Typeface.DEFAULT_BOLD);
				tv.setLetterSpacing(0.06f);
				int accent = resolveColor(ctx, android.R.attr.colorAccent, 0xff4f9cff);
				tv.setTextColor(ColorUtils.setAlphaComponent(accent, 220));
				Runnable resetParent = () -> {
					ViewParent p = tv.getParent();
					if (p instanceof me.aap.utils.pref.PreferenceView pv) {
						pv.setBackground(null);
						pv.setPadding(0, 0, 0, 0);
						pv.setFocusable(false);
						pv.setClickable(false);
					}
				};
				tv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
					@Override
					public void onViewAttachedToWindow(View v) {
						resetParent.run();
					}

					@Override
					public void onViewDetachedFromWindow(View v) {
						ViewParent p = v.getParent();
						if (p instanceof me.aap.utils.pref.PreferenceView pv) {
							pv.setBackgroundResource(me.aap.utils.R.drawable.box_secondary);
							int pad = (int) UiUtils.toPx(pv.getContext(), 5);
							pv.setPadding(pad, pad, pad, pad);
							pv.setFocusable(true);
							pv.setClickable(true);
						}
					}
				});
				tv.post(resetParent);
				return tv;
			};
		});
	}

	private static int resolveColor(Context context, int attribute, int fallback) {
		TypedValue value = new TypedValue();
		if (!context.getTheme().resolveAttribute(attribute, value, true)) return fallback;
		return (value.resourceId == 0) ? value.data : ContextCompat.getColor(context, value.resourceId);
	}
}
