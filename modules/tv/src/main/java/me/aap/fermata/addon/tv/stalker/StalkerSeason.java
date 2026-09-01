package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

import java.util.List;

public record StalkerSeason(String id, int number, String name, @Nullable String logo,
		@Nullable String description, List<StalkerEpisode> episodes) {
	public StalkerSeason {
		if ((name == null) || name.isBlank()) {
			name = (number > 0) ? "Season " + number : "Season";
		}
		episodes = List.copyOf(episodes);
	}
}
