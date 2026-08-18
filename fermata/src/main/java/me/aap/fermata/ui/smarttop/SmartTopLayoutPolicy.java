package me.aap.fermata.ui.smarttop;

/** Compatibility entry point for Dashboard resize plumbing; all decisions live in SmartTopAdaptivePolicy. */
public final class SmartTopLayoutPolicy {
	private SmartTopLayoutPolicy() {
	}

	public static SmartTopLayoutMode resolve(float measuredContentWidthDp, float fontScale) {
		return SmartTopAdaptivePolicy.resolveMode(new SmartTopEnvironment(
				measuredContentWidthDp, 0F, fontScale, SmartTopInteractionProfile.TOUCH));
	}
}
