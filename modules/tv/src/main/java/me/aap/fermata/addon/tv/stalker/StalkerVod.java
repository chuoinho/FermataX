package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

public record StalkerVod(String id, String name, @Nullable String logo,
		@Nullable String description, @Nullable String categoryId, String command) {
	public StalkerVod {
		if ((name == null) || name.isBlank()) name = id;
	}
}
