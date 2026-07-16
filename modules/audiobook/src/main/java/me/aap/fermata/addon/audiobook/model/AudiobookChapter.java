package me.aap.fermata.addon.audiobook.model;

import androidx.annotation.Nullable;

public final class AudiobookChapter {
	private final String bookId;
	private final String id;
	private final int index;
	private final String title;
	private final String mediaUrl;
	private final String mimeType;
	private final long offsetMs;
	private final long bookOffsetMs;
	private final long durationMs;
	private final boolean segment;
	@Nullable private final String localPath;
	private final int downloadState;

	public AudiobookChapter(String bookId, String id, int index, String title, String mediaUrl,
			String mimeType, long offsetMs, long bookOffsetMs, long durationMs, boolean segment,
			@Nullable String localPath,
			int downloadState) {
		this.bookId = bookId;
		this.id = id;
		this.index = index;
		this.title = title;
		this.mediaUrl = mediaUrl;
		this.mimeType = mimeType;
		this.offsetMs = offsetMs;
		this.bookOffsetMs = bookOffsetMs;
		this.durationMs = durationMs;
		this.segment = segment;
		this.localPath = localPath;
		this.downloadState = downloadState;
	}

	public String getBookId() { return bookId; }
	public String getId() { return id; }
	public int getIndex() { return index; }
	public String getTitle() { return title; }
	public String getMediaUrl() { return mediaUrl; }
	public String getMimeType() { return mimeType; }
	public long getOffsetMs() { return offsetMs; }
	public long getBookOffsetMs() { return bookOffsetMs; }
	public long getDurationMs() { return durationMs; }
	public boolean isSegment() { return segment; }
	@Nullable public String getLocalPath() { return localPath; }
	public int getDownloadState() { return downloadState; }
	public boolean isDownloaded() { return (localPath != null) && !localPath.isEmpty(); }
}
