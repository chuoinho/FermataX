package me.aap.fermata.addon.stremio.net;

import java.util.Objects;

/** One defensive copy at ingress, then immutable sharing between transport and cache layers. */
public final class ImmutableBytePayload {
	private final byte[] bytes;

	private ImmutableBytePayload(byte[] bytes) {
		this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
	}

	public static ImmutableBytePayload copyOf(byte[] bytes) {
		return new ImmutableBytePayload(bytes);
	}

	public int size() {
		return bytes.length;
	}

	public byte[] copy() {
		return bytes.clone();
	}
}
