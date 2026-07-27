package me.aap.fermata.addon.stremio.session;

public record StremioFavoriteUpdate(
		String stableId,
		String canonicalContentKey,
		boolean favorite) {

	public StremioFavoriteUpdate {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		canonicalContentKey = StremioSessionIds.requireOpaque(
				canonicalContentKey, "canonicalContentKey");
	}
}
