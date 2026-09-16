package me.aap.fermata.ui.control;

import java.util.List;

import me.aap.fermata.auto.AutomotiveConnectionState;

/** Pure readiness state for the phone settings UI. */
public final class PhoneReadiness {
	private PhoneReadiness() {
	}

	public static List<Row> evaluate(Input input) {
		return List.of(connection(input),
				new Row(Check.CONTROL_SESSION, input.controlAvailable() ? Status.OK : Status.INFO,
						Action.NONE),
				new Row(Check.NOTIFICATIONS, input.notificationsEnabled() ? Status.OK :
						Status.ACTION_AVAILABLE, Action.NOTIFICATION_SETTINGS),
				overlay(input),
				new Row(Check.SCREEN_CAPTURE, input.autoBuild() ? Status.INFO :
						Status.NOT_APPLICABLE, Action.NONE),
				new Row(Check.BATTERY, Status.INFO, Action.BATTERY_SETTINGS));
	}

	private static Row connection(Input input) {
		if (!input.autoBuild()) {
			return new Row(Check.CONNECTION, Status.NOT_APPLICABLE, Action.NONE);
		}
		if (!input.connectionObserved() || (input.connectionState() == null)) {
			return new Row(Check.CONNECTION, Status.UNKNOWN, Action.NONE);
		}
		return new Row(Check.CONNECTION,
				input.connectionState() == AutomotiveConnectionState.State.DISCONNECTED ?
						Status.INFO : Status.OK, Action.NONE);
	}

	private static Row overlay(Input input) {
		if (!input.autoBuild()) {
			return new Row(Check.OVERLAY, Status.NOT_APPLICABLE, Action.NONE);
		}
		return new Row(Check.OVERLAY, input.overlayGranted() ? Status.OK : Status.INFO,
				Action.OVERLAY_SETTINGS);
	}

	public enum Check {
		CONNECTION,
		CONTROL_SESSION,
		NOTIFICATIONS,
		OVERLAY,
		SCREEN_CAPTURE,
		BATTERY
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
		NOTIFICATION_SETTINGS,
		OVERLAY_SETTINGS,
		BATTERY_SETTINGS
	}

	public record Row(Check check, Status status, Action action) {
	}

	public record Input(boolean autoBuild, boolean connectionObserved,
			AutomotiveConnectionState.State connectionState, boolean controlAvailable,
			boolean notificationsEnabled, boolean overlayGranted, boolean batteryExempt) {
	}
}
