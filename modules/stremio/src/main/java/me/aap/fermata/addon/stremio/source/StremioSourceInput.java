package me.aap.fermata.addon.stremio.source;

import java.util.Objects;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;

/** User-provided source transport material; never persist or log this object. */
public record StremioSourceInput(String transportUrl, String configurationToken,
		NetworkConsent networkConsent) {
	public StremioSourceInput(String transportUrl, String configurationToken) {
		this(transportUrl, configurationToken, NetworkConsent.STRICT);
	}

	public StremioSourceInput {
		Objects.requireNonNull(transportUrl, "transportUrl");
		if (transportUrl.isBlank()) throw new IllegalArgumentException("transportUrl is blank");
		configurationToken = clean(configurationToken);
		Objects.requireNonNull(networkConsent, "networkConsent");
	}

	public StremioSourceSecret secret() {
		return new StremioSourceSecret(transportUrl, configurationToken);
	}

	@Override
	public String toString() {
		return "StremioSourceInput[redacted]";
	}

	private static String clean(String value) {
		return ((value == null) || value.isEmpty()) ? null : value;
	}
}
