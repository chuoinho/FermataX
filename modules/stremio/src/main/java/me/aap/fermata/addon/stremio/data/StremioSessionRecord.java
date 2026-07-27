package me.aap.fermata.addon.stremio.data;

import java.util.Objects;

/** Minimal durable pointer used to restore the active Stremio video after process death. */
public record StremioSessionRecord(
		String videoKey,
		String backToListId,
		long playbackGeneration,
		long updatedMs) {

	public StremioSessionRecord {
		Objects.requireNonNull(videoKey, "videoKey");
		Objects.requireNonNull(backToListId, "backToListId");
		if (playbackGeneration < 0L) {
			throw new IllegalArgumentException("playbackGeneration cannot be negative");
		}
		if (updatedMs < 0L) throw new IllegalArgumentException("updatedMs cannot be negative");
	}
}
