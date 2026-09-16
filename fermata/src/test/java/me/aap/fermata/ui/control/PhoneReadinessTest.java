package me.aap.fermata.ui.control;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.auto.AutomotiveConnectionState.State;

public class PhoneReadinessTest {
	@Test
	public void mobileBuildShowsOnlyTheChecksThatApplyOnPhone() {
		assertEquals(List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
						PhoneReadiness.Status.NOT_APPLICABLE, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.CONTROL_SESSION,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NOTIFICATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.OVERLAY,
						PhoneReadiness.Status.NOT_APPLICABLE, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.SCREEN_CAPTURE,
						PhoneReadiness.Status.NOT_APPLICABLE, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.BATTERY,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.BATTERY_SETTINGS)),
				PhoneReadiness.evaluate(new PhoneReadiness.Input(false, false,
						State.DISCONNECTED, false, true, false, false)));
	}

	@Test
	public void unknownConnectionIsNotReportedAsDisconnected() {
		assertEquals(new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
				PhoneReadiness.Status.UNKNOWN, PhoneReadiness.Action.NONE), row(
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, false,
						State.DISCONNECTED, true, false, false, false)),
				PhoneReadiness.Check.CONNECTION));
	}

	@Test
	public void disconnectedAutoKeepsOptionalChecksInformational() {
		assertEquals(List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.CONTROL_SESSION,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.ACTION_AVAILABLE,
						PhoneReadiness.Action.NOTIFICATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.OVERLAY,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.OVERLAY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.SCREEN_CAPTURE,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.BATTERY,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.BATTERY_SETTINGS)),
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
						State.DISCONNECTED, false, false, false, false)));
	}

	@Test
	public void connectedAutoReportsAvailableControlAndConfiguredOptionalChecks() {
		assertEquals(List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.CONTROL_SESSION,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NOTIFICATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.OVERLAY,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.OVERLAY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.SCREEN_CAPTURE,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.BATTERY,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.BATTERY_SETTINGS)),
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
						State.CONNECTED, true, true, true, true)));
	}

	@Test
	public void visibleAutoRemainsConnectedRatherThanNeedingASeparateReadinessState() {
		assertEquals(new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
				PhoneReadiness.Status.OK, PhoneReadiness.Action.NONE), row(
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
						State.APP_VISIBLE, false, true, true, false)),
				PhoneReadiness.Check.CONNECTION));
	}

	private static PhoneReadiness.Row row(List<PhoneReadiness.Row> rows,
			PhoneReadiness.Check check) {
		return rows.stream().filter(row -> row.check() == check).findFirst().orElseThrow();
	}
}
