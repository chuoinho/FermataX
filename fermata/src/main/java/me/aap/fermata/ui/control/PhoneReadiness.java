package me.aap.fermata.ui.control;

import java.util.List;

import me.aap.fermata.auto.AutomotiveConnectionState;

/** Pure readiness state for the phone settings UI. */
public final class PhoneReadiness {
	private PhoneReadiness() {
	}

	public static List<Row> evaluate(Input input) {
		java.util.ArrayList<Row> rows = new java.util.ArrayList<>();
		if (input.autoBuild()) rows.add(connection(input));
		rows.add(storage(input));
		rows.add(new Row(Check.NOTIFICATIONS, input.notificationsEnabled() ? Status.OK :
				Status.ACTION_AVAILABLE, Action.NOTIFICATION_SETTINGS));
		rows.add(location(input));
		rows.add(new Row(Check.BATTERY, input.backgroundUnrestricted() ? Status.OK :
				Status.INFO, Action.BATTERY_SETTINGS));
		rows.add(microphone(input));
		rows.add(new Row(Check.NAVIGATION, input.navigationNotificationsGranted() ? Status.OK :
				Status.ACTION_AVAILABLE, Action.NAVIGATION_SETTINGS));
		if (input.autoBuild()) {
			rows.add(accessibility(input));
			rows.add(overlay(input));
		}
		return List.copyOf(rows);
	}

	public static boolean isSectionReady(List<Row> rows) {
		return !rows.isEmpty() && rows.stream().allMatch(row -> row.status() == Status.OK);
	}

	private static Row connection(Input input) {
		if (!input.autoBuild()) {
			return new Row(Check.CONNECTION, Status.NOT_APPLICABLE, Action.NONE);
		}
		if (!input.connectionObserved() || (input.connectionState() == null)) {
			return new Row(Check.CONNECTION, Status.UNKNOWN, Action.NONE);
		}
		return new Row(Check.CONNECTION,
				(input.connectionState() == AutomotiveConnectionState.State.DISCONNECTED ||
						!input.controlAvailable()) ? Status.INFO : Status.OK, Action.NONE);
	}

	private static Row storage(Input input) {
		return new Row(Check.STORAGE, input.storageGranted() ? Status.OK : Status.ACTION_AVAILABLE,
				Action.STORAGE_SETTINGS);
	}

	private static Row location(Input input) {
		return new Row(Check.LOCATION, input.locationGranted() ? Status.OK : Status.ACTION_AVAILABLE,
				Action.LOCATION_SETTINGS);
	}

	private static Row accessibility(Input input) {
		if (!input.autoBuild()) {
			return new Row(Check.ACCESSIBILITY, Status.NOT_APPLICABLE, Action.NONE);
		}
		return new Row(Check.ACCESSIBILITY, input.accessibilityGranted() ? Status.OK :
				Status.ACTION_AVAILABLE, Action.ACCESSIBILITY_SETTINGS);
	}

	private static Row microphone(Input input) {
		return new Row(Check.MICROPHONE, input.microphoneGranted() ? Status.OK : Status.ACTION_AVAILABLE,
				Action.MICROPHONE_SETTINGS);
	}

	private static Row overlay(Input input) {
		return new Row(Check.OVERLAY, input.overlayGranted() ? Status.OK : Status.ACTION_AVAILABLE,
				Action.OVERLAY_SETTINGS);
	}

	public enum Check {
		CONNECTION,
		CONTROL_SESSION,
		STORAGE,
		LOCATION,
		ACCESSIBILITY,
		MICROPHONE,
		NOTIFICATIONS,
		OVERLAY,
		SCREEN_CAPTURE,
		BATTERY,
		NAVIGATION
	}

	public enum Status {
		OK,
		ACTION_AVAILABLE,
		INFO,
		UNKNOWN,
		NOT_APPLICABLE
	}

	public enum Action {
		NONE,
		APP_DETAILS,
		STORAGE_SETTINGS,
		LOCATION_SETTINGS,
		ACCESSIBILITY_SETTINGS,
		MICROPHONE_SETTINGS,
		NOTIFICATION_SETTINGS,
		OVERLAY_SETTINGS,
		BATTERY_SETTINGS,
		NAVIGATION_SETTINGS
	}

	public record Row(Check check, Status status, Action action) {
	}

	public record Input(boolean autoBuild, boolean connectionObserved,
			AutomotiveConnectionState.State connectionState, boolean controlAvailable,
			boolean storageGranted, boolean locationGranted, boolean accessibilityGranted,
			boolean microphoneGranted, boolean notificationsEnabled, boolean overlayGranted,
			boolean backgroundUnrestricted, boolean navigationNotificationsGranted) {
		public Input(boolean autoBuild, boolean connectionObserved,
				AutomotiveConnectionState.State connectionState, boolean controlAvailable,
				boolean storageGranted, boolean locationGranted, boolean accessibilityGranted,
				boolean microphoneGranted, boolean notificationsEnabled, boolean overlayGranted,
					boolean backgroundUnrestricted) {
			this(autoBuild, connectionObserved, connectionState, controlAvailable, storageGranted,
					locationGranted, accessibilityGranted, microphoneGranted, notificationsEnabled,
					overlayGranted, backgroundUnrestricted, false);
		}
	}

	public static boolean hasNotificationListenerCheck(android.content.Context ctx) {
		if (ctx == null) return false;
		// Exact package match: a substring check also matches e.g. the ".debug" build of this app.
		return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx)
				.contains(ctx.getPackageName());
	}

	public static android.content.Intent createNotificationListenerSettingsIntent() {
		return new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
	}
}
