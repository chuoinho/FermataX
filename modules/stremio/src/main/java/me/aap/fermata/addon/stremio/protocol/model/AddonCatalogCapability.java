package me.aap.fermata.addon.stremio.protocol.model;

import java.util.Objects;

public record AddonCatalogCapability(String type, String id, String name) {
	public AddonCatalogCapability {
		if (Objects.requireNonNull(type, "type").isBlank()) {
			throw new IllegalArgumentException("type cannot be blank");
		}
		if (Objects.requireNonNull(id, "id").isBlank()) {
			throw new IllegalArgumentException("id cannot be blank");
		}
		if ((name != null) && name.isBlank()) name = null;
	}
}
