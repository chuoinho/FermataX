package me.aap.fermata.addon.podcast.download;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.utils.async.FutureSupplier;

/** Persistence boundary used to sequence Podcast download state changes. */
public interface PodcastDownloadStore {
	PodcastPlaybackSource resolveNetworkPlayback(PodcastEpisodeRecord episode) throws Exception;

	FutureSupplier<PodcastDownloadInfo> getDownloadInfo(String feedKey, String episodeKey);

	FutureSupplier<Void> updateDownload(String feedKey, String episodeKey, int state,
			@Nullable String localPath, @Nullable String tempPath, long downloaded, long total,
			@Nullable String etag, @Nullable String lastModified, @Nullable String errorCode);

	FutureSupplier<Void> deleteDownloadState(String feedKey, String episodeKey);
}
