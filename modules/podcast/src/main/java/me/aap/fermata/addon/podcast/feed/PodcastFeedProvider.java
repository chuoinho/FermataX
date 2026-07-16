package me.aap.fermata.addon.podcast.feed;

import me.aap.utils.async.FutureSupplier;

public interface PodcastFeedProvider {
	FutureSupplier<PodcastLoadedFeed> load(PodcastFeedRequest request);
}
