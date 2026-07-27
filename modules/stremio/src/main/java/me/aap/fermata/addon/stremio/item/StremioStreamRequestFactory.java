package me.aap.fermata.addon.stremio.item;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.ContentIdentitySet;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;

/** One request factory shared by the compatibility item tree and native presentation layer. */
public final class StremioStreamRequestFactory {
	private StremioStreamRequestFactory() {
	}

	public static StreamAggregationRequest create(
			BrowseMedia media, @Nullable BrowseEpisode episode) {
		String videoId = (episode == null) ? media.id() : episode.videoId();
		String title = (episode == null) ? media.title() : episode.title();
		String artwork = (episode != null) && (episode.thumbnail() != null) &&
				!episode.thumbnail().isBlank() ? episode.thumbnail() :
				StremioMetaItem.preferredArtwork(media);
		long duration = durationMillis((episode == null) ? media.duration() : episode.duration());
		StremioCanonicalIdentity canonical =
				StremioCanonicalIdentity.from(media.type(), media.id());
		StremioPlaybackIdentity identity = (canonical == null) ?
				StremioPlaybackIdentity.scoped(media.sourceUuid(), media.type(), media.id(), videoId) :
				canonical.playbackIdentity(media.type(), videoId,
						(episode == null) ? -1 : episode.season(),
						(episode == null) ? -1 : episode.episode());
		return new StreamAggregationRequest(identity, media.type(), media.id(), videoId,
				new StremioPlaybackMetadata(title, artwork, duration),
				(episode == null) ? -1 : episode.season(),
				(episode == null) ? -1 : episode.episode(),
				ContentIdentitySet.from(media.sourceUuid(), media.type(), media.id(), videoId,
						(episode == null) ? -1 : episode.season(),
						(episode == null) ? -1 : episode.episode()));
	}

	private static long durationMillis(@Nullable StremioDuration duration) {
		return (duration == null) ? StremioPlaybackMetadata.UNKNOWN_DURATION :
				duration.milliseconds();
	}
}
