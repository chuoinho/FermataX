package me.aap.fermata.addon.podcast.data;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.podcast.model.PodcastEpisode;

final class PodcastStoredEpisode {
	final PodcastEpisode episode;
	final String mediaUrl;
	final String mediaCredentialRef;
	final String artworkUrl;
	final String artworkCredentialRef;

	PodcastStoredEpisode(PodcastEpisode episode, String mediaUrl,
			@Nullable String mediaCredentialRef, String artworkUrl,
			@Nullable String artworkCredentialRef) {
		this.episode = episode;
		this.mediaUrl = mediaUrl;
		this.mediaCredentialRef = mediaCredentialRef;
		this.artworkUrl = artworkUrl;
		this.artworkCredentialRef = artworkCredentialRef;
	}
}
