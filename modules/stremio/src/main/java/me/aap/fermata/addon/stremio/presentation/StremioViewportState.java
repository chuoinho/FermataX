package me.aap.fermata.addon.stremio.presentation;

import java.util.Map;
import java.util.Objects;

/** Focus and scroll restoration owned by one Stremio route. */
public record StremioViewportState(String focusedKey, int verticalPosition, int verticalOffset,
		Map<String, Integer> horizontalPositions) {
	public StremioViewportState(String focusedKey, int verticalPosition,
			Map<String, Integer> horizontalPositions) {
		this(focusedKey, verticalPosition, 0, horizontalPositions);
	}

	public StremioViewportState {
		focusedKey = Objects.requireNonNullElse(focusedKey, "");
		if (focusedKey.contains("://")) {
			throw new IllegalArgumentException("focused key contains URL");
		}
		if (verticalPosition < 0) {
			throw new IllegalArgumentException("vertical position must not be negative");
		}
		horizontalPositions = Map.copyOf(horizontalPositions);
		for (var entry : horizontalPositions.entrySet()) {
			if ((entry.getKey() == null) || entry.getKey().isBlank() ||
					entry.getKey().contains("://") || (entry.getValue() == null) ||
					(entry.getValue() < 0)) {
				throw new IllegalArgumentException("invalid horizontal viewport entry");
			}
		}
	}

	public static StremioViewportState empty() {
		return new StremioViewportState("", 0, 0, Map.of());
	}
}
