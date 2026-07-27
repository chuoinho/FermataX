package me.aap.fermata.addon.stremio.security;

import androidx.annotation.Nullable;

/** Fail-closed boundary for provider-controlled text written to durable Stremio tables. */
public final class StremioDurableTextPolicy {
	private StremioDurableTextPolicy() {
	}

	public static void requireUntainted(String record, @Nullable String... fields) {
		if (isTainted(fields)) {
			throw new SecurityException("Refusing tainted Stremio " + record);
		}
	}

	public static boolean isTainted(@Nullable String... fields) {
		if (fields == null) return false;
		for (String field : fields) {
			if (SecretTaintDetector.isTainted(field)) return true;
		}
		return false;
	}
}
