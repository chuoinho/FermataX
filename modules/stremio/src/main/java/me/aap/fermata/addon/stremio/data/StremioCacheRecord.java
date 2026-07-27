package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

public record StremioCacheRecord(
		String cacheKey,
		String sourceUuid,
		String resource,
		byte[] payload,
		String etag,
		String lastModified,
		long storedMs,
		long freshUntilMs,
		long staleUntilMs) {

	public StremioCacheRecord {
		Objects.requireNonNull(cacheKey, "cacheKey");
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(resource, "resource");
		payload = Objects.requireNonNull(payload, "payload").clone();
		if ((freshUntilMs < storedMs) || (staleUntilMs < freshUntilMs)) {
			throw new IllegalArgumentException("Invalid cache lifetime");
		}
	}

	@Override
	public byte[] payload() {
		return payload.clone();
	}
}
