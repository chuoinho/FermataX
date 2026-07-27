package me.aap.fermata.addon.stremio.security;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Keeps untrusted HTTP validators bounded and free of provider credentials. */
public final class HttpValidatorPolicy {
	public static final int MAX_CHARS = 512;

	private HttpValidatorPolicy() {
	}

	@Nullable
	public static String sanitize(@Nullable String value) {
		return sanitize(value, List.of());
	}

	@Nullable
	public static String sanitize(@Nullable String value,
			Collection<String> knownSecrets) {
		if ((value == null) || value.isEmpty() || (value.length() > MAX_CHARS)) return null;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c < 0x20) || (c == 0x7f)) return null;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		if (lower.matches(".*(?:access[_-]?token|api[_-]?key|authorization|bearer|" +
				"credential|password|secret|session|signature|token).*")) return null;
		return SecretTaintDetector.isTainted(value, knownSecrets) ? null : value;
	}
}
