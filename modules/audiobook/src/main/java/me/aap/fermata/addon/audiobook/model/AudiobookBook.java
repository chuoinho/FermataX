package me.aap.fermata.addon.audiobook.model;

import androidx.annotation.Nullable;

public final class AudiobookBook {
	private final String id;
	@Nullable private final String sourceId;
	@Nullable private final String remoteId;
	private final String title;
	private final String author;
	private final String narrator;
	private final String description;
	private final String artworkUrl;
	private final String language;
	private final long durationMs;
	@Nullable private final String progressChapterId;
	private final long progressMs;
	private final long lastPlayedMs;
	private final boolean finished;
	private final long addedMs;
	private final long updatedMs;

	public AudiobookBook(String id, @Nullable String sourceId, @Nullable String remoteId,
			String title, String author, String narrator, String description, String artworkUrl,
			String language, long durationMs, @Nullable String progressChapterId, long progressMs,
			long lastPlayedMs, boolean finished, long addedMs, long updatedMs) {
		this.id = id;
		this.sourceId = sourceId;
		this.remoteId = remoteId;
		this.title = title;
		this.author = author;
		this.narrator = narrator;
		this.description = description;
		this.artworkUrl = artworkUrl;
		this.language = language;
		this.durationMs = durationMs;
		this.progressChapterId = progressChapterId;
		this.progressMs = progressMs;
		this.lastPlayedMs = lastPlayedMs;
		this.finished = finished;
		this.addedMs = addedMs;
		this.updatedMs = updatedMs;
	}

	public String getId() { return id; }
	@Nullable public String getSourceId() { return sourceId; }
	@Nullable public String getRemoteId() { return remoteId; }
	public String getTitle() { return title; }
	public String getAuthor() { return author; }
	public String getNarrator() { return narrator; }
	public String getDescription() { return description; }
	public String getArtworkUrl() { return artworkUrl; }
	public String getLanguage() { return language; }
	public long getDurationMs() { return durationMs; }
	@Nullable public String getProgressChapterId() { return progressChapterId; }
	public long getProgressMs() { return progressMs; }
	public long getLastPlayedMs() { return lastPlayedMs; }
	public boolean isFinished() { return finished; }
	public long getAddedMs() { return addedMs; }
	public long getUpdatedMs() { return updatedMs; }
}
