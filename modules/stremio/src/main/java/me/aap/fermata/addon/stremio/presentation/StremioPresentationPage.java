package me.aap.fermata.addon.stremio.presentation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One immutable, generation-bound renderer payload. */
public record StremioPresentationPage(List<StremioUiModel> models,
		Map<String, StremioSelection> selections) {
	public StremioPresentationPage {
		models = List.copyOf(Objects.requireNonNull(models, "models"));
		selections = Map.copyOf(Objects.requireNonNull(selections, "selections"));
		for (var entry : selections.entrySet()) {
			if ((entry.getKey() == null) || entry.getKey().isBlank()) {
				throw new IllegalArgumentException("selection key must not be blank");
			}
			if (entry.getKey().contains("://")) {
				throw new IllegalArgumentException("selection key contains URL");
			}
			Objects.requireNonNull(entry.getValue(), "selection");
		}
	}

	public static StremioPresentationPage of(List<StremioUiModel> models) {
		return new StremioPresentationPage(models, Map.of());
	}
}
