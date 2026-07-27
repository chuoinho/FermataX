package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

public record BrowseDetails(BrowseMedia media, List<BrowseSeason> seasons) {
	public BrowseDetails {
		media = Objects.requireNonNull(media, "media");
		seasons = List.copyOf(Objects.requireNonNull(seasons, "seasons"));
	}

	public boolean series() {
		return "series".equals(media.type()) || !seasons.isEmpty();
	}
}
