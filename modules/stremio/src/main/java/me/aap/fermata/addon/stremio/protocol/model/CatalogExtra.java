package me.aap.fermata.addon.stremio.protocol.model;

import java.util.List;
import java.util.Objects;

public record CatalogExtra(String name, boolean required, List<String> options, int optionsLimit) {
	public CatalogExtra {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
		options = List.copyOf(Objects.requireNonNull(options, "options"));
		if (optionsLimit < 1) throw new IllegalArgumentException("optionsLimit must be positive");
	}
}
