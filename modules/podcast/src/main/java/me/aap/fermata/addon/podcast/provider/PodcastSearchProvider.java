package me.aap.fermata.addon.podcast.provider;

import java.util.List;

import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.utils.async.FutureSupplier;

public interface PodcastSearchProvider {
	String getId();

	FutureSupplier<List<PodcastSearchResult>> search(PodcastSearchRequest request);
}
