package me.app.fermatax.auto.carapps;

/** What the phone screen does while Car Apps shows on the car. */
public final class PanelPowerPolicy {

	public enum PanelMode {
		NONE,
		DIM,
		PANEL_OFF
	}

	public static boolean sdkSupported(int sdk) {
		return sdk >= 29 && sdk <= 35;
	}

	private PanelPowerPolicy() {
	}

	public static PanelMode decide(boolean prefPanelOff, int caps, boolean interactive, boolean keyguardLocked) {
		if (!prefPanelOff || !interactive || keyguardLocked) {
			return PanelMode.NONE;
		}

		if ((caps & ICarDisplayServer.CAP_PANEL_POWER) != 0) {
			return PanelMode.PANEL_OFF;
		}

		if ((caps & ICarDisplayServer.CAP_OWN_POWER_GROUP) != 0) {
			return PanelMode.NONE;
		}

		return PanelMode.DIM;
	}
}
