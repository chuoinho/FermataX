package me.aap.fermata.addon.stremio.item;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;

/** Canonical destination for durable Stremio content identities. */
public enum StremioNavigationTarget {
	DETAILS,
	STREAMS;

	public static StremioNavigationTarget forContent(
			BrowseMedia media, @Nullable BrowseEpisode episode) {
		if ((episode == null) && "series".equalsIgnoreCase(media.type())) return DETAILS;
		return STREAMS;
	}
}
