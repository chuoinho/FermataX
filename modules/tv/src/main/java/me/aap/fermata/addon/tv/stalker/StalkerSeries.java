package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

public record StalkerSeries(String id, String name, @Nullable String logo,
		@Nullable String description, @Nullable String categoryId) {
	public StalkerSeries {
		if ((name == null) || name.isBlank()) name = id;
	}
}
