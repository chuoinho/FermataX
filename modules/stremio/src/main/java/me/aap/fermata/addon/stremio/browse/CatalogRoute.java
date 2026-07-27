package me.aap.fermata.addon.stremio.browse;

import java.util.Objects;

public record CatalogRoute(String sourceUuid, String type, String catalogId) {
	public CatalogRoute {
		sourceUuid = requireText(sourceUuid, "sourceUuid");
		type = requireText(type, "type");
		catalogId = requireText(catalogId, "catalogId");
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
