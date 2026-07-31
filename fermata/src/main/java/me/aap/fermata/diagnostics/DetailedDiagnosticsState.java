package me.aap.fermata.diagnostics;

/**
 * Supplies the current detailed-diagnostics state. The Android integration owns preferences and
 * the 48-hour expiry; the diagnostics core deliberately does not read either.
 */
@FunctionalInterface
public interface DetailedDiagnosticsState {
	DetailedDiagnosticsState DISABLED = wallTimeMillis -> false;

	boolean isEnabled(long wallTimeMillis);
}
