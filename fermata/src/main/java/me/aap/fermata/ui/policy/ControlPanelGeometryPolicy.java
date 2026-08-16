package me.aap.fermata.ui.policy;

/** Pure geometry policy for keeping the five transport controls usable on narrow player bars. */
public final class ControlPanelGeometryPolicy {
	private ControlPanelGeometryPolicy() {
	}

	public static int getTransportButtonSize(int panelWidth, float startPercent, float endPercent,
			int defaultSize, int buttonCount) {
		int fallback = Math.max(0, defaultSize);
		if ((panelWidth <= 0) || (buttonCount <= 0)) return fallback;
		float span = endPercent - startPercent;
		if (!(span > 0F) || (span > 1F)) return fallback;
		int available = Math.round(panelWidth * span);
		if (available <= 0) return fallback;
		int slot = Math.max(1, available / buttonCount);
		return Math.min(fallback, slot);
	}

	public static int getTransportPadding(int buttonSize, int defaultPadding) {
		return Math.min(Math.max(0, defaultPadding), Math.max(0, buttonSize / 4));
	}

	public static int getTransportTopMargin(int buttonSize, int defaultSize, int baseTopMargin) {
		return Math.max(0, baseTopMargin) + Math.max(0, defaultSize - buttonSize) / 2;
	}
}
