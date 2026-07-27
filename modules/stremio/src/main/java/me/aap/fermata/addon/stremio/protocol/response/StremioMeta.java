package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

public record StremioMeta(
		String id,
		String type,
		String name,
		String poster,
		String posterShape,
		String background,
		String logo,
		String description,
		String releaseInfo,
		String imdbRating,
		StremioDuration runtime,
		List<String> genres,
		String language,
		List<StremioVideo> videos) {
	public StremioMeta {
		id = required(id, "id");
		type = required(type, "type");
		name = required(name, "name");
		genres = List.copyOf(Objects.requireNonNull(genres, "genres"));
		videos = List.copyOf(Objects.requireNonNull(videos, "videos"));
	}

	public StremioMeta(String id, String type, String name, String poster, String posterShape,
			String background, String logo, String description, String releaseInfo,
			StremioDuration runtime, List<String> genres, String language,
			List<StremioVideo> videos) {
		this(id, type, name, poster, posterShape, background, logo, description, releaseInfo,
				null, runtime, genres, language, videos);
	}

	private static String required(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
		return value;
	}

	@Override
	public String toString() {
		return "StremioMeta[id=<redacted>, type=<redacted>, name=<redacted>, media=<redacted>, genres=" +
				genres.size() + ", videos=" + videos.size() + "]";
	}
}
