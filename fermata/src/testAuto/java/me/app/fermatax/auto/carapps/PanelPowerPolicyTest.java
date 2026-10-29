package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import me.app.fermatax.auto.carapps.PanelPowerPolicy.PanelAction;
import me.app.fermatax.auto.carapps.PanelPowerPolicy.PanelEvent;

public class PanelPowerPolicyTest {

	@Test
	public void everyTerminalEventRestoresPanelWhenOff() {
		PanelEvent[] terminalEvents = {
				PanelEvent.USER_ON,
				PanelEvent.SCREEN_OFF_BROADCAST,
				PanelEvent.CAR_DISCONNECTED,
				PanelEvent.SERVER_DIED,
				PanelEvent.RELEASED
		};

		for (PanelEvent event : terminalEvents) {
			PanelAction action = PanelPowerPolicy.next(true, event);
			assertEquals("Event " + event + " while panel is off must return PANEL_ON_AND_WAKE",
					PanelAction.PANEL_ON_AND_WAKE, action);
		}
	}

	@Test
	public void userOffTurnsPanelOffAndHolds() {
		PanelAction action = PanelPowerPolicy.next(false, PanelEvent.USER_OFF);
		assertEquals("USER_OFF while panel is on must return PANEL_OFF_AND_HOLD",
				PanelAction.PANEL_OFF_AND_HOLD, action);
	}

	@Test
	public void screenOffBroadcastWhilePanelOffHandsBackThePhone() {
		PanelAction action = PanelPowerPolicy.next(true, PanelEvent.SCREEN_OFF_BROADCAST);
		assertEquals("SCREEN_OFF_BROADCAST while panel is off must restore panel",
				PanelAction.PANEL_ON_AND_WAKE, action);
	}

	@Test
	public void capabilityMissingNeverTurnsPanelOff() {
		PanelAction action = PanelPowerPolicy.next(false, PanelEvent.CAPABILITY_MISSING);
		assertEquals("CAPABILITY_MISSING must return NONE and never turn panel off",
				PanelAction.NONE, action);
	}

	@Test
	public void userOffWhileAlreadyOffIsNoOp() {
		PanelAction action = PanelPowerPolicy.next(true, PanelEvent.USER_OFF);
		assertEquals("USER_OFF while already off must return NONE",
				PanelAction.NONE, action);
	}

	@Test
	public void nonOffEventsWhilePanelOnAreNoOp() {
		PanelEvent[] nonOffEvents = {
				PanelEvent.USER_ON,
				PanelEvent.SCREEN_OFF_BROADCAST,
				PanelEvent.CAR_DISCONNECTED,
				PanelEvent.SERVER_DIED,
				PanelEvent.RELEASED
		};

		for (PanelEvent event : nonOffEvents) {
			PanelAction action = PanelPowerPolicy.next(false, event);
			assertEquals("Event " + event + " while panel is on must return NONE",
					PanelAction.NONE, action);
		}
	}

	@Test
	public void nullEventReturnsNone() {
		assertEquals(PanelAction.NONE, PanelPowerPolicy.next(true, null));
		assertEquals(PanelAction.NONE, PanelPowerPolicy.next(false, null));
	}
}
