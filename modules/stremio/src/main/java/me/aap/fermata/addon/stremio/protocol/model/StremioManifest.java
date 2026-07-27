package me.aap.fermata.addon.stremio.protocol.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StremioManifest(
		String id,
		String name,
		String description,
		String version,
		List<String> types,
		PrefixConstraint idPrefixes,
		List<ResourceCapability> resources,
		List<CatalogCapability> catalogs,
		List<AddonCatalogCapability> addonCatalogs,
		ManifestBehaviorHints behaviorHints) {

	public StremioManifest(String id, String name, String description, String version,
			List<String> types, PrefixConstraint idPrefixes,
			List<ResourceCapability> resources, List<CatalogCapability> catalogs,
			ManifestBehaviorHints behaviorHints) {
		this(id, name, description, version, types, idPrefixes, resources, catalogs,
				List.of(), behaviorHints);
	}

	public StremioManifest {
		id = requireText(id, "id");
		name = requireText(name, "name");
		description = Objects.requireNonNullElse(description, "").trim();
		version = requireText(version, "version");
		types = List.copyOf(Objects.requireNonNull(types, "types"));
		if (types.isEmpty()) throw new IllegalArgumentException("types cannot be empty");
		idPrefixes = Objects.requireNonNull(idPrefixes, "idPrefixes");
		resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
		if (resources.isEmpty()) throw new IllegalArgumentException("resources cannot be empty");
		catalogs = List.copyOf(Objects.requireNonNull(catalogs, "catalogs"));
		addonCatalogs = List.copyOf(Objects.requireNonNull(addonCatalogs, "addonCatalogs"));
		behaviorHints = Objects.requireNonNull(behaviorHints, "behaviorHints");
	}

	public Optional<CatalogCapability> catalog(String type, String catalogId) {
		return catalogs.stream()
				.filter(catalog -> catalog.type().equals(type) && catalog.id().equals(catalogId))
				.findFirst();
	}

	private static String requireText(String value, String field) {
		Objects.requireNonNull(value, field);
		if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
		return value;
	}
}
