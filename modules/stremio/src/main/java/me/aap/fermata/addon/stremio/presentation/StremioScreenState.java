package me.aap.fermata.addon.stremio.presentation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StremioScreenState(long generation, StremioRoute route, Phase phase,
		List<StremioUiModel> models, Map<String, StremioSelection> selections, String message,
		StremioViewportState viewport) {
	public StremioScreenState {
		if (generation < 0L) throw new IllegalArgumentException("generation must not be negative");
		Objects.requireNonNull(route, "route");
		Objects.requireNonNull(phase, "phase");
		models = List.copyOf(models);
		selections = Map.copyOf(selections);
		message = Objects.requireNonNullElse(message, "");
		Objects.requireNonNull(viewport, "viewport");
		if ((phase == Phase.CONTENT) && models.isEmpty()) {
			throw new IllegalArgumentException("content state requires models");
		}
	}

	public StremioScreenState(long generation, StremioRoute route, Phase phase,
			List<StremioUiModel> models, String message) {
		this(generation, route, phase, models, Map.of(), message,
				StremioViewportState.empty());
	}

	public StremioScreenState(long generation, StremioRoute route, Phase phase,
			List<StremioUiModel> models, Map<String, StremioSelection> selections,
			String message) {
		this(generation, route, phase, models, selections, message,
				StremioViewportState.empty());
	}

	public enum Phase { LOADING, CONTENT, EMPTY, ERROR }
}
