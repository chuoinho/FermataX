package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

public record BrowseSeason(int number, List<BrowseEpisode> episodes) {
	public BrowseSeason {
		if (number < 0) throw new IllegalArgumentException("number cannot be negative");
		episodes = List.copyOf(Objects.requireNonNull(episodes, "episodes"));
	}
}
