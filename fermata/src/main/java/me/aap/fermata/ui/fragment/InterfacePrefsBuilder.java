package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.TypedValue;
import android.widget.CheckBox;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.control.PhoneReadiness;
import me.aap.fermata.ui.glass.speedo.TripModePolicy;
import me.aap.fermata.ui.glass.speedo.TripTrackingService;
import me.aap.fermata.ui.policy.BackgroundRunPolicy;
import me.aap.fermata.ui.policy.BackgroundRunState;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.NavBarView;

final class InterfacePrefsBuilder {
	private InterfacePrefsBuilder() {
	}

	static void add(MainActivityDelegate activity, PreferenceSet set) {
		PreferenceStore store = activity.getPrefs();

		normalizeTheme(store);
		if (BuildConfig.AUTO) normalizeThemeAA(store);
		set.addListPref(o -> {
			o.store = store;
			o.pref = MainActivityPrefs.THEME_MAIN;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_classic, R.string.theme_material_you, R.string.theme_glass};
			o.valuesMap = new int[]{MainActivityPrefs.THEME_CLASSIC, MainActivityPrefs.THEME_MATERIAL_YOU, MainActivityPrefs.THEME_GLASS};
		});
		normalizeGlassAppearance(store);
		if (store.getIntPref(MainActivityPrefs.THEME_MAIN) == MainActivityPrefs.THEME_GLASS
				|| (BuildConfig.AUTO && store.getIntPref(MainActivityPrefs.THEME_AA) == MainActivityPrefs.THEME_GLASS)) {
			set.addListPref(o -> {
				o.store = store;
				o.pref = MainActivityPrefs.GLASS_APPEARANCE;
				o.title = R.string.glass_appearance;
				o.subtitle = R.string.glass_appearance_sub;
				o.formatSubtitle = true;
				o.values = new int[]{R.string.glass_appearance_day, R.string.glass_appearance_night};
				o.valuesMap = new int[]{MainActivityPrefs.GLASS_APPEARANCE_DAY, MainActivityPrefs.GLASS_APPEARANCE_NIGHT};
			});
		}

		Pref<IntSupplier> navBarPosition = BuildConfig.AUTO ?
				MainActivityPrefs.NAV_BAR_POS_AA : MainActivityPrefs.NAV_BAR_POS;
		normalizeNavBarPosition(store, navBarPosition);
		set.addListPref(o -> {
			o.store = store;
			o.pref = navBarPosition;
			o.title = R.string.nav_bar_pos;
			o.subtitle = R.string.nav_bar_pos_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.nav_bar_pos_left, R.string.nav_bar_pos_right};
			o.valuesMap = new int[]{NavBarView.POSITION_LEFT, NavBarView.POSITION_RIGHT};
		});

		normalizeLocale(store);
		set.addListPref(o -> {
			o.store = store;
			o.pref = MainActivityPrefs.LOCALE;
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.removeDefault = false;
			o.cmp = String::compareTo;
			var langs = MainActivityPrefs.Lang.getValues();
			o.stringValues = CollectionUtils.mapToArray(langs,
					v -> v.locale.getDisplayName(), String[]::new);
			o.valuesMap = langs.stream().mapToInt(Enum::ordinal).toArray();
		});

		if (!activity.isCarActivityNotMirror()) {
			set.addButton(o -> {
				o.title = R.string.initial_setup_reopen;
				o.subtitle = R.string.initial_setup_reopen_sub;
				o.onClick = () -> activity.showFragment(R.id.initial_setup_fragment);
			});
		}

		if (activity.isGlassTheme()) {
			set.addButton(o -> {
				o.title = R.string.glass_quick_manage_title;
				o.subtitle = R.string.glass_quick_manage_sub;
				o.onClick = () -> me.aap.fermata.ui.glass.quickbar.GlassQuickBarManager.showManageDialog(
						activity.getContext(), activity, () -> {
							me.aap.utils.ui.fragment.ActivityFragment f = activity.getActiveFragment();
							if (f instanceof DashboardFragment df && df.getView() instanceof me.aap.fermata.ui.glass.dashboard.GlassDashboardLayout gl) {
								if (gl.getQuickBarView() instanceof me.aap.fermata.ui.glass.quickbar.GlassQuickBarView qb) {
									qb.refresh();
								}
							}
						});
			});
		}
	}

	static void manageLocationAccess(MainActivityDelegate activity, Runnable onChanged) {
		manageLocationAccess(activity, onChanged, false);
	}

	static void manageLocationAccessFromInitialSetup(MainActivityDelegate activity, Runnable onChanged) {
		manageLocationAccess(activity, onChanged, true);
	}

	private static void manageLocationAccess(MainActivityDelegate activity, Runnable onChanged,
			boolean checkBackgroundRestrictions) {
		Context ctx = activity.getContext();
		TripModePolicy.Access access = currentLocationAccess(ctx);
		if (access == TripModePolicy.Access.ALWAYS && offersPermissionDowngrade(ctx)) {
			showLocationDowngradeDialog(activity);
		} else if (access == TripModePolicy.Access.WHILE_USING && BuildConfig.TRIP_BACKGROUND_SUPPORTED) {
			showBackgroundPermissionPrompt(activity, onChanged, checkBackgroundRestrictions);
		} else if (access == TripModePolicy.Access.NOT_GRANTED) {
			requestForegroundLocation(activity, onChanged, checkBackgroundRestrictions);
		}
	}

	static void manageBackgroundAccess(MainActivityDelegate activity) {
		Context ctx = activity.getContext();
		BackgroundRunPolicy.Vendor vendor = BackgroundRunPolicy.vendorFor(Build.MANUFACTURER);
		if ((vendor == null) || BackgroundRunState.isVendorGuideDone(ctx)) {
			openAndroidBatterySettings(activity);
			return;
		}
		BackgroundRunPolicy.ManagerTarget target = BackgroundRunPolicy.selectVendorTarget(
				Build.MANUFACTURER, candidate -> !candidate.appDetailsFallback()
						&& canResolve(ctx, vendorIntent(candidate)));
		showVendorBackgroundGuide(activity, vendor, target);
	}

	static int locationAccessState(TripModePolicy.Access access) {
		return switch (access) {
			case NOT_GRANTED -> R.string.location_state_missing;
			case WHILE_USING -> R.string.location_state_while_using;
			case ALWAYS -> R.string.location_state_always;
		};
	}

	static int locationAccessColor(TripModePolicy.Access access) {
		return switch (access) {
			case NOT_GRANTED -> 0xFFEF4444;
			case WHILE_USING -> 0xFFF59E0B;
			case ALWAYS -> 0xFF22C55E;
		};
	}

	/** True while Android still allows location "all the time", which only the user can lower. */
	static boolean offersPermissionDowngrade(Context ctx) {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && TripModePolicy.hasBackgroundLocation(ctx);
	}

	static Intent permissionSettingsIntent(Context ctx) {
		return ReadinessPrefsBuilder.settingsIntent(PhoneReadiness.Action.APP_DETAILS, ctx.getPackageName(),
				Build.VERSION.SDK_INT);
	}

	private static TripModePolicy.Access currentLocationAccess(Context ctx) {
		return TripModePolicy.access(TripModePolicy.hasForegroundLocation(ctx),
				TripModePolicy.hasBackgroundLocation(ctx));
	}

	private static void maybeOfferBackgroundPermissionPrompt(MainActivityDelegate activity, Runnable onChanged,
			boolean checkBackgroundRestrictions) {
		Context ctx = activity.getContext();
		if (!TripModePolicy.shouldOfferBackgroundPrompt(TripModePolicy.hasForegroundLocation(ctx),
				TripModePolicy.hasBackgroundLocation(ctx), wasBackgroundPromptAsked(ctx),
				BuildConfig.TRIP_BACKGROUND_SUPPORTED)) return;
		showBackgroundPermissionPrompt(activity, onChanged, checkBackgroundRestrictions);
	}

	private static boolean wasBackgroundPromptAsked(Context ctx) {
		return ctx.getSharedPreferences("location_access", Context.MODE_PRIVATE)
				.getBoolean("background_prompt_asked", false);
	}

	private static void markBackgroundPromptAsked(Context ctx) {
		ctx.getSharedPreferences("location_access", Context.MODE_PRIVATE).edit()
				.putBoolean("background_prompt_asked", true).apply();
	}

	private static void requestForegroundLocation(MainActivityDelegate activity, Runnable onChanged,
			boolean checkBackgroundRestrictions) {
		AppActivity app = activity.getAppActivity();
		if (app == null) return;
		Context ctx = activity.getContext();
		app.checkPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_COARSE_LOCATION).onCompletion((r, e) -> {
			onChanged.run();
			if (TripModePolicy.hasForegroundLocation(ctx)) {
				maybeOfferBackgroundPermissionPrompt(activity, onChanged, checkBackgroundRestrictions);
			}
		});
	}

	private static void showBackgroundPermissionPrompt(MainActivityDelegate activity, Runnable onChanged,
			boolean checkBackgroundRestrictions) {
		Context ctx = activity.getContext();
		DialogBuilder d = activity.createDialogBuilder(ctx);
		d.setTitle(R.string.location_always_title);
		d.setMessage(R.string.location_background_disclosure);
		d.setPositiveButton(R.string.location_allow_now,
				(dlg, which) -> requestBackgroundLocation(activity, onChanged, checkBackgroundRestrictions));
		d.setNegativeButton(R.string.location_skip, (dlg, which) -> {
			markBackgroundPromptAsked(ctx);
			onChanged.run();
			if (checkBackgroundRestrictions) maybeShowInitialBackgroundPrompt(activity);
		});
		d.show();
	}

	private static void requestBackgroundLocation(MainActivityDelegate activity, Runnable onChanged,
			boolean checkBackgroundRestrictions) {
		Context ctx = activity.getContext();
		markBackgroundPromptAsked(ctx);
		AppActivity app = activity.getAppActivity();
		if (app == null) {
			if (checkBackgroundRestrictions) maybeShowInitialBackgroundPrompt(activity);
			return;
		}
		app.checkPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION).onCompletion((r, e) -> {
			onChanged.run();
			boolean showWarning = checkBackgroundRestrictions && shouldOfferInitialBackgroundPrompt(ctx);
			if (TripModePolicy.hasBackgroundLocation(ctx)) {
				if (AutomotiveConnectionState.get().isProjectionConnected()) TripTrackingService.start(ctx);
				if (!showWarning) UiUtils.showAlert(ctx, ctx.getString(R.string.location_background_enabled));
			} else if (!showWarning) {
				UiUtils.showAlert(ctx, ctx.getString(R.string.location_background_denied));
			}
			if (showWarning) showInitialBackgroundPrompt(activity, ctx);
		});
	}

	private static boolean shouldOfferInitialBackgroundPrompt(Context ctx) {
		return BackgroundRunPolicy.shouldOfferInitialWarning(BackgroundRunState.isUnrestricted(ctx),
				BackgroundRunState.wasInitialWarningAsked(ctx));
	}

	private static void maybeShowInitialBackgroundPrompt(MainActivityDelegate activity) {
		Context ctx = activity.getContext();
		if (shouldOfferInitialBackgroundPrompt(ctx)) showInitialBackgroundPrompt(activity, ctx);
	}

	private static void showInitialBackgroundPrompt(MainActivityDelegate activity, Context ctx) {
		BackgroundRunState.markInitialWarningAsked(ctx);
		DialogBuilder d = activity.createDialogBuilder(ctx);
		d.setTitle(R.string.readiness_background_prompt_title);
		d.setMessage(R.string.readiness_background_prompt_message);
		d.setPositiveButton(R.string.readiness_background_open_settings,
				(dlg, which) -> openAndroidBatterySettings(activity));
		d.setNegativeButton(R.string.readiness_background_skip, null);
		d.show();
	}

	private static void showVendorBackgroundGuide(MainActivityDelegate activity,
			BackgroundRunPolicy.Vendor vendor, BackgroundRunPolicy.ManagerTarget target) {
		Context ctx = activity.getContext();
		String vendorName = vendorDisplayName(vendor);
		CheckBox done = new CheckBox(ctx);
		done.setText(R.string.readiness_background_oem_done);
		if (activity.isGlassTheme()) {
			done.setTextColor(activity.getGlassTextPrimary());
		} else {
			TypedValue textColor = new TypedValue();
			if (ctx.getTheme().resolveAttribute(android.R.attr.textColorPrimary, textColor, true)) {
				done.setTextColor(textColor.resourceId != 0
						? ctx.getColorStateList(textColor.resourceId).getDefaultColor() : textColor.data);
			}
		}
		float density = ctx.getResources().getDisplayMetrics().density;
		done.setPadding(Math.round(20 * density), Math.round(8 * density),
				Math.round(20 * density), Math.round(8 * density));
		done.setOnCheckedChangeListener((button, checked) ->
				BackgroundRunState.setVendorGuideDone(ctx, checked));
		DialogBuilder d = activity.createDialogBuilder(ctx);
		d.setTitle(ctx.getString(R.string.readiness_background_oem_title, vendorName));
		d.setMessage(ctx.getString(R.string.readiness_background_oem_guide, vendorName));
		d.setView(done);
		d.setPositiveButton(ctx.getString(R.string.readiness_background_oem_open_vendor, vendorName),
				(dlg, which) -> openVendorSettings(activity, target));
		d.setNegativeButton(R.string.readiness_background_oem_open_android,
				(dlg, which) -> openAndroidBatterySettings(activity));
		d.setNeutralButton(R.string.readiness_background_oem_later, null);
		d.show();
	}

	private static String vendorDisplayName(BackgroundRunPolicy.Vendor vendor) {
		return switch (vendor) {
			case HUAWEI_HONOR -> "Huawei / Honor";
			case XIAOMI -> "Xiaomi / Redmi / POCO";
			case OPPO_REALME_ONEPLUS -> "OPPO / Realme / OnePlus";
			case VIVO -> "Vivo";
		};
	}

	private static Intent vendorIntent(BackgroundRunPolicy.ManagerTarget target) {
		return new Intent().setComponent(new ComponentName(target.packageName(), target.className()));
	}

	private static void openVendorSettings(MainActivityDelegate activity,
			BackgroundRunPolicy.ManagerTarget target) {
		Context ctx = activity.getContext();
		Intent intent = (target == null || target.appDetailsFallback()) ? appDetailsIntent(ctx) : vendorIntent(target);
		if (!startExternalSettings(activity, intent)) startExternalSettings(activity, appDetailsIntent(ctx));
	}

	private static void openAndroidBatterySettings(MainActivityDelegate activity) {
		Context ctx = activity.getContext();
		Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
		if (!startExternalSettings(activity, intent)) startExternalSettings(activity, appDetailsIntent(ctx));
	}

	private static Intent appDetailsIntent(Context ctx) {
		return new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
				android.net.Uri.parse("package:" + ctx.getPackageName()));
	}

	private static boolean canResolve(Context ctx, Intent intent) {
		try {
			return intent.resolveActivity(ctx.getPackageManager()) != null;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static boolean startExternalSettings(MainActivityDelegate activity, Intent intent) {
		if (!canResolve(activity.getContext(), intent)) return false;
		try {
			activity.startActivity(intent);
			return true;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static void showLocationDowngradeDialog(MainActivityDelegate activity) {
		Context ctx = activity.getContext();
		DialogBuilder d = activity.createDialogBuilder(ctx);
		d.setTitle(R.string.location_always_title);
		d.setMessage(R.string.location_background_lower_message);
		d.setPositiveButton(R.string.location_open_settings, (dlg, which) -> {
			try {
				ctx.startActivity(permissionSettingsIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			} catch (RuntimeException e) {
				Log.e(e, "Unable to open app permission settings");
			}
		});
		d.setNegativeButton(R.string.location_keep, null);
		d.show();
	}

	private static void normalizeTheme(PreferenceStore store) {
		int theme = store.getIntPref(MainActivityPrefs.THEME_MAIN);
		if (theme != MainActivityPrefs.THEME_CLASSIC &&
				theme != MainActivityPrefs.THEME_MATERIAL_YOU &&
				theme != MainActivityPrefs.THEME_GLASS) {
			store.applyIntPref(MainActivityPrefs.THEME_MAIN, MainActivityPrefs.THEME_CLASSIC);
		}
	}

	private static void normalizeThemeAA(PreferenceStore store) {
		int theme = store.getIntPref(MainActivityPrefs.THEME_AA);
		if (theme != MainActivityPrefs.THEME_CLASSIC &&
				theme != MainActivityPrefs.THEME_MATERIAL_YOU &&
				theme != MainActivityPrefs.THEME_GLASS) {
			store.applyIntPref(MainActivityPrefs.THEME_AA, MainActivityPrefs.THEME_CLASSIC);
		}
	}

	private static void normalizeGlassAppearance(PreferenceStore store) {
		int app = store.getIntPref(MainActivityPrefs.GLASS_APPEARANCE);
		if (app != MainActivityPrefs.GLASS_APPEARANCE_DAY && app != MainActivityPrefs.GLASS_APPEARANCE_NIGHT) {
			store.applyIntPref(MainActivityPrefs.GLASS_APPEARANCE, MainActivityPrefs.GLASS_APPEARANCE_NIGHT);
		}
	}

	private static void normalizeNavBarPosition(PreferenceStore store, Pref<IntSupplier> pref) {
		int position = store.getIntPref(pref);
		int normalized = MainActivityPrefs.normalizeNavBarPosition(position);
		if (normalized != position) store.applyIntPref(pref, normalized);
	}

	private static void normalizeLocale(PreferenceStore store) {
		int pref = store.getIntPref(MainActivityPrefs.LOCALE);
		MainActivityPrefs.Lang lang = MainActivityPrefs.Lang.get(pref);
		if (lang.ordinal() != pref) store.applyIntPref(MainActivityPrefs.LOCALE, lang.ordinal());
	}
}
