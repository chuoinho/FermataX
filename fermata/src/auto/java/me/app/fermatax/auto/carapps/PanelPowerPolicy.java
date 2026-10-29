package me.app.fermatax.auto.carapps;

/**
 * State machine and policy for phone physical panel power management during Car Apps sessions.
 */
public final class PanelPowerPolicy {

	public static final String PREF_PANEL_OFF = "CAR_APPS_PANEL_OFF";

	public enum PanelEvent {
		USER_OFF,
		USER_ON,
		SCREEN_OFF_BROADCAST,
		CAR_DISCONNECTED,
		SERVER_DIED,
		RELEASED,
		CAPABILITY_MISSING
	}

	public enum PanelAction {
		PANEL_OFF_AND_HOLD,
		PANEL_ON_AND_WAKE,
		NONE
	}

	private PanelPowerPolicy() {
	}

	public static PanelAction next(boolean panelOff, PanelEvent e) {
		if (e == null || e == PanelEvent.CAPABILITY_MISSING) {
			return PanelAction.NONE;
		}

		if (panelOff) {
			if (e == PanelEvent.USER_OFF) {
				return PanelAction.NONE;
			}
			return PanelAction.PANEL_ON_AND_WAKE;
		} else {
			if (e == PanelEvent.USER_OFF) {
				return PanelAction.PANEL_OFF_AND_HOLD;
			}
			return PanelAction.NONE;
		}
	}
}
