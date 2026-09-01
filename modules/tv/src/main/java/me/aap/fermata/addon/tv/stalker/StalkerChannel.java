package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

public record StalkerChannel(String id, String name, @Nullable String logo,
		@Nullable String categoryId, String command) {
	public StalkerChannel {
		if ((name == null) || name.isBlank()) name = id;
	}
}
