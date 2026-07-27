package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mutable construction detail; only immutable pages cross the renderer boundary. */
final class StremioPresentationPageBuilder {
	final List<StremioUiModel> models = new ArrayList<>();
	final Map<String, StremioSelection> selections = new LinkedHashMap<>();

	StremioPresentationPageBuilder() {
	}

	StremioPresentationPageBuilder(StremioPresentationPageBuilder source) {
		models.addAll(source.models);
		selections.putAll(source.selections);
	}

	void add(StremioUiModel model) {
		models.add(Objects.requireNonNull(model, "model"));
	}

	void add(StremioUiModel model, StremioSelection selection) {
		add(model);
		selections.put(model.stableKey(), Objects.requireNonNull(selection, "selection"));
	}

	boolean isEmpty() {
		return models.isEmpty();
	}

	StremioPresentationPage build() {
		return new StremioPresentationPage(models, selections);
	}
}
