package me.aap.fermata.addon.stremio.integration;

import java.util.List;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

/** Internal projection joining durable session identity with browsable metadata. */
record StremioPersistedItem(
		StremioSessionItem item,
		String type,
		String contentId,
		String videoId,
		BrowseMedia media,
		BrowseEpisode episode,
		List<BrowseEpisode> siblings) {
	StremioPersistedItem(StremioSessionItem item, String type, String contentId,
			String videoId, BrowseMedia media, BrowseEpisode episode) {
		this(item, type, contentId, videoId, media, episode,
				(episode == null) ? List.of() : List.of(episode));
	}
}
