package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;

public record StremioVideoRecord(
		String videoKey,
		String metaKey,
		String type,
		String providerVideoId,
		String title,
		Integer seasonNumber,
		Integer episodeNumber,
		long releasedMs,
		long durationMs,
		String thumbnailUrl,
		long updatedMs) {

	public StremioVideoRecord {
		Objects.requireNonNull(videoKey, "videoKey");
		Objects.requireNonNull(metaKey, "metaKey");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(providerVideoId, "providerVideoId");
		Objects.requireNonNull(title, "title");
		thumbnailUrl = ArtworkUrlSanitizer.sanitize(thumbnailUrl);
	}
}
