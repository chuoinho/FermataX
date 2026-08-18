package me.aap.fermata.ui.smarttop;

/**
 * Compatibility entry point for Dashboard viewport plumbing.
 * All space-class decisions are delegated to SmartTopAdaptivePolicy; geometry no longer belongs here.
 */
public final class SmartTopLayoutPolicy {
	public static final int VISUAL_ACTION_DP = 44;
	public static final int ACTION_GLYPH_DP = 22;
	public static final int MIN_CONTROL_DP = 48;
	public static final int FOCUS_RING_DP = 2;
	public static final int MOBILE_ARTWORK_DP = 56;
	public static final int MAX_LABELED_ACTION_DP = 112;
	public static final int MAX_MOBILE_LABELED_ACTION_DP = 108;

	private SmartTopLayoutPolicy() {
	}

	public static SmartTopLayoutMode resolve(float measuredContentWidthDp, float fontScale) {
		return SmartTopAdaptivePolicy.resolveMode(new SmartTopEnvironment(
				measuredContentWidthDp, 0F, fontScale, SmartTopInteractionProfile.TOUCH));
	}

	/** Compatibility bridge removed from the renderer in Phase 4. */
	@Deprecated
	public static int cardHeightDp(SmartTopLayoutMode mode, float fontScale) {
		float boundedScale = Math.max(1F, Math.min(2F, fontScale));
		return mode.cardHeightDp() + Math.round(40F * (boundedScale - 1F));
	}
}
