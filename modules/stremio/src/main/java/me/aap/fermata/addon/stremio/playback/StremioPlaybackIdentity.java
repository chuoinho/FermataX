package me.aap.fermata.addon.stremio.playback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/** Stable opaque identity shared by short-lived playback descriptors. */
public final class StremioPlaybackIdentity {
	private final String contentKey;
	private final String videoKey;

	private StremioPlaybackIdentity(String contentKey, String videoKey) {
		this.contentKey = contentKey;
		this.videoKey = videoKey;
	}

	public static StremioPlaybackIdentity canonical(
			String type, String contentId, String videoId) {
		return create("canonical", type, contentId, videoId);
	}

	public static StremioPlaybackIdentity scoped(
			String sourceUuid, String type, String contentId, String videoId) {
		return create(requireText(sourceUuid, "sourceUuid"), type, contentId, videoId);
	}

	private static StremioPlaybackIdentity create(
			String scope, String type, String contentId, String videoId) {
		String normalizedType = requireText(type, "type").toLowerCase(Locale.ROOT);
		String rawContent = requireText(contentId, "contentId");
		String rawVideo = requireText(videoId, "videoId");
		String contentKey = "stremio:content:" + digest(scope, normalizedType, rawContent);
		String videoKey = "stremio:video:" + digest(scope, normalizedType, rawContent, rawVideo);
		return new StremioPlaybackIdentity(contentKey, videoKey);
	}

	public String contentKey() {
		return contentKey;
	}

	public String videoKey() {
		return videoKey;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof StremioPlaybackIdentity other)) return false;
		return contentKey.equals(other.contentKey) && videoKey.equals(other.videoKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(contentKey, videoKey);
	}

	@Override
	public String toString() {
		return "StremioPlaybackIdentity{content=" + contentKey + ", video=" + videoKey + '}';
	}

	static String digest(String... values) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String value : values) {
				byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
				digest.update((byte) (bytes.length >>> 24));
				digest.update((byte) (bytes.length >>> 16));
				digest.update((byte) (bytes.length >>> 8));
				digest.update((byte) bytes.length);
				digest.update(bytes);
			}
			byte[] hash = digest.digest();
			StringBuilder id = new StringBuilder(32);
			for (int i = 0; i < 16; i++) id.append(String.format(Locale.ROOT, "%02x", hash[i]));
			return id.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new AssertionError("SHA-256 is unavailable", ex);
		}
	}

	static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return value;
	}
}
