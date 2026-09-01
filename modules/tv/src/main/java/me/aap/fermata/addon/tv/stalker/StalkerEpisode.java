package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

public record StalkerEpisode(String id, int number, String name, String command,
		String seriesNumber, @Nullable String logo, @Nullable String description) {
	public StalkerEpisode {
		if ((name == null) || name.isBlank()) {
			name = (number > 0) ? "Episode " + number : id;
		}
	}
}
