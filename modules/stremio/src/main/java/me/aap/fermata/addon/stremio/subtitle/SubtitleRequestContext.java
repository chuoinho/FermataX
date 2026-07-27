package me.aap.fermata.addon.stremio.subtitle;

import java.util.Objects;

/** Protocol context used to resolve subtitles for one selected stream. */
public record SubtitleRequestContext(String videoId, String videoHash,
		Long videoSize, String filename) {
	public SubtitleRequestContext {
		videoId = requireText(videoId, "videoId");
		if ((videoHash != null) && videoHash.isBlank()) videoHash = null;
		if ((videoSize != null) && videoSize < 0L) {
			throw new IllegalArgumentException("videoSize cannot be negative");
		}
		if ((filename != null) && filename.isBlank()) filename = null;
	}

	public static SubtitleRequestContext forVideo(String videoId) {
		return new SubtitleRequestContext(videoId, null, null, null);
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
