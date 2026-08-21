package me.aap.fermata.ui.smarttop;

/**
 * Compatibility bridge for existing Dashboard resize plumbing.
 * This class owns no layout policy; all space-class decisions delegate to SmartTopAdaptivePolicy.
 */
public final class SmartTopLayoutPolicy {
	private SmartTopLayoutPolicy() {
	}

	public static SmartTopLayoutMode resolve(float measuredContentWidthDp, float fontScale) {
		return SmartTopAdaptivePolicy.resolveMode(new SmartTopEnvironment(
				measuredContentWidthDp, 0F, fontScale, SmartTopInteractionProfile.TOUCH));
	}
}
