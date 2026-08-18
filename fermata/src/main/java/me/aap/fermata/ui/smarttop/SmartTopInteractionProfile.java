package me.aap.fermata.ui.smarttop;

/**
 * Minimum interaction requirements, independent from space class.
 * TOUCH and AUTOMOTIVE may resolve the same COMPACT/STANDARD/EXPANDED composition with different controls.
 */
public enum SmartTopInteractionProfile {
	TOUCH,
	AUTOMOTIVE;

	public boolean isAutomotive() {
		return this == AUTOMOTIVE;
	}
}
