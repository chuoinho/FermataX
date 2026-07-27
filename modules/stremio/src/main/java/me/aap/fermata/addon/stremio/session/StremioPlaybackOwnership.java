package me.aap.fermata.addon.stremio.session;

/** Capability token bound to one media-session item generation. */
public record StremioPlaybackOwnership(
		String stableId,
		long playbackGeneration,
		long ownershipToken) {

	public StremioPlaybackOwnership {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		if ((playbackGeneration < 0L) || (ownershipToken <= 0L)) {
			throw new IllegalArgumentException("invalid playback ownership");
		}
	}
}
