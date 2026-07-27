package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

public record CatalogPage(
		CatalogDescriptor catalog,
		String genre,
		int skip,
		int nextSkip,
		boolean hasNext,
		List<BrowseMedia> items) {
	public CatalogPage {
		catalog = Objects.requireNonNull(catalog, "catalog");
		if ((skip < 0) || (nextSkip < skip)) throw new IllegalArgumentException("Invalid page offsets");
		items = List.copyOf(Objects.requireNonNull(items, "items"));
	}
}
