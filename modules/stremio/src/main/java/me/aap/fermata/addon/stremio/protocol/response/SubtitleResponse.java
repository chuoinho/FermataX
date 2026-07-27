package me.aap.fermata.addon.stremio.protocol.response;

import java.util.List;
import java.util.Objects;

public record SubtitleResponse(List<StremioSubtitle> subtitles) {
	public SubtitleResponse {
		subtitles = List.copyOf(Objects.requireNonNull(subtitles, "subtitles"));
	}

	@Override
	public String toString() {
		return "SubtitleResponse[subtitles=" + subtitles.size() + "]";
	}
}
