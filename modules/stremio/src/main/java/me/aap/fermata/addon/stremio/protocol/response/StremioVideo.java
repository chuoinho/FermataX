package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record StremioVideo(
		String id,
		String title,
		Integer season,
		Integer episode,
		String released,
		String thumbnail,
		String overview,
		StremioDuration duration) {
	public StremioVideo {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(title, "title");
		if (id.isBlank() || title.isBlank()) {
			throw new IllegalArgumentException("id and title cannot be blank");
		}
		if ((season != null) && (season < 0)) throw new IllegalArgumentException("Invalid season");
		if ((episode != null) && (episode < 0)) throw new IllegalArgumentException("Invalid episode");
	}

	@Override
	public String toString() {
		return "StremioVideo[id=<redacted>, title=<redacted>, season=" + season +
				", episode=" + episode + ", media=<redacted>]";
	}
}
