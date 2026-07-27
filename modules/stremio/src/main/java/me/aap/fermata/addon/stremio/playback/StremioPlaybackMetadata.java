package me.aap.fermata.addon.stremio.playback;

import java.util.Objects;

import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;

/** Exact user-visible metadata captured before provider stream aggregation. */
public record StremioPlaybackMetadata(String title, String artwork, long durationMillis) {
	public static final long UNKNOWN_DURATION = -1L;

	public StremioPlaybackMetadata {
		Objects.requireNonNull(title, "title");
		if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
		artwork = ArtworkUrlSanitizer.sanitize(artwork);
		if (durationMillis < UNKNOWN_DURATION) {
			throw new IllegalArgumentException("durationMillis is invalid");
		}
	}

	@Override
	public String toString() {
		return "StremioPlaybackMetadata{title=<redacted>, artwork=<redacted>, duration=" +
				durationMillis + '}';
	}
}
