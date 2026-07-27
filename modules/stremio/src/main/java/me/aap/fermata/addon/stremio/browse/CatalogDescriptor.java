package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.addon.stremio.protocol.model.CatalogExtra;

public record CatalogDescriptor(
		CatalogRoute route,
		String providerName,
		String name,
		List<String> genres,
		boolean searchable,
		int providerPosition,
		int catalogPosition,
		List<CatalogExtra> extras) {
	public CatalogDescriptor(CatalogRoute route, String providerName, String name,
			List<String> genres, boolean searchable, int providerPosition,
			int catalogPosition) {
		this(route, providerName, name, genres, searchable, providerPosition,
				catalogPosition, List.of());
	}

	public CatalogDescriptor {
		route = Objects.requireNonNull(route, "route");
		providerName = Objects.requireNonNull(providerName, "providerName");
		name = Objects.requireNonNull(name, "name");
		genres = List.copyOf(Objects.requireNonNull(genres, "genres"));
		extras = List.copyOf(Objects.requireNonNull(extras, "extras"));
	}

	public boolean invocableWithoutRequiredExtras() {
		return extras.stream().noneMatch(CatalogExtra::required);
	}
}
