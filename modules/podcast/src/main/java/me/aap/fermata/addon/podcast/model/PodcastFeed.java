package me.aap.fermata.addon.podcast.model;

import androidx.annotation.NonNull;

import java.util.List;

public final class PodcastFeed {
	private final String title;
	private final String author;
	private final String description;
	private final String artworkUrl;
	private final String websiteUrl;
	private final String selfUrl;
	private final String language;
	private final boolean explicit;
	private final List<PodcastEpisode> episodes;

	public PodcastFeed(String title, String author, String description, String artworkUrl,
			String websiteUrl, String selfUrl, String language, boolean explicit,
			List<PodcastEpisode> episodes) {
		this.title = text(title, 1000);
		this.author = text(author, 1000);
		this.description = text(description, 64 * 1024);
		this.artworkUrl = text(artworkUrl, 16 * 1024);
		this.websiteUrl = text(websiteUrl, 16 * 1024);
		this.selfUrl = text(selfUrl, 16 * 1024);
		this.language = text(language, 64);
		this.explicit = explicit;
		this.episodes = List.copyOf(episodes);
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
	public String getArtworkUrl() {
		return artworkUrl;
	}

	@NonNull
	public String getWebsiteUrl() {
		return websiteUrl;
	}

	@NonNull
	public String getSelfUrl() {
		return selfUrl;
	}

	@NonNull
	public String getLanguage() {
		return language;
	}

	public boolean isExplicit() {
		return explicit;
	}

	@NonNull
	public List<PodcastEpisode> getEpisodes() {
		return episodes;
	}

	private static String text(String value, int max) {
		if (value == null) return "";
		value = value.trim();
		return (value.length() <= max) ? value : value.substring(0, max).trim();
	}
}
