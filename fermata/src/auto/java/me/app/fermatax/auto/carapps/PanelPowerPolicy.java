package me.app.fermatax.auto.carapps;

/** What the phone screen does while Car Apps shows on the car. */
public final class PanelPowerPolicy {

	public enum PanelMode {
		NONE,
		DIM,
		PANEL_OFF,
		SLEEP
	}

	public static boolean sdkSupported(int sdk) {
		return sdk >= 30 && sdk <= 35;
	}

	private PanelPowerPolicy() {
	}

	public static PanelMode forButton(int caps, int sdk, boolean dark, boolean keyguardLocked) {
		if (dark) return PanelMode.NONE;
		if (keyguardLocked || ((caps & ICarDisplayServer.CAP_OWN_POWER_GROUP) != 0 && sdk >= 36)) {
			return PanelMode.SLEEP;
		}
		if ((caps & ICarDisplayServer.CAP_PANEL_POWER) != 0) {
			return PanelMode.PANEL_OFF;
		}
		return PanelMode.DIM;
	}

	public static PanelMode decide(int caps, boolean interactive, boolean keyguardLocked) {
		if (!interactive || keyguardLocked) {
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
