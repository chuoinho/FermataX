package me.aap.fermata.addon.stremio.session;

/**
 * A core-authorized progress write. Completion and checkpoint timing remain owned by
 * PlaybackProgressPolicy; this model only preserves item/generation ownership.
 */
public record StremioProgressSnapshot(
		String stableId,
		long playbackGeneration,
		long ownershipToken,
		long positionMs,
		boolean completed,
		long updatedAtMs) {

	public StremioProgressSnapshot {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		if ((playbackGeneration < 0L) || (ownershipToken <= 0L)) {
			throw new IllegalArgumentException("invalid progress ownership");
		}
		if ((positionMs < 0L) || (updatedAtMs < 0L)) {
			throw new IllegalArgumentException("progress values cannot be negative");
		}
		if (completed && (positionMs != 0L)) {
			throw new IllegalArgumentException("core must normalize completed progress to zero");
		}
	}
}
