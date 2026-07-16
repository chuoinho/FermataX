package me.aap.fermata.addon.podcast.model;

public record PodcastOpmlEntry(String title, String feedUrl) {
	public PodcastOpmlEntry {
		title = (title == null) ? "" : title.trim();
		feedUrl = (feedUrl == null) ? "" : feedUrl.trim();
	}
}
