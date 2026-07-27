package me.aap.fermata.addon.stremio.session;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validation for opaque identifiers crossing the session persistence boundary. */
final class StremioSessionIds {
	private static final Pattern OPAQUE_ID = Pattern.compile("[A-Za-z0-9:_-]{1,192}");

	private StremioSessionIds() {
	}

	static String requireOpaque(String value, String name) {
		Objects.requireNonNull(value, name);
		if (!OPAQUE_ID.matcher(value).matches()) {
			throw new IllegalArgumentException(name + " must be an opaque identifier");
		}
		return value;
	}

	static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
		return value;
	}
}
