package me.aap.fermata.addon.stremio.session;

/** Durable current-item pointer; metadata is resolved from the DB after process death. */
public record StremioRestorePoint(
		String stableId,
		String backToListId,
		long playbackGeneration,
		long updatedAtMs) {

	public StremioRestorePoint {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		backToListId = StremioSessionIds.requireOpaque(backToListId, "backToListId");
		if (playbackGeneration < 0L) {
			throw new IllegalArgumentException("playbackGeneration cannot be negative");
		}
		if (updatedAtMs < 0L) throw new IllegalArgumentException("updatedAtMs cannot be negative");
	}
}
