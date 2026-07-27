package me.aap.fermata.addon.stremio.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record StremioRequest(String resource, String type, String id, Map<String, ?> extras) {
	public StremioRequest {
		resource = requireText(resource, "resource");
		type = requireText(type, "type");
		id = requireText(id, "id");
		extras = Collections.unmodifiableMap(
				new LinkedHashMap<>(Objects.requireNonNull(extras, "extras")));
	}

	public StremioRequest(String resource, String type, String id) {
		this(resource, type, id, Map.of());
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
