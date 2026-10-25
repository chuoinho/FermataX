package me.aap.fermata.ui.control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.auto.AutomotiveConnectionState.State;

public class PhoneReadinessTest {
	@Test
	public void mobileBuildShowsOnlyTheChecksThatApplyOnPhone() {
		assertEquals(List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.STORAGE,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.STORAGE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NOTIFICATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.LOCATION,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.LOCATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.BATTERY,
							PhoneReadiness.Status.INFO, PhoneReadiness.Action.BATTERY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.MICROPHONE,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.MICROPHONE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NAVIGATION,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.NAVIGATION_SETTINGS)),
				PhoneReadiness.evaluate(new PhoneReadiness.Input(false, false,
						State.DISCONNECTED, false, true, true, false, true, true, false, false)));
	}

	@Test
	public void unknownConnectionIsNotReportedAsDisconnected() {
		assertEquals(new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
				PhoneReadiness.Status.UNKNOWN, PhoneReadiness.Action.NONE), row(
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, false,
						State.DISCONNECTED, true, false, false, false, false, false, false, false)),
				PhoneReadiness.Check.CONNECTION));
	}

	@Test
	public void disconnectedAutoKeepsOptionalChecksInformational() {
		assertEquals(List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
						PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.STORAGE,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.STORAGE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.ACTION_AVAILABLE,
						PhoneReadiness.Action.NOTIFICATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.LOCATION,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.LOCATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.BATTERY,
							PhoneReadiness.Status.INFO, PhoneReadiness.Action.BATTERY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.MICROPHONE,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.MICROPHONE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NAVIGATION,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.NAVIGATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.ACCESSIBILITY,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.ACCESSIBILITY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.OVERLAY,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.OVERLAY_SETTINGS)),
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
						State.DISCONNECTED, false, false, false, false, false, false, false, false)));
	}

	@Test
	public void connectedAutoReportsAvailableControlAndConfiguredOptionalChecks() {
		assertEquals(List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NONE),
				new PhoneReadiness.Row(PhoneReadiness.Check.STORAGE,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.STORAGE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NOTIFICATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.LOCATION,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.LOCATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.BATTERY,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.BATTERY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.MICROPHONE,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.MICROPHONE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NAVIGATION,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NAVIGATION_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.ACCESSIBILITY,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.ACCESSIBILITY_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.OVERLAY,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.OVERLAY_SETTINGS)),
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
						State.CONNECTED, true, true, true, true, true, true, true, true, true)));
	}

	@Test
	public void visibleAutoReportsControlAvailabilityInTheCombinedReadinessState() {
		assertEquals(new PhoneReadiness.Row(PhoneReadiness.Check.CONNECTION,
				PhoneReadiness.Status.INFO, PhoneReadiness.Action.NONE), row(
				PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
						State.APP_VISIBLE, false, true, true, true, true, true, true, false)),
				PhoneReadiness.Check.CONNECTION));
	}

	@Test
	public void androidAutoStatusCombinesConnectionAndControlAvailability() {
		List<PhoneReadiness.Row> rows = PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
				State.CONNECTED, false, true, true, true, true, true, true, true));

		assertEquals(PhoneReadiness.Status.INFO,
				row(rows, PhoneReadiness.Check.CONNECTION).status());
		assertFalse(rows.stream().anyMatch(r -> r.check() == PhoneReadiness.Check.CONTROL_SESSION));
	}

	@Test
	public void readinessDoesNotShowScreenCaptureOutsideAnActiveMirrorSession() {
		List<PhoneReadiness.Row> rows = PhoneReadiness.evaluate(new PhoneReadiness.Input(true, true,
				State.CONNECTED, true, true, true, true, true, true, true, true));

		assertFalse(rows.stream().anyMatch(r -> r.check() == PhoneReadiness.Check.SCREEN_CAPTURE));
	}

	@Test
	public void aReadinessSectionCollapsesOnlyWhenEveryRowIsReady() {
		List<PhoneReadiness.Row> ready = List.of(
				new PhoneReadiness.Row(PhoneReadiness.Check.STORAGE,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.STORAGE_SETTINGS),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.OK, PhoneReadiness.Action.NOTIFICATION_SETTINGS));
		List<PhoneReadiness.Row> incomplete = List.of(ready.get(0),
				new PhoneReadiness.Row(PhoneReadiness.Check.NOTIFICATIONS,
						PhoneReadiness.Status.ACTION_AVAILABLE, PhoneReadiness.Action.NOTIFICATION_SETTINGS));

		assertTrue(PhoneReadiness.isSectionReady(ready));
		assertFalse(PhoneReadiness.isSectionReady(incomplete));
		assertFalse(PhoneReadiness.isSectionReady(List.of()));
	}

	@Test
	public void backgroundRestrictionUsesWarningStatusUntilBothChecksPass() {
		List<PhoneReadiness.Row> restricted = PhoneReadiness.evaluate(new PhoneReadiness.Input(false,
				false, State.DISCONNECTED, false, true, true, false, true, true, false, false));
		List<PhoneReadiness.Row> unrestricted = PhoneReadiness.evaluate(new PhoneReadiness.Input(false,
				false, State.DISCONNECTED, false, true, true, false, true, true, false, true));

		assertEquals(PhoneReadiness.Status.INFO,
				row(restricted, PhoneReadiness.Check.BATTERY).status());
		assertEquals(PhoneReadiness.Status.OK,
				row(unrestricted, PhoneReadiness.Check.BATTERY).status());
	}

	private static PhoneReadiness.Row row(List<PhoneReadiness.Row> rows,
			PhoneReadiness.Check check) {
		return rows.stream().filter(row -> row.check() == check).findFirst().orElseThrow();
	}
}
