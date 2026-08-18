package me.aap.fermata.ui.smarttop;

/** Resolves layout from measured Dashboard content, never from the full display alone. */
public final class SmartTopLayoutPolicy {
	public static final int VISUAL_ACTION_DP = 44;
	public static final int ACTION_GLYPH_DP = 22;
	public static final int MIN_CONTROL_DP = 48;
	public static final int FOCUS_RING_DP = 2;
	public static final int MOBILE_ARTWORK_DP = 56;
	public static final int MAX_LABELED_ACTION_DP = 112;
	public static final int MAX_MOBILE_LABELED_ACTION_DP = 120;

	private SmartTopLayoutPolicy() {
	}

	public static SmartTopLayoutMode resolve(float measuredContentWidthDp, float fontScale) {
		float scale = Math.max(1F, fontScale);
		if ((measuredContentWidthDp < 460F) ||
				((measuredContentWidthDp < 700F) && (scale >= 1.5F))) {
			return SmartTopLayoutMode.COMPACT;
		}
		if ((measuredContentWidthDp >= 1000F) && (scale <= 1.5F)) {
			return SmartTopLayoutMode.EXPANDED;
		}
		return SmartTopLayoutMode.STANDARD;
	}

	public static boolean showQuickRecent(SmartTopLayoutMode mode,
			float measuredContentWidthDp, float fontScale, int actionCount, int titleLength) {
		return (mode != SmartTopLayoutMode.COMPACT) && (measuredContentWidthDp >= 640F) &&
				(fontScale <= 1.3F) && (actionCount <= 5) && (titleLength <= 48);
	}

	/** Preserves the approved base geometry while making room for accessibility text scaling. */
	public static int cardHeightDp(SmartTopLayoutMode mode, float fontScale) {
		float boundedScale = Math.max(1F, Math.min(2F, fontScale));
		return mode.cardHeightDp() + Math.round(40F * (boundedScale - 1F));
	}
}
