package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

public record StremioProgressRecord(
		String videoKey,
		long positionMs,
		long durationMs,
		boolean completed,
		long lastPlayedMs,
		long updatedMs) {

	public StremioProgressRecord {
		Objects.requireNonNull(videoKey, "videoKey");
		if (positionMs < 0) throw new IllegalArgumentException("positionMs must be non-negative");
	}
}
