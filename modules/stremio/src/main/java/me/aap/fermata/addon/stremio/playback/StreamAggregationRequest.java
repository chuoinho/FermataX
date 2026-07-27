package me.aap.fermata.addon.stremio.playback;

import java.util.Objects;

/** Resource key and immutable metadata needed to fetch stream choices. */
public record StreamAggregationRequest(
		StremioPlaybackIdentity identity,
		String type,
		String contentId,
		String videoId,
		StremioPlaybackMetadata metadata,
		int seasonNumber,
		int episodeNumber,
		ContentIdentitySet identities) {

	public StreamAggregationRequest(StremioPlaybackIdentity identity, String type,
			String contentId, String videoId, StremioPlaybackMetadata metadata) {
		this(identity, type, contentId, videoId, metadata, -1, -1);
	}

	public StreamAggregationRequest(StremioPlaybackIdentity identity, String type,
			String contentId, String videoId, StremioPlaybackMetadata metadata,
			int seasonNumber, int episodeNumber) {
		this(identity, type, contentId, videoId, metadata, seasonNumber, episodeNumber,
				ContentIdentitySet.legacy(type, videoId));
	}

	public StreamAggregationRequest {
		Objects.requireNonNull(identity, "identity");
		type = StremioPlaybackIdentity.requireText(type, "type");
		contentId = StremioPlaybackIdentity.requireText(contentId, "contentId");
		videoId = StremioPlaybackIdentity.requireText(videoId, "videoId");
		Objects.requireNonNull(metadata, "metadata");
		Objects.requireNonNull(identities, "identities");
		if (!identities.type().equalsIgnoreCase(type)) {
			throw new IllegalArgumentException("identity type does not match request type");
		}
		if (((seasonNumber < 0) || (episodeNumber < 0)) &&
				((seasonNumber != -1) || (episodeNumber != -1))) {
			throw new IllegalArgumentException("episode coordinates must both be present");
		}
	}

	public boolean isEpisode() {
		return seasonNumber >= 0;
	}

	@Override
	public String toString() {
		return "StreamAggregationRequest{identity=" + identity +
				", resource=<redacted>, metadata=" + metadata + '}';
	}
}
