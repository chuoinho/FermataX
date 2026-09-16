package me.aap.fermata.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.auto.AutomotiveConnectionState;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.control.PhoneReadiness;
import me.aap.fermata.ui.control.PhoneReadiness.Action;
import me.aap.fermata.ui.control.PhoneReadiness.Check;
import me.aap.fermata.ui.control.PhoneReadiness.Input;
import me.aap.fermata.ui.control.PhoneReadiness.Row;
import me.aap.fermata.ui.control.PhoneReadiness.Status;
import me.aap.fermata.ui.policy.RuntimeHostMode;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.ui.UiUtils;

/** Phone-settings collector for read-only connection and system-permission readiness. */
final class ReadinessPrefsBuilder {
	private final MainActivityDelegate activity;
	private final Context context;
	private final BooleanSupplier uiActive;
	private final AutomotiveConnectionState connection = AutomotiveConnectionState.get();
	private final FermataServiceUiBinder binder;
	private final AutomotiveConnectionState.Listener connectionListener = state -> scheduleRefresh();
	private final FermataServiceUiBinder.Listener binderListener = (oldItem, newItem) -> scheduleRefresh();
	private Snapshot snapshot;
	@Nullable
	private PreferenceSet set;
	private boolean closed;

	ReadinessPrefsBuilder(MainActivityDelegate activity, BooleanSupplier uiActive) {
		this.activity = activity;
		this.context = activity.getContext().getApplicationContext();
		this.uiActive = uiActive;
		this.binder = activity.getMediaServiceBinder();
		this.snapshot = collect();
		connection.addListener(connectionListener);
		binder.addBroadcastListener(binderListener);
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
		set = null;
	}

	private void scheduleRefresh() {
		activity.getHandler().post(this::refresh);
	}

	private void configure(PreferenceSet target) {
		target.addButton(o -> {
			o.title = R.string.readiness_refresh;
			o.subtitle = R.string.readiness_refresh_subtitle;
			o.onClick = this::refresh;
		});

		Snapshot current = snapshot;
		for (Row row : current.rows()) {
			target.addButton(o -> {
				o.title = title(row.check());
				o.csubtitle = subtitle(row, current.input());
				o.onClick = (row.action() == Action.NONE) ? () -> {
				} : () -> openSettings(row.action());
			});
		}
	}

	private Snapshot collect() {
		Boolean notifications = queryNotifications();
		Boolean overlay = queryOverlay();
		Boolean battery = queryBatteryExemption();
		Input input = new Input(BuildConfig.AUTO, connection.hasConnectionObservation(),
				connection.state(), binder.isControlAvailable(), Boolean.TRUE.equals(notifications),
				Boolean.TRUE.equals(overlay), Boolean.TRUE.equals(battery));
		List<Row> rows = new ArrayList<>(PhoneReadiness.evaluate(input));
		if (notifications == null) markUnknown(rows, Check.NOTIFICATIONS);
		if (overlay == null) markUnknown(rows, Check.OVERLAY);
		if (battery == null) markUnknown(rows, Check.BATTERY);
		return new Snapshot(input, List.copyOf(rows));
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
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
		try {
			return Settings.canDrawOverlays(context);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	@Nullable
	private Boolean queryBatteryExemption() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
		try {
			PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			return (power == null) ? null : power.isIgnoringBatteryOptimizations(context.getPackageName());
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
			case CONNECTION -> R.string.readiness_connection;
			case CONTROL_SESSION -> R.string.readiness_control_session;
			case NOTIFICATIONS -> R.string.readiness_notifications;
			case OVERLAY -> R.string.readiness_overlay;
			case SCREEN_CAPTURE -> R.string.readiness_screen_capture;
			case BATTERY -> R.string.readiness_battery;
		};
	}

	private String subtitle(Row row, Input input) {
		if (row.status() == Status.UNKNOWN) return activity.getString(R.string.readiness_unknown);
		return switch (row.check()) {
			case CONNECTION -> connectionSubtitle(input);
			case CONTROL_SESSION -> activity.getString(input.controlAvailable() ?
					R.string.readiness_control_available : R.string.readiness_control_unavailable);
			case NOTIFICATIONS -> activity.getString(input.notificationsEnabled() ?
					R.string.readiness_notifications_enabled : R.string.readiness_notifications_disabled);
			case OVERLAY -> activity.getString(row.status() == Status.NOT_APPLICABLE ?
					R.string.readiness_not_applicable : input.overlayGranted() ?
					R.string.readiness_overlay_granted : R.string.readiness_overlay_not_granted);
			case SCREEN_CAPTURE -> activity.getString(row.status() == Status.NOT_APPLICABLE ?
					R.string.readiness_not_applicable : R.string.readiness_screen_capture_info);
			case BATTERY -> activity.getString(input.batteryExempt() ?
					R.string.readiness_battery_exempt : R.string.readiness_battery_managed);
		};
	}

	private String connectionSubtitle(Input input) {
		if (!input.autoBuild()) return activity.getString(R.string.readiness_not_applicable);
		return switch (input.connectionState()) {
			case DISCONNECTED -> activity.getString(R.string.readiness_connection_disconnected);
			case CONNECTED -> activity.getString(R.string.readiness_connection_connected);
			case APP_VISIBLE -> activity.getString(R.string.readiness_connection_visible);
		};
	}

	private void openSettings(Action action) {
		if (!isRefreshAllowed(uiActive, closed)) return;
		Intent primary = settingsIntent(action, context.getPackageName(), Build.VERSION.SDK_INT);
		if (startSettings(primary)) return;
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
			case APP_DETAILS -> new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case NOTIFICATION_SETTINGS -> (sdk >= Build.VERSION_CODES.O) ?
					new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
							.putExtra(Settings.EXTRA_APP_PACKAGE, packageName) :
					new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case OVERLAY_SETTINGS -> (sdk >= Build.VERSION_CODES.M) ?
					new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri) :
					new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
			case BATTERY_SETTINGS -> new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
		};
	}

	private record Snapshot(Input input, List<Row> rows) {
	}
}
