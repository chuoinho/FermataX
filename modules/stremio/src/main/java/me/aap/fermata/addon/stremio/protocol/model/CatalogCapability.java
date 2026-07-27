package me.aap.fermata.addon.stremio.protocol.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CatalogCapability(String type, String id, String name, List<CatalogExtra> extras) {
	public CatalogCapability {
		type = requireText(type, "type");
		id = requireText(id, "id");
		if ((name != null) && name.isBlank()) name = null;
		extras = List.copyOf(Objects.requireNonNull(extras, "extras"));
	}

	public String displayName() {
		return Optional.ofNullable(name).orElse(id);
	}

	public Optional<CatalogExtra> extra(String extraName) {
		return extras.stream().filter(extra -> extra.name().equals(extraName)).findFirst();
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
