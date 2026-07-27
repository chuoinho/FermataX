package me.aap.fermata.addon.stremio.net.cache;

import java.util.Objects;

import me.aap.fermata.addon.stremio.security.HttpValidatorPolicy;
import me.aap.fermata.addon.stremio.net.ImmutableBytePayload;

public final class CacheEntry {
	private final ImmutableBytePayload payload;
	private final String etag;
	private final String lastModified;
	private final long validatedAtMillis;

	public CacheEntry(byte[] body, String etag, String lastModified, long validatedAtMillis) {
		this(ImmutableBytePayload.copyOf(body), etag, lastModified, validatedAtMillis);
	}

	public CacheEntry(ImmutableBytePayload payload, String etag, String lastModified,
			long validatedAtMillis) {
		this.payload = Objects.requireNonNull(payload, "payload");
		if (validatedAtMillis < 0) throw new IllegalArgumentException("validatedAtMillis is negative");
		this.etag = HttpValidatorPolicy.sanitize(etag);
		this.lastModified = HttpValidatorPolicy.sanitize(lastModified);
		this.validatedAtMillis = validatedAtMillis;
	}

	public byte[] body() {
		return payload.copy();
	}

	public ImmutableBytePayload payload() {
		return payload;
	}

	public String etag() {
		return etag;
	}

	public String lastModified() {
		return lastModified;
	}

	public long validatedAtMillis() {
		return validatedAtMillis;
	}

	public int sizeBytes() {
		return payload.size();
	}

	public CacheEntry revalidated(long nowMillis) {
		return new CacheEntry(payload, etag, lastModified, nowMillis);
	}
}
