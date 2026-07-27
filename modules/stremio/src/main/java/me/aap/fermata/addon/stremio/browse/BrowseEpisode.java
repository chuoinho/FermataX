package me.aap.fermata.addon.stremio.browse;

import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;
import me.aap.fermata.addon.stremio.security.ArtworkUrlSanitizer;

public record BrowseEpisode(
		String sourceUuid,
		String seriesType,
		String seriesId,
		String videoId,
		String title,
		int season,
		int episode,
		String released,
		String thumbnail,
		String overview,
		StremioDuration duration) {
	public BrowseEpisode {
		Objects.requireNonNull(sourceUuid, "sourceUuid");
		Objects.requireNonNull(seriesType, "seriesType");
		Objects.requireNonNull(seriesId, "seriesId");
		Objects.requireNonNull(videoId, "videoId");
		Objects.requireNonNull(title, "title");
		thumbnail = ArtworkUrlSanitizer.sanitize(thumbnail);
		if ((season < 0) || (episode < 0)) {
			throw new IllegalArgumentException("season and episode cannot be negative");
		}
	}

	public String scopedId() {
		return sourceUuid + ':' + seriesType + ':' + seriesId + ':' + videoId;
	}

	@Override
	public String toString() {
		return "BrowseEpisode[sourceUuid=" + sourceUuid + ", seriesType=" + seriesType +
				", identity=<redacted>, title=<redacted>, season=" + season +
				", episode=" + episode + ", media=<redacted>]";
	}
}
