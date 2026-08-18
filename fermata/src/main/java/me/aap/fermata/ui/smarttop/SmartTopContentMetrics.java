package me.aap.fermata.ui.smarttop;

/** Intrinsic text/data measurements supplied by the Android renderer to the pure adaptive policy. */
public record SmartTopContentMetrics(
		float titleWidthDp,
		float terminalLabelWidthDp,
		int recentItems) {
	public SmartTopContentMetrics {
		titleWidthDp = Math.max(0F, titleWidthDp);
		terminalLabelWidthDp = Math.max(0F, terminalLabelWidthDp);
		recentItems = Math.max(0, Math.min(SmartTopViewState.MAX_QUICK_RECENT, recentItems));
	}

	public static SmartTopContentMetrics empty() {
		return new SmartTopContentMetrics(0F, 0F, 0);
	}
}
