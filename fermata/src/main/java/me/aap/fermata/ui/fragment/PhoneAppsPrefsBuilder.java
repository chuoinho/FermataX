package me.aap.fermata.ui.fragment;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.carapps.CarAppsChecks;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.CarAppsReadinessModel;
import me.aap.fermata.ui.carapps.PhoneAppsGuide;
import me.aap.fermata.ui.carapps.PhoneAppsStatus;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.utils.function.Consumer;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceView;

/**
 * Settings > Addons > Phone Apps: the switch the car's Addons catalog also flips, the live status, setup,
 * the usage guide, the report and the technical checks. Phone settings of the Auto flavor only.
 */
final class PhoneAppsPrefsBuilder implements Consumer<PreferenceView.Opts> {
	private final MainActivityDelegate activity;
	private final PreferenceStore store;
	private final BooleanSupplier uiActive;
	private final Runnable featureListener = this::scheduleRefresh;
	// Held here: the store keeps listeners weakly. The switch writes the pref itself; this applies it.
	private final PreferenceStore.Listener storeListener = (s, prefs) -> {
		if (prefs.contains(CarAppsFeature.PREF_ENABLED)) CarAppsFeature.syncEnabled(s);
	};
	private PreferenceSet set;
	private boolean closed;

	PhoneAppsPrefsBuilder(MainActivityDelegate activity, PreferenceStore store, BooleanSupplier uiActive) {
		this.activity = activity;
		this.store = store;
		this.uiActive = uiActive;
		store.addBroadcastListener(storeListener);
		CarAppsFeature.addListener(featureListener);
	}

	static boolean shouldAdd(boolean autoBuild, boolean supported, RuntimeHostMode host) {
		return autoBuild && supported && (host == RuntimeHostMode.PHONE);
	}

	static boolean shouldAddReport(boolean autoBuild, boolean supported, boolean hasBackend, RuntimeHostMode host) {
		return shouldAdd(autoBuild, supported, host) && hasBackend;
	}

	/** "Set up" while something is missing, "Fix" when the paired channel failed, nothing when it works or is off. */
	static int primaryAction(PhoneAppsStatus s) {
		if (!s.needsAction) return 0;
		return (s == PhoneAppsStatus.FAILED) ? R.string.car_apps_settings_fix : R.string.car_apps_settings_setup;
	}

	/** Fix pairs again (a revoked authorization is only cured that way); the old pairing stays until replaced. */
	static PhoneAppsGuide.Page setupPage(PhoneAppsStatus s) {
		return (s == PhoneAppsStatus.FAILED) ? PhoneAppsGuide.Page.SETUP_AGAIN : PhoneAppsGuide.Page.SETUP;
	}

	/** "Not applicable" rows tell the user nothing (the report still has them); none until the server can run. */
	static List<CarAppsChecks.Check> detailChecks(boolean canRun, List<CarAppsChecks.Check> checks) {
		List<CarAppsChecks.Check> out = new ArrayList<>();
		if (!canRun) return out;
		for (CarAppsChecks.Check c : checks) if (c.status != CarAppsChecks.Status.NA) out.add(c);
		return out;
	}

	static int detailsEmptyText(boolean enabled) {
		return enabled ? R.string.car_apps_settings_details_empty : R.string.car_apps_turned_off;
	}

	@Override
	public void accept(PreferenceView.Opts o) {
		o.title = R.string.car_apps_title;
		o.icon = R.drawable.ic_car_apps;
		o.csubtitle = activity.getString(PhoneAppsStatus.current().textRes);
	}

	void configure(PreferenceSet target) {
		if (closed) return;
		set = target;
		PhoneAppsStatus status = PhoneAppsStatus.current();

		target.addBooleanPref(o -> {
			o.store = store;
			o.pref = CarAppsFeature.PREF_ENABLED;
			o.title = R.string.car_apps_settings_enable;
			o.csubtitle = statusLine(status);
		});

		int action = primaryAction(status);
		if (action != 0) {
			target.addButton(o -> {
				o.title = action;
				o.subtitle = (status == PhoneAppsStatus.FAILED) ? R.string.car_apps_settings_fix_sub :
						R.string.car_apps_settings_setup_sub;
				o.onClick = () -> open(setupPage(status));
			});
		}

		target.addButton(o -> {
			o.title = R.string.car_apps_settings_how_to;
			o.subtitle = R.string.car_apps_settings_how_to_sub;
			o.onClick = () -> open(PhoneAppsGuide.Page.USAGE);
		});

		if (CarAppsFeature.backend() != null) {
			target.addButton(o -> {
				o.title = R.string.car_apps_share_report;
				o.subtitle = R.string.car_apps_share_report_sub;
				o.onClick = () -> {
					if (isActive()) SettingsDiagnosticsManager.sharePhoneAppsReport(activity, uiActive);
				};
			});
		}

		PreferenceSet details = target.subSet(o -> {
			o.title = R.string.car_apps_settings_details;
			o.subtitle = R.string.car_apps_settings_details_sub;
		});
		details.configure(this::addDetails);
	}

	private void addDetails(PreferenceSet target) {
		CarAppsReadinessModel model = CarAppsReadinessModel.from(CarAppsFeature.state(), CarAppsFeature.capabilities());
		List<CarAppsChecks.Check> checks = detailChecks(CarAppsFeature.canRun(), model.checks);
		if (checks.isEmpty()) {
			target.addButton(o -> {
				o.title = detailsEmptyText(CarAppsFeature.isEnabled());
				o.hideChevron = true;
			});
			return;
		}
		for (CarAppsChecks.Check check : checks) {
			int color = switch (check.status) {
				case FAIL -> 0xFFEF4444;
				case WARN -> 0xFFF59E0B;
				case OK -> 0xFF22C55E;
				case NA -> 0xFF9CA3AF;
			};
			target.addButton(o -> {
				o.ctitle = dot(activity.getString(check.summaryRes), color);
				o.csubtitle = (check.fixRes != 0) ? activity.getString(check.fixRes) : null; // "" keeps an empty line
				o.hideChevron = true;
			});
		}
	}

	private CharSequence statusLine(PhoneAppsStatus status) {
		int color = status.needsAction ? 0xFFEF4444 :
				((status == PhoneAppsStatus.OFF) || (status == PhoneAppsStatus.NOT_SUPPORTED)) ? 0xFF94A3B8 : 0xFF22C55E;
		return dot(activity.getString(status.textRes), color);
	}

	private static SpannableString dot(CharSequence text, int color) {
		SpannableString s = new SpannableString("● " + text);
		s.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return s;
	}

	private void open(PhoneAppsGuide.Page page) {
		if (isActive()) PhoneAppsGuide.open(activity.getContext(), page);
	}

	private boolean isActive() {
		return ReadinessPrefsBuilder.isRefreshAllowed(uiActive, closed);
	}

	private void scheduleRefresh() {
		activity.getHandler().post(() -> {
			PreferenceSet s = set;
			if ((s != null) && isActive()) s.configure(this::configure);
		});
	}

	void close() {
		if (closed) return;
		closed = true;
		set = null;
		store.removeBroadcastListener(storeListener);
		CarAppsFeature.removeListener(featureListener);
	}
}
