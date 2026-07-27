package me.aap.fermata.addon.stremio.protocol.response;

import java.util.Objects;

public record StremioSubtitle(String id, String url, String language) {
	public StremioSubtitle {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(language, "language");
		if (id.isBlank() || url.isBlank() || language.isBlank()) {
			throw new IllegalArgumentException("Subtitle fields cannot be blank");
		}
	}

	@Override
	public String toString() {
		return "StremioSubtitle[id=<redacted>, url=<redacted>, language=<redacted>]";
	}
}
