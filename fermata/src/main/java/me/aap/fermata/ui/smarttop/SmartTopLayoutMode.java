package me.aap.fermata.ui.smarttop;

/**
 * Coarse space/composition class only. It intentionally carries no dimensions or host identity;
 * SmartTopAdaptivePolicy resolves all geometry from the measured runtime environment.
 */
public enum SmartTopLayoutMode {
	COMPACT,
	STANDARD,
	EXPANDED
}
