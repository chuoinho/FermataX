package me.aap.fermata.addon.stremio.ui.source;

import java.util.Objects;

/** Secret-free projection of one addon returned by an addon_catalog provider. */
public record SourceUiDiscoveryItem(
		String stableId,
		String name,
		String description,
		String version,
		boolean official,
		boolean protectedAddon,
		boolean configurable,
		boolean installed) {
	public SourceUiDiscoveryItem {
		stableId = text(stableId, "stableId");
		name = text(name, "name");
		description = Objects.requireNonNullElse(description, "").trim();
		version = Objects.requireNonNullElse(version, "").trim();
	}

	private static String text(String value, String field) {
		Objects.requireNonNull(value, field);
		String normalized = value.trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be empty");
		return normalized;
	}
}
