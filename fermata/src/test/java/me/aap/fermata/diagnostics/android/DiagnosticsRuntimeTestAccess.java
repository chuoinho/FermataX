package me.aap.fermata.diagnostics.android;

/** Gives tests in other packages the package-private runtime reset. */
public final class DiagnosticsRuntimeTestAccess {
	private DiagnosticsRuntimeTestAccess() {
	}

	public static void reset() {
		AndroidDiagnosticsRuntime.resetForTesting();
	}
}
