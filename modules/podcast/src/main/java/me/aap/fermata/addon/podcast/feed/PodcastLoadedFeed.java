package me.aap.fermata.addon.podcast.feed;

import androidx.annotation.Nullable;

import me.aap.fermata.addon.podcast.model.PodcastFeed;

public final class PodcastLoadedFeed {
	private final PodcastFeed feed;
	private final String finalUrl;
	private final String etag;
	private final String lastModified;
	private final boolean notModified;

	PodcastLoadedFeed(@Nullable PodcastFeed feed, String finalUrl, @Nullable String etag,
			@Nullable String lastModified, boolean notModified) {
		this.feed = feed;
		this.finalUrl = finalUrl;
		this.etag = etag;
		this.lastModified = lastModified;
		this.notModified = notModified;
	}

	@Nullable public PodcastFeed getFeed() { return feed; }
	public String getFinalUrl() { return finalUrl; }
	@Nullable public String getEtag() { return etag; }
	@Nullable public String getLastModified() { return lastModified; }
	public boolean isNotModified() { return notModified; }
}
