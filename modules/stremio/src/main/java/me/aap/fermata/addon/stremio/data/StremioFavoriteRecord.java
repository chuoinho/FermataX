package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

/** Durable mirror entry whose ownership remains with Unified Favorites. */
public record StremioFavoriteRecord(String stableId, long updatedMs) {
	public StremioFavoriteRecord {
		Objects.requireNonNull(stableId, "stableId");
		if (updatedMs < 0L) throw new IllegalArgumentException("updatedMs cannot be negative");
	}
}
