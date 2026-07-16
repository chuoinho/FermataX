package me.aap.fermata.addon.podcast.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PodcastSubscription {
	private final String feedKey;
	private final String canonicalUrl;
	private final String credentialRef;
	private final String title;
	private final String author;
	private final String description;
	private final String artworkUrl;
	private final String artworkCredentialRef;
	private final String websiteUrl;
	private final String language;
	private final boolean explicit;
	private final String etag;
	private final String lastModified;
	private final long lastCheckedMs;
	private final long lastSuccessMs;
	private final long nextRefreshMs;
	private final int failureCount;
	private final String lastErrorCode;
	private final long subscribedMs;

	public PodcastSubscription(String feedKey, String canonicalUrl,
			@Nullable String credentialRef, String title, String author, String description,
			String artworkUrl, @Nullable String artworkCredentialRef, String websiteUrl,
			String language, boolean explicit, @Nullable String etag,
			@Nullable String lastModified, long lastCheckedMs, long lastSuccessMs,
			long subscribedMs) {
		this(feedKey, canonicalUrl, credentialRef, title, author, description, artworkUrl,
				artworkCredentialRef, websiteUrl, language, explicit, etag, lastModified,
				lastCheckedMs, lastSuccessMs, 0, 0, null, subscribedMs);
	}

	public PodcastSubscription(String feedKey, String canonicalUrl,
			@Nullable String credentialRef, String title, String author, String description,
			String artworkUrl, @Nullable String artworkCredentialRef, String websiteUrl,
			String language, boolean explicit, @Nullable String etag,
			@Nullable String lastModified, long lastCheckedMs, long lastSuccessMs,
			long nextRefreshMs, int failureCount, @Nullable String lastErrorCode,
			long subscribedMs) {
		this.feedKey = feedKey;
		this.canonicalUrl = canonicalUrl;
		this.credentialRef = credentialRef;
		this.title = title;
		this.author = author;
		this.description = description;
		this.artworkUrl = artworkUrl;
		this.artworkCredentialRef = artworkCredentialRef;
		this.websiteUrl = websiteUrl;
		this.language = language;
		this.explicit = explicit;
		this.etag = etag;
		this.lastModified = lastModified;
		this.lastCheckedMs = Math.max(lastCheckedMs, 0);
		this.lastSuccessMs = Math.max(lastSuccessMs, 0);
		this.nextRefreshMs = Math.max(nextRefreshMs, 0);
		this.failureCount = Math.max(failureCount, 0);
		this.lastErrorCode = lastErrorCode;
		this.subscribedMs = Math.max(subscribedMs, 0);
	}

	@NonNull public String getFeedKey() { return feedKey; }
	@NonNull public String getCanonicalUrl() { return canonicalUrl; }
	@Nullable public String getCredentialRef() { return credentialRef; }
	@NonNull public String getTitle() { return title; }
	@NonNull public String getAuthor() { return author; }
	@NonNull public String getDescription() { return description; }
	@NonNull public String getArtworkUrl() { return artworkUrl; }
	@Nullable public String getArtworkCredentialRef() { return artworkCredentialRef; }
	@NonNull public String getWebsiteUrl() { return websiteUrl; }
	@NonNull public String getLanguage() { return language; }
	public boolean isExplicit() { return explicit; }
	@Nullable public String getEtag() { return etag; }
	@Nullable public String getLastModified() { return lastModified; }
	public long getLastCheckedMs() { return lastCheckedMs; }
	public long getLastSuccessMs() { return lastSuccessMs; }
	public long getNextRefreshMs() { return nextRefreshMs; }
	public int getFailureCount() { return failureCount; }
	@Nullable public String getLastErrorCode() { return lastErrorCode; }
	public long getSubscribedMs() { return subscribedMs; }
}
