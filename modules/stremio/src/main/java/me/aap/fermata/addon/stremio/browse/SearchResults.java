package me.aap.fermata.addon.stremio.browse;

import java.util.List;
import java.util.Objects;

public record SearchResults(String query, List<BrowseMedia> items) {
	public SearchResults {
		Objects.requireNonNull(query, "query");
		items = List.copyOf(Objects.requireNonNull(items, "items"));
	}
}
