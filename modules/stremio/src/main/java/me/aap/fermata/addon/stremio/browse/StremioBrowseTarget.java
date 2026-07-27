package me.aap.fermata.addon.stremio.browse;

import java.util.Objects;

/** DB-restorable content target used by native navigation without provider-tree traversal. */
public record StremioBrowseTarget(
		BrowseMedia media, BrowseEpisode episode, BrowseSeason season) {
	public StremioBrowseTarget {
		Objects.requireNonNull(media, "media");
		if ((episode == null) != (season == null)) {
			throw new IllegalArgumentException("episode and season must be present together");
		}
	}
}
