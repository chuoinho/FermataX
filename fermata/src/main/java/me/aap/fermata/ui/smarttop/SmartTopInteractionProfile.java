package me.aap.fermata.ui.smarttop;

/** Interaction requirements are independent from the amount of layout space available. */
public enum SmartTopInteractionProfile {
	TOUCH,
	AUTOMOTIVE;

	public boolean isAutomotive() {
		return this == AUTOMOTIVE;
	}
}
