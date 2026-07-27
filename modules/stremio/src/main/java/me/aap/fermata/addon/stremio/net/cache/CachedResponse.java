package me.aap.fermata.addon.stremio.net.cache;

import java.util.Objects;

import me.aap.fermata.addon.stremio.net.ImmutableBytePayload;

public final class CachedResponse {
	private final ImmutableBytePayload payload;
	private final Origin origin;

	public CachedResponse(byte[] body, Origin origin) {
		this(ImmutableBytePayload.copyOf(body), origin);
	}

	public CachedResponse(ImmutableBytePayload payload, Origin origin) {
		this.payload = Objects.requireNonNull(payload, "payload");
		this.origin = Objects.requireNonNull(origin, "origin");
	}

	public byte[] body() {
		return payload.copy();
	}

	public ImmutableBytePayload payload() {
		return payload;
	}

	public Origin origin() {
		return origin;
	}

	public enum Origin {
		FRESH_CACHE,
		STALE_CACHE,
		NETWORK,
		REVALIDATED_CACHE
	}
}
