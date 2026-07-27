package me.aap.fermata.addon.stremio.security;

import androidx.annotation.Nullable;

import java.util.Objects;

/** Secret transport material. Its string representation never reveals stored values. */
public final class StremioSourceSecret {
	private final String transportUrl;
	private final String configurationToken;

	public StremioSourceSecret(String transportUrl, @Nullable String configurationToken) {
		this.transportUrl = requireText(transportUrl, "transportUrl");
		this.configurationToken = clean(configurationToken);
	}

	public String transportUrl() {
		return transportUrl;
	}

	@Nullable
	public String configurationToken() {
		return configurationToken;
	}

	@Override
	public String toString() {
		return "StremioSourceSecret[redacted]";
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}

	@Nullable
	private static String clean(@Nullable String value) {
		return ((value == null) || value.isEmpty()) ? null : value;
	}
}
