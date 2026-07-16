package me.aap.fermata.addon.podcast.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public final class PodcastSearchResult {
	private final String provider;
	private final String providerId;
	private final String title;
	private final String author;
	private final String description;
	private final String feedUrl;
	private final String artworkUrl;
	private final String websiteUrl;
	private final String language;
	private final int episodeCount;
	private final boolean explicit;

	private PodcastSearchResult(String provider, String providerId, String title, String author,
			String description, String feedUrl, String artworkUrl, String websiteUrl,
			String language, int episodeCount, boolean explicit) {
		this.provider = provider;
		this.providerId = providerId;
		this.title = title;
		this.author = author;
		this.description = description;
		this.feedUrl = feedUrl;
		this.artworkUrl = artworkUrl;
		this.websiteUrl = websiteUrl;
		this.language = language;
		this.episodeCount = Math.max(episodeCount, 0);
		this.explicit = explicit;
	}

	@Nullable
	public static PodcastSearchResult create(String provider, String providerId, String title,
			String author, String description, String feedUrl, String artworkUrl,
			String websiteUrl, String language, int episodeCount, boolean explicit) {
		String normalizedFeed = PodcastUrls.normalizeHttpUrl(feedUrl);
		title = text(title, 500);
		if (title.isEmpty() || (normalizedFeed == null)) return null;

		provider = text(provider, 32).toLowerCase(java.util.Locale.ROOT);
		if (provider.isEmpty()) return null;
		providerId = text(providerId, 256);
		if (providerId.isEmpty()) providerId = PodcastIds.hash(normalizedFeed);
		return new PodcastSearchResult(provider, providerId, title, text(author, 500),
				text(description, 4000), normalizedFeed, PodcastUrls.normalizeHttpUrl(artworkUrl),
				PodcastUrls.normalizeHttpUrl(websiteUrl), text(language, 32), episodeCount, explicit);
	}

	@NonNull
	public String getProvider() {
		return provider;
	}

	@NonNull
	public String getProviderId() {
		return providerId;
	}

	@NonNull
	public String getTitle() {
		return title;
	}

	@NonNull
	public String getAuthor() {
		return author;
	}

	@NonNull
	public String getDescription() {
		return description;
	}

	@NonNull
	public String getFeedUrl() {
		return feedUrl;
	}

	@Nullable
	public String getArtworkUrl() {
		return artworkUrl;
	}

	@Nullable
	public String getWebsiteUrl() {
		return websiteUrl;
	}

	@NonNull
	public String getLanguage() {
		return language;
	}

	public int getEpisodeCount() {
		return episodeCount;
	}

	public boolean isExplicit() {
		return explicit;
	}

	@NonNull
	public String dedupeKey() {
		return PodcastUrls.identity(feedUrl);
	}

	@NonNull
	public String itemKey() {
		return provider + ':' + PodcastIds.hash(providerId + '\n' + feedUrl);
	}

	@NonNull
	@Override
	public String toString() {
		return "PodcastSearchResult{" + provider + ':' + providerId + ", title=" + title + '}';
	}

	private static String text(String value, int limit) {
		if (value == null) return "";
		value = value.trim();
		return (value.length() <= limit) ? value : value.substring(0, limit).trim();
	}
}
