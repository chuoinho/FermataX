package me.aap.fermata.addon.stremio.data;

import java.util.List;
import java.util.Objects;

/** Consistent DB snapshot used to assemble the native Library without per-row queries. */
public record StremioLibraryData(
		List<StremioFavoriteRecord> favorites,
		StremioSessionData session) {

	public StremioLibraryData {
		favorites = List.copyOf(Objects.requireNonNull(favorites, "favorites"));
		Objects.requireNonNull(session, "session");
	}
}
