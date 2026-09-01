package me.aap.fermata.addon.tv.stalker;

public record StalkerCategory(String id, String name) {
	public StalkerCategory {
		if ((name == null) || name.isBlank()) name = id;
	}
}
