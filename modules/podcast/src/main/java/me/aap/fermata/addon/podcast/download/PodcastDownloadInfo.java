package me.aap.fermata.addon.podcast.download;

import androidx.annotation.Nullable;

/** Persisted validators needed to resume a partial Podcast download safely. */
public record PodcastDownloadInfo(@Nullable String etag,
		@Nullable String lastModified) {
	public static final PodcastDownloadInfo EMPTY = new PodcastDownloadInfo(null, null);
}
