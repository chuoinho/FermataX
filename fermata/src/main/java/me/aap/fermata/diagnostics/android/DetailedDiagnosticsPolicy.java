package me.aap.fermata.diagnostics.android;

final class DetailedDiagnosticsPolicy {
	static final long NO_SCHEDULE = -1L;

	private DetailedDiagnosticsPolicy() {
	}

	static long expiryDelay(boolean enabled, long expiresAt, long now) {
		if (!enabled || (expiresAt <= 0L)) return NO_SCHEDULE;
		return Math.max(0L, expiresAt - now);
	}
}
