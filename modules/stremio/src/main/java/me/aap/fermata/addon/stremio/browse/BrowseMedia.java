package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;
import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;

public record BrowseMedia(
		String sourceUuid,
		String type,
		String id,
		String title,
		String poster,
		String background,
		String description,
		String releaseInfo,
		String imdbRating,
		StremioDuration duration,
		List<String> genres,
		String language) {
	public BrowseMedia {
		sourceUuid = requireText(sourceUuid, "sourceUuid");
		type = requireText(type, "type");
		id = requireText(id, "id");
		title = requireText(title, "title");
		poster = ArtworkUrlSanitizer.sanitize(poster);
		if (poster == null) poster = ArtworkUrlSanitizer.canonicalPoster(type, id);
		background = ArtworkUrlSanitizer.sanitize(background);
		genres = List.copyOf(Objects.requireNonNull(genres, "genres"));
	}

	public BrowseMedia(String sourceUuid, String type, String id, String title, String poster,
			String background, String description, String releaseInfo, StremioDuration duration,
			List<String> genres, String language) {
		this(sourceUuid, type, id, title, poster, background, description, releaseInfo, null,
				duration, genres, language);
	}

	public String scopedId() {
		return sourceUuid + ':' + type + ':' + id;
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}

	@Override
	public String toString() {
		return "BrowseMedia[sourceUuid=" + sourceUuid + ", type=" + type +
				", id=<redacted>, title=<redacted>, media=<redacted>, genres=" +
				genres.size() + ']';
	}
}
