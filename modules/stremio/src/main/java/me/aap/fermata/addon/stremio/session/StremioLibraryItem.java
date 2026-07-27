package me.aap.fermata.addon.stremio.session;

import java.util.Locale;
import java.util.Objects;

/** Favorite Library projection with optional progress from the same database snapshot. */
public record StremioLibraryItem(
		StremioSessionItem item,
		String mediaType,
		long favoriteUpdatedMs,
		StremioProgressState progress) {

	public StremioLibraryItem {
		Objects.requireNonNull(item, "item");
		mediaType = Objects.requireNonNull(mediaType, "mediaType")
				.strip().toLowerCase(Locale.ROOT);
		if (mediaType.isEmpty()) throw new IllegalArgumentException("mediaType cannot be blank");
		if (favoriteUpdatedMs < 0L) {
			throw new IllegalArgumentException("favoriteUpdatedMs cannot be negative");
		}
		if ((progress != null) && !item.stableId().equals(progress.stableId())) {
			throw new IllegalArgumentException("progress identity mismatch");
		}
	}

	public boolean isSeries() {
		return "series".equals(mediaType) || item.isEpisode();
	}
}
