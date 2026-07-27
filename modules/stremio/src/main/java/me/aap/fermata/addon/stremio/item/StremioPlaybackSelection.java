package me.aap.fermata.addon.stremio.item;

import java.util.Objects;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;

/** Ephemeral playback binding kept outside durable presentation state. */
public record StremioPlaybackSelection(String routeKey, BrowseMedia media,
		BrowseEpisode episode, BrowseSeason season, StreamAggregationRequest request,
		PlaybackDescriptor descriptor, long resumePositionMs) {
	public StremioPlaybackSelection(String routeKey, BrowseMedia media,
			BrowseEpisode episode, BrowseSeason season, StreamAggregationRequest request,
			PlaybackDescriptor descriptor) {
		this(routeKey, media, episode, season, request, descriptor, 0L);
	}

	public StremioPlaybackSelection {
		Objects.requireNonNull(routeKey, "routeKey");
		Objects.requireNonNull(media, "media");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(descriptor, "descriptor");
		if ((episode == null) != (season == null)) {
			throw new IllegalArgumentException("episode and season must be present together");
		}
		if (resumePositionMs < 0L) {
			throw new IllegalArgumentException("resume position must not be negative");
		}
	}
}
