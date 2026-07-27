package me.aap.fermata.addon.stremio.net.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/** Opaque cache identity. Raw provider URLs and credentials are never retained. */
public record CacheKey(UUID sourceUuid, String resource, String requestDigest) {
	public CacheKey {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(resource, "resource");
		Objects.requireNonNull(requestDigest, "requestDigest");
		if (!resource.matches("[A-Za-z0-9._:-]{1,64}")) {
			throw new IllegalArgumentException("Invalid cache resource name");
		}
		if (!requestDigest.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("requestDigest must be SHA-256 hex");
		}
	}

	public static CacheKey derive(UUID sourceUuid, String resource, String canonicalRequestIdentity) {
		Objects.requireNonNull(canonicalRequestIdentity, "canonicalRequestIdentity");
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(
					canonicalRequestIdentity.getBytes(StandardCharsets.UTF_8));
			return new CacheKey(sourceUuid, resource, toHex(digest));
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("SHA-256 is required by Android", ex);
		}
	}

	private static String toHex(byte[] bytes) {
		char[] hex = "0123456789abcdef".toCharArray();
		char[] result = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xff;
			result[i * 2] = hex[value >>> 4];
			result[i * 2 + 1] = hex[value & 0x0f];
		}
		return new String(result);
	}

	@Override
	public String toString() {
		return sourceUuid + ":" + resource + ":" + requestDigest.substring(0, 12);
	}
}
