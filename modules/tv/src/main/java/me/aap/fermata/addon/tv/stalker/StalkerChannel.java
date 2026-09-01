package me.aap.fermata.addon.tv.stalker;

import androidx.annotation.Nullable;

public record StalkerChannel(String id, String name, @Nullable String logo,
		@Nullable String categoryId, String command, int catchupDays) {
	public StalkerChannel {
		if ((name == null) || name.isBlank()) name = id;
		if (catchupDays < 0) catchupDays = 0;
	}

	public StalkerChannel(String id, String name, @Nullable String logo,
			@Nullable String categoryId, String command) {
		this(id, name, logo, categoryId, command, 0);
	}
}
