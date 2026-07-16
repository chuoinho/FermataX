package me.aap.fermata.addon.podcast.model;

import androidx.annotation.Nullable;

public final class PodcastEpisodeRecord {
	private final String feedKey;
	private final String episodeKey;
	private final String feedTitle;
	private final String title;
	private final String description;
	private final String author;
	private final String mediaUrl;
	private final String mediaCredentialRef;
	private final String mimeType;
	private final String artworkUrl;
	private final String artworkCredentialRef;
	private final long publicationMs;
	private final long durationMs;
	private final long mediaLength;
	private final boolean played;
	private final long progressMs;
	private final long lastPlayedMs;
	private final int downloadState;
	private final String localPath;

	public PodcastEpisodeRecord(String feedKey, String episodeKey, String feedTitle, String title,
			String description, String author, String mediaUrl, @Nullable String mediaCredentialRef,
			String mimeType, String artworkUrl, @Nullable String artworkCredentialRef,
			long publicationMs, long durationMs, long mediaLength, boolean played,
			long progressMs, long lastPlayedMs, int downloadState, @Nullable String localPath) {
		this.feedKey = feedKey;
		this.episodeKey = episodeKey;
		this.feedTitle = feedTitle;
		this.title = title;
		this.description = description;
		this.author = author;
		this.mediaUrl = mediaUrl;
		this.mediaCredentialRef = mediaCredentialRef;
		this.mimeType = mimeType;
		this.artworkUrl = artworkUrl;
		this.artworkCredentialRef = artworkCredentialRef;
		this.publicationMs = publicationMs;
		this.durationMs = durationMs;
		this.mediaLength = mediaLength;
		this.played = played;
		this.progressMs = progressMs;
		this.lastPlayedMs = lastPlayedMs;
		this.downloadState = downloadState;
		this.localPath = localPath;
	}

	public String getFeedKey() { return feedKey; }
	public String getEpisodeKey() { return episodeKey; }
	public String getFeedTitle() { return feedTitle; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public String getAuthor() { return author; }
	public String getMediaUrl() { return mediaUrl; }
	@Nullable public String getMediaCredentialRef() { return mediaCredentialRef; }
	public String getMimeType() { return mimeType; }
	public String getArtworkUrl() { return artworkUrl; }
	@Nullable public String getArtworkCredentialRef() { return artworkCredentialRef; }
	public long getPublicationMs() { return publicationMs; }
	public long getDurationMs() { return durationMs; }
	public long getMediaLength() { return mediaLength; }
	public boolean isPlayed() { return played; }
	public long getProgressMs() { return progressMs; }
	public long getLastPlayedMs() { return lastPlayedMs; }
	public int getDownloadState() { return downloadState; }
	@Nullable public String getLocalPath() { return localPath; }
}
