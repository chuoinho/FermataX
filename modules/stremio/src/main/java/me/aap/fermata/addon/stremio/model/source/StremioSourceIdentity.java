package me.aap.fermata.addon.stremio.model.source;

import java.util.Objects;
import java.util.UUID;

/** Durable source identity. Transport edits never replace the source UUID. */
public record StremioSourceIdentity(String sourceUuid, String transportFingerprint) {
	private static final int SHA256_BASE64_URL_LENGTH = 43;

	public StremioSourceIdentity {
		sourceUuid = canonicalUuid(sourceUuid);
		transportFingerprint = requireFingerprint(transportFingerprint);
	}

	public static StremioSourceIdentity create(String transportIdentity) {
		return new StremioSourceIdentity(UUID.randomUUID().toString(),
				TransportFingerprint.create(transportIdentity));
	}

	public static StremioSourceIdentity restore(String sourceUuid, String transportFingerprint) {
		return new StremioSourceIdentity(sourceUuid, transportFingerprint);
	}

	public StremioSourceIdentity withTransport(String transportIdentity) {
		return new StremioSourceIdentity(sourceUuid, TransportFingerprint.create(transportIdentity));
	}

	private static String canonicalUuid(String value) {
		Objects.requireNonNull(value, "sourceUuid");
		try {
			String canonical = UUID.fromString(value).toString();
			if (!canonical.equals(value)) {
				throw new IllegalArgumentException("sourceUuid must use canonical UUID form");
			}
			return canonical;
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("sourceUuid must be a canonical UUID", ex);
		}
	}

	private static String requireFingerprint(String value) {
		Objects.requireNonNull(value, "transportFingerprint");
		if ((value.length() != SHA256_BASE64_URL_LENGTH) ||
				!value.matches("[A-Za-z0-9_-]+")) {
			throw new IllegalArgumentException("transportFingerprint must be a SHA-256 base64url value");
		}
		return value;
	}
}
