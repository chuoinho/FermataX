package me.aap.fermata.ui.fragment;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.graphics.Typeface;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.carapps.CarAppsFeature;
import me.aap.fermata.ui.carapps.PhoneAppsStatus;
import me.aap.fermata.ui.control.PhoneReadiness;
import me.aap.fermata.ui.control.PhoneReadiness.Action;
import me.aap.fermata.ui.control.PhoneReadiness.Check;
import me.aap.fermata.ui.control.PhoneReadiness.Input;
import me.aap.fermata.ui.control.PhoneReadiness.Row;
import me.aap.fermata.ui.control.PhoneReadiness.Status;
import me.aap.fermata.ui.glass.speedo.TripModePolicy;
import me.aap.fermata.ui.policy.BackgroundRunState;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.ui.UiUtils;

/** Phone-settings collector for connection and system-permission readiness, grouped by what each check serves. */
final class ReadinessPrefsBuilder {
	static final Check[] REQUIRED_CHECKS = {Check.STORAGE, Check.LOCATION};
	// Playback and Android Auto work without the notification permission.
	static final Check[] OPTIONAL_CHECKS = {Check.NOTIFICATIONS, Check.MICROPHONE,
			Check.NAVIGATION, Check.ACCESSIBILITY, Check.OVERLAY};
	private static final int GREEN = 0xFF22C55E;
	private static final int RED = 0xFFEF4444;
	private static final int AMBER = 0xFFF59E0B;
	private static final int GREY = 0xFF94A3B8;

	enum Group {PLAYBACK, ANDROID_AUTO, PHONE_APPS, STABILITY}

	enum Kind {OVERALL, HEADER, CONNECTION, SUMMARY, DETAIL, PHONE_APPS}

	enum Summary {OK, OPTIONAL_OFF, ATTENTION}

	record Item(Kind kind, @Nullable Group group, @Nullable Row row, @Nullable Summary summary) {
	}

	private final MainActivityDelegate activity;
	private final Context context;
	private final BooleanSupplier uiActive;
	private final Runnable openPhoneApps;
	private final AutomotiveConnectionState connection = AutomotiveConnectionState.get();
	private final FermataServiceUiBinder binder;
	private final AutomotiveConnectionState.Listener connectionListener = state -> scheduleRefresh();
	private final FermataServiceUiBinder.Listener binderListener = (oldItem, newItem) -> scheduleRefresh();
	private final Runnable rootListener = this::scheduleRefresh;
	private Snapshot snapshot;
	@Nullable
	private PreferenceSet set;
	private boolean closed;

	ReadinessPrefsBuilder(MainActivityDelegate activity, BooleanSupplier uiActive, Runnable openPhoneApps) {
		this.activity = activity;
		this.context = activity.getContext().getApplicationContext();
		this.uiActive = uiActive;
		this.openPhoneApps = openPhoneApps;
		this.binder = activity.getMediaServiceBinder();
		this.snapshot = collect();
		connection.addListener(connectionListener);
		binder.addBroadcastListener(binderListener);
		CarAppsFeature.addListener(rootListener);
	}

	void addTo(PreferenceSet parent) {
		if (closed || (set != null)) return;
		set = parent.subSet(o -> {
			o.title = R.string.readiness_title;
			o.subtitle = R.string.readiness_subtitle;
		});
		refresh();
	}

	void refresh() {
		if (!isRefreshAllowed(uiActive, closed)) return;
		snapshot = collect();
		PreferenceSet current = set;
		if (current != null) current.configure(this::configure);
	}

	void close() {
		if (closed) return;
		closed = true;
		connection.removeListener(connectionListener);
		binder.removeBroadcastListener(binderListener);
		CarAppsFeature.removeListener(rootListener);
		set = null;
	}

	private void scheduleRefresh() {
		activity.getHandler().post(this::refresh);
	}

	private void configure(PreferenceSet target) {
		target.addButton(o -> {
			o.title = R.string.readiness_refresh;
			o.subtitle = R.string.readiness_refresh_subtitle;
			o.hideChevron = true;
			o.onClick = this::refresh;
		});

		Snapshot current = snapshot;
		PhoneAppsStatus phoneApps = shouldAddPhoneAppsRow(BuildConfig.AUTO, CarAppsFeature.isSupported())
				? PhoneAppsStatus.current() : null;
		for (Item item : plan(current.rows(), phoneApps)) {
			switch (item.kind()) {
				case OVERALL -> addStatusLine(target, (item.summary() == Summary.OK) ?
						R.string.readiness_all_ready : R.string.readiness_some_issues, item.summary());
				case HEADER -> addSectionHeader(target, groupTitle(item.group()));
				case CONNECTION -> addConnectionRow(target, item.row(), current.input());
				case SUMMARY -> addStatusLine(target, switch (item.summary()) {
					case OK -> R.string.readiness_group_ok;
					case OPTIONAL_OFF -> R.string.readiness_group_optional_off;
					case ATTENTION -> R.string.readiness_needs_attention;
				}, item.summary());
				case DETAIL -> addReadinessRow(target, item.row(), current.input());
				case PHONE_APPS -> addPhoneAppsRow(target, phoneApps);
			}
		}
	}

	static Group group(Check check) {
		return switch (check) {
			case STORAGE, NOTIFICATIONS, MICROPHONE -> Group.PLAYBACK;
			case BATTERY -> Group.STABILITY;
			case CONNECTION, CONTROL_SESSION, LOCATION, NAVIGATION, ACCESSIBILITY, OVERLAY, SCREEN_CAPTURE ->
					Group.ANDROID_AUTO;
		};
	}

	/** A row worth showing: not fine and not the connection, which is a status, not something to fix. */
	static boolean isDetail(Row row) {
		return (row.check() != Check.CONNECTION) && (row.status() != Status.OK)
				&& (row.status() != Status.NOT_APPLICABLE);
	}

	/** Optional items never break "Everything is ready"; nor does location allowed only while in use. */
	static boolean needsAttention(Row row) {
		return isDetail(row) && !contains(OPTIONAL_CHECKS, row.check())
				&& !((row.check() == Check.LOCATION) && (row.status() == Status.INFO));
	}

	/** Overall line, then per group: header, connection status, one summary line, and only the rows not fine. */
	static List<Item> plan(List<Row> rows, @Nullable PhoneAppsStatus phoneApps) {
		List<Item> out = new ArrayList<>();
		boolean attention = (phoneApps != null) && phoneApps.needsAction;
		for (Group g : Group.values()) {
			if (g == Group.PHONE_APPS) {
				if (phoneApps == null) continue;
				out.add(new Item(Kind.HEADER, g, null, null));
				out.add(new Item(Kind.PHONE_APPS, g, null, phoneApps.needsAction ? Summary.ATTENTION : Summary.OK));
				continue;
			}
			List<Row> groupRows = new ArrayList<>();
			for (Row r : rows) if (group(r.check()) == g) groupRows.add(r);
			if (groupRows.isEmpty()) continue;
			out.add(new Item(Kind.HEADER, g, null, null));
			for (Row r : groupRows) if (r.check() == Check.CONNECTION) out.add(new Item(Kind.CONNECTION, g, r, null));
			Summary s = groupRows.stream().anyMatch(ReadinessPrefsBuilder::needsAttention) ? Summary.ATTENTION :
					groupRows.stream().anyMatch(ReadinessPrefsBuilder::isDetail) ? Summary.OPTIONAL_OFF : Summary.OK;
			attention |= (s == Summary.ATTENTION);
			out.add(new Item(Kind.SUMMARY, g, null, s));
			for (Row r : groupRows) if (isDetail(r)) out.add(new Item(Kind.DETAIL, g, r, null));
		}
		out.add(0, new Item(Kind.OVERALL, null, null, attention ? Summary.ATTENTION : Summary.OK));
		return out;
	}

	static boolean shouldAddPhoneAppsRow(boolean autoBuild, boolean supported) {
		return autoBuild && supported;
	}

	private int groupTitle(Group g) {
		return switch (g) {
			case PLAYBACK -> R.string.readiness_group_playback;
			// Mobile has no Android Auto: there, location and navigation serve the phone's own driving screens
			case ANDROID_AUTO -> BuildConfig.AUTO ? R.string.readiness_group_android_auto :
					R.string.readiness_group_location;
			case PHONE_APPS -> R.string.car_apps_title;
			case STABILITY -> R.string.readiness_group_stability;
		};
	}

	private void addStatusLine(PreferenceSet target, int text, Summary summary) {
		boolean ok = summary != Summary.ATTENTION;
		target.addButton(o -> {
			o.ctitle = statusTitle(activity.getString(text), ok ? GREEN : AMBER, ok ? "✓ " : "● ");
			o.hideChevron = true;
		});
	}

	/** One row: the status, and a tap opens the Phone Apps page in Addons, where setup, the guide and the switch are. */
	private void addPhoneAppsRow(PreferenceSet target, PhoneAppsStatus status) {
		int color = status.needsAction ? RED :
				((status == PhoneAppsStatus.OFF) || (status == PhoneAppsStatus.NOT_SUPPORTED)) ? GREY : GREEN;
		target.addButton(o -> {
			o.ctitle = statusTitle(activity.getString(status.textRes), color);
			o.subtitle = R.string.car_apps_readiness_open;
			o.onClick = () -> {
				if (isRefreshAllowed(uiActive, closed)) openPhoneApps.run();
			};
		});
	}

	private void addConnectionRow(PreferenceSet target, Row row, Input input) {
		String text = activity.getString(R.string.readiness_android_auto_status,
				activity.getString(R.string.readiness_android_auto), connectionSubtitle(input),
				activity.getString(input.controlAvailable() ? R.string.readiness_control_available :
						R.string.readiness_control_unavailable));
		target.addButton(o -> {
			o.ctitle = statusTitle(text, statusColor(row.status()));
			o.hideChevron = true;
		});
	}

	private static boolean contains(Check[] checks, Check check) {
		for (Check candidate : checks) if (candidate == check) return true;
		return false;
	}

	private void addSectionHeader(PreferenceSet target, int title) {
		target.addView(o -> o.view = () -> {
			Context themeContext = activity.getContext();
			TextView label = new TextView(themeContext);
			label.setText(title);
			label.setTextAppearance(android.R.style.TextAppearance_Small);
			label.setTypeface(Typeface.DEFAULT_BOLD);
			label.setAllCaps(true);
			label.setPaddingRelative(dp(14), dp(18), dp(14), dp(6));
			if (activity.isGlassTheme()) {
				label.setTextColor(activity.getGlassTextPrimary());
			} else {
				android.util.TypedValue value = new android.util.TypedValue();
				if (themeContext.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true)) {
					if (value.resourceId != 0) label.setTextColor(themeContext.getColorStateList(value.resourceId));
					else label.setTextColor(value.data);
				}
			}
			return label;
		});
	}

	private int dp(int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}

	private void addReadinessRow(PreferenceSet target, Row row, Input input) {
		int color = row.check() == Check.LOCATION ? locationColor() : statusColor(row.status());
		target.addButton(o -> {
			o.ctitle = statusTitle(activity.getString(title(row.check())), color);
			o.csubtitle = subtitle(row, input);
			o.hideChevron = (row.status() == Status.OK) || (row.action() == Action.NONE);
			o.onClick = (o.hideChevron || row.action() == Action.NONE) ? null :
					() -> performAction(row.action());
		});
	}

	private int locationColor() {
		TripModePolicy.Access access = locationAccess();
		return InterfacePrefsBuilder.locationAccessColor(access);
	}

	private TripModePolicy.Access locationAccess() {
		return TripModePolicy.access(TripModePolicy.hasForegroundLocation(context),
				TripModePolicy.hasBackgroundLocation(context));
	}

	private static int statusColor(Status status) {
		return switch (status) {
			case OK -> 0xFF22C55E;
			case ACTION_AVAILABLE -> 0xFFEF4444;
			case INFO -> 0xFFF59E0B;
			case UNKNOWN, NOT_APPLICABLE -> 0xFF94A3B8;
		};
	}

	private static SpannableString statusTitle(CharSequence text, int color) {
		return statusTitle(text, color, "● ");
	}

	private static SpannableString statusTitle(CharSequence text, int color, String prefix) {
		SpannableString title = new SpannableString(prefix + text);
		title.setSpan(new ForegroundColorSpan(color), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return title;
	}

	private Snapshot collect() {
		Boolean storage = queryStorage();
		Boolean location = queryLocation();
		Boolean accessibility = queryAccessibility();
		Boolean microphone = queryMicrophone();
		Boolean notifications = queryNotifications();
		Boolean overlay = queryOverlay();
		boolean backgroundUnrestricted = BackgroundRunState.isUnrestricted(context);
		boolean navigation = PhoneReadiness.hasNotificationListenerCheck(context);
		Input input = new Input(BuildConfig.AUTO, connection.hasConnectionObservation(),
				connection.state(), binder.isControlAvailable(), Boolean.TRUE.equals(storage),
				Boolean.TRUE.equals(location), Boolean.TRUE.equals(accessibility),
				Boolean.TRUE.equals(microphone), Boolean.TRUE.equals(notifications),
				Boolean.TRUE.equals(overlay), backgroundUnrestricted, navigation);
		List<Row> rows = new ArrayList<>(PhoneReadiness.evaluate(input));
		if (storage == null) markUnknown(rows, Check.STORAGE);
		if (location == null) markUnknown(rows, Check.LOCATION);
		if (accessibility == null) markUnknown(rows, Check.ACCESSIBILITY);
		if (microphone == null) markUnknown(rows, Check.MICROPHONE);
		if (notifications == null) markUnknown(rows, Check.NOTIFICATIONS);
		if (overlay == null) markUnknown(rows, Check.OVERLAY);
		if (locationAccess() == TripModePolicy.Access.WHILE_USING) {
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).check() == Check.LOCATION) {
					rows.set(i, new Row(Check.LOCATION, Status.INFO, Action.LOCATION_SETTINGS));
					break;
				}
			}
		}
		return new Snapshot(input, List.copyOf(rows));
	}

	@Nullable
	private Boolean queryStorage() {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				return Environment.isExternalStorageManager();
			} else {
				return ContextCompat.checkSelfPermission(context,
						Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
			}
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private Boolean queryLocation() {
		try {
			return ContextCompat.checkSelfPermission(context,
					Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
					|| ContextCompat.checkSelfPermission(context,
					Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private Boolean queryAccessibility() {
		try {
			ComponentName target = new ComponentName(context.getPackageName(),
					"me.app.fermatax.auto.AccessibilityEventDispatcherService");
			String enabledServices = Settings.Secure.getString(context.getContentResolver(),
					Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
			if (enabledServices == null) return false;
			for (String c : enabledServices.split(":")) {
				ComponentName n = ComponentName.unflattenFromString(c);
				if (target.equals(n)) return true;
			}
			return false;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private Boolean queryMicrophone() {
		try {
			return ContextCompat.checkSelfPermission(context,
					Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private Boolean queryNotifications() {
		try {
			return NotificationManagerCompat.from(context).areNotificationsEnabled();
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private Boolean queryOverlay() {
		try {
			return Settings.canDrawOverlays(context);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static void markUnknown(List<Row> rows, Check check) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).check() == check) {
				rows.set(i, new Row(check, Status.UNKNOWN, Action.NONE));
				return;
			}
		}
	}

	private int title(Check check) {
		return switch (check) {
			case CONNECTION -> R.string.readiness_android_auto;
			case CONTROL_SESSION -> R.string.readiness_control_session;
			case STORAGE -> R.string.readiness_storage;
			case LOCATION -> R.string.readiness_location;
			case ACCESSIBILITY -> R.string.readiness_accessibility;
			case MICROPHONE -> R.string.readiness_microphone;
			case NOTIFICATIONS -> R.string.readiness_notifications;
			case OVERLAY -> R.string.readiness_overlay;
			case SCREEN_CAPTURE -> R.string.readiness_screen_capture;
			case BATTERY -> R.string.readiness_battery;
			case NAVIGATION -> R.string.readiness_nav_notification_access;
		};
	}

	private String subtitle(Row row, Input input) {
		if (row.status() == Status.UNKNOWN) return activity.getString(R.string.readiness_unknown);
		return switch (row.check()) {
			case CONNECTION -> connectionSubtitle(input);
			case CONTROL_SESSION -> activity.getString(input.controlAvailable() ?
					R.string.readiness_control_available : R.string.readiness_control_unavailable);
			case STORAGE -> activity.getString(input.storageGranted() ?
					R.string.readiness_storage_granted : R.string.readiness_storage_not_granted);
			case LOCATION -> activity.getString(InterfacePrefsBuilder.locationAccessState(locationAccess()));
			case ACCESSIBILITY -> activity.getString(row.status() == Status.NOT_APPLICABLE ?
					R.string.readiness_not_applicable : input.accessibilityGranted() ?
					R.string.readiness_accessibility_granted : R.string.readiness_accessibility_not_granted);
			case MICROPHONE -> activity.getString(input.microphoneGranted() ?
					R.string.readiness_microphone_granted : R.string.readiness_microphone_not_granted);
			case NOTIFICATIONS -> activity.getString(input.notificationsEnabled() ?
					R.string.readiness_notifications_enabled : R.string.readiness_notifications_disabled);
			case OVERLAY -> activity.getString(row.status() == Status.NOT_APPLICABLE ?
					R.string.readiness_not_applicable : input.overlayGranted() ?
					R.string.readiness_overlay_granted : R.string.readiness_overlay_not_granted);
			case SCREEN_CAPTURE -> activity.getString(row.status() == Status.NOT_APPLICABLE ?
					R.string.readiness_not_applicable : R.string.readiness_screen_capture_info);
			case BATTERY -> activity.getString(input.backgroundUnrestricted() ?
					R.string.readiness_battery_exempt : R.string.readiness_battery_managed);
			case NAVIGATION -> activity.getString(input.navigationNotificationsGranted() ?
					R.string.readiness_nav_notification_access_granted :
					R.string.readiness_nav_notification_access_not_granted);
		};
	}

	private String connectionSubtitle(Input input) {
		if (!input.autoBuild() || !input.connectionObserved() || (input.connectionState() == null)) {
			return activity.getString(R.string.readiness_connection_unknown);
		}
		return switch (input.connectionState()) {
			case DISCONNECTED -> activity.getString(R.string.readiness_connection_disconnected);
			case CONNECTED -> activity.getString(R.string.readiness_connection_connected);
			case APP_VISIBLE -> activity.getString(R.string.readiness_connection_visible);
		};
	}

	private void performAction(Action action) {
		if (!isRefreshAllowed(uiActive, closed)) return;
		if (action == Action.LOCATION_SETTINGS) {
			InterfacePrefsBuilder.manageLocationAccess(activity, this::scheduleRefresh);
			return;
		} else if (action == Action.BATTERY_SETTINGS) {
			InterfacePrefsBuilder.manageBackgroundAccess(activity);
			return;
		} else if (action == Action.NAVIGATION_SETTINGS) {
			startSettings(PhoneReadiness.createNotificationListenerSettingsIntent());
			return;
		} else if (action == Action.MICROPHONE_SETTINGS) {
			if (activity.getAppActivity() != null) {
				activity.getAppActivity().checkPermissions(
						Manifest.permission.RECORD_AUDIO
				).onCompletion((res, err) -> scheduleRefresh());
				return;
			}
		} else if (action == Action.STORAGE_SETTINGS) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && activity.getAppActivity() != null) {
				activity.getAppActivity().checkPermissions(
						Manifest.permission.READ_EXTERNAL_STORAGE
				).onCompletion((res, err) -> scheduleRefresh());
				return;
			}
		}
		openSettings(action);
	}

	private void openSettings(Action action) {
		if (!isRefreshAllowed(uiActive, closed)) return;
		Intent primary = settingsIntent(action, context.getPackageName(), Build.VERSION.SDK_INT);
		if (startSettings(primary)) return;
		if (action == Action.STORAGE_SETTINGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if (startSettings(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))) return;
		}
		if ((action != Action.APP_DETAILS) && startSettings(
				settingsIntent(Action.APP_DETAILS, context.getPackageName(), Build.VERSION.SDK_INT))) return;
		if (isRefreshAllowed(uiActive, closed)) {
			UiUtils.showInfo(context, R.string.readiness_settings_unavailable);
		}
	}

	private boolean startSettings(@Nullable Intent intent) {
		if ((intent == null) || (intent.resolveActivity(context.getPackageManager()) == null)) return false;
		try {
			activity.startActivity(intent);
			return true;
		} catch (ActivityNotFoundException | SecurityException ignored) {
			return false;
		}
	}

	static boolean shouldAdd(RuntimeHostMode hostMode) {
		return hostMode == RuntimeHostMode.PHONE;
	}

	static boolean isRefreshAllowed(BooleanSupplier uiActive, boolean closed) {
		if (closed || (uiActive == null)) return false;
		try {
			return uiActive.getAsBoolean();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	@Nullable
	static Intent settingsIntent(Action action, String packageName, int sdk) {
		Uri packageUri = Uri.parse("package:" + packageName);
		return switch (action) {
			case NONE -> null;
			case APP_DETAILS, LOCATION_SETTINGS, MICROPHONE_SETTINGS ->
					new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case STORAGE_SETTINGS -> (sdk >= Build.VERSION_CODES.R) ?
					new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri) :
					new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case NOTIFICATION_SETTINGS -> (sdk >= Build.VERSION_CODES.O) ?
					new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
							.putExtra(Settings.EXTRA_APP_PACKAGE, packageName) :
					new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case OVERLAY_SETTINGS -> (sdk >= Build.VERSION_CODES.M) ?
					new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri) :
					new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case BATTERY_SETTINGS -> new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
			case ACCESSIBILITY_SETTINGS -> new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
			case NAVIGATION_SETTINGS -> new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
		};
	}

	private record Snapshot(Input input, List<Row> rows) {
	}
}
