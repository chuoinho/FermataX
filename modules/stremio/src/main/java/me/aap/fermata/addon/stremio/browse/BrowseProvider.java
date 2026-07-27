package me.aap.fermata.addon.stremio.browse;

import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;

public record BrowseProvider(
		String sourceUuid,
		String displayName,
		StremioManifest manifest,
		boolean enabled,
		int position) {
	public BrowseProvider {
		sourceUuid = requireText(sourceUuid, "sourceUuid");
		displayName = requireText(displayName, "displayName");
		manifest = Objects.requireNonNull(manifest, "manifest");
		if (position < 0) throw new IllegalArgumentException("position cannot be negative");
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
