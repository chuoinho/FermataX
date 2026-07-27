package me.aap.fermata.addon.stremio.session;

/** Immutable batch projection for movie or episode progress. */
public record StremioProgressState(
		String stableId,
		long positionMs,
		long durationMs,
		boolean completed,
		long lastPlayedMs,
		long updatedMs) {

	public StremioProgressState {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		if ((positionMs < 0L) || (lastPlayedMs < 0L) || (updatedMs < 0L)) {
			throw new IllegalArgumentException("progress values cannot be negative");
		}
		if (durationMs < -1L) throw new IllegalArgumentException("durationMs is invalid");
	}

	public boolean resumable() {
		return !completed && (positionMs > 0L) && (durationMs > 0L) &&
				(positionMs < durationMs);
	}
}
