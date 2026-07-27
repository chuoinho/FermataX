package me.aap.fermata.addon.stremio.source;

import java.util.UUID;

import me.aap.fermata.addon.stremio.data.StremioSourceRecord;

/** Canonical codec for the opaque reference stored beside a Stremio source. */
public final class StremioSecretReference {
	private static final String PREFIX = "secure:stremio-source:";
	private static final String LEGACY_PREFIX = "secure:";

	private StremioSecretReference() {
	}

	public static String create(String secretId) {
		return PREFIX + canonicalUuid(secretId);
	}

	public static String resolve(StremioSourceRecord source) {
		String reference = source.secretRef();
		if (reference == null) return canonicalUuid(source.sourceUuid());
		if (reference.startsWith(PREFIX)) {
			return canonicalUuid(reference.substring(PREFIX.length()));
		}
		if (reference.startsWith(LEGACY_PREFIX)) {
			return canonicalUuid(reference.substring(LEGACY_PREFIX.length()));
		}
		return canonicalUuid(source.sourceUuid());
	}

	private static String canonicalUuid(String value) {
		try {
			String canonical = UUID.fromString(value).toString();
			if (!canonical.equals(value)) throw new IllegalArgumentException();
			return canonical;
		} catch (NullPointerException | IllegalArgumentException failure) {
			throw new IllegalArgumentException("secretId must be a canonical UUID", failure);
		}
	}
}
