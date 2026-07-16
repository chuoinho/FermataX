package me.aap.fermata.addon.podcast.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.podcast.util.PodcastIds;

public final class PodcastEpisode {
	public enum IdentityKind {
		GUID,
		ENCLOSURE,
		PERMALINK,
		METADATA
	}

	private final String key;
	private final IdentityKind identityKind;
	private final String identity;
	private final String guid;
	private final String title;
	private final String description;
	private final String author;
	private final String permalink;
	private final String mediaUrl;
	private final String mimeType;
	private final String artworkUrl;
	private final long publicationMs;
	private final long durationMs;
	private final long mediaLength;
	private final int seasonNumber;
	private final int episodeNumber;
	private final boolean explicit;

	private PodcastEpisode(Builder builder, IdentityKind identityKind, String identity) {
		this.identityKind = identityKind;
		this.identity = identity;
		key = PodcastIds.hash(identityKind.name() + '\n' + identity);
		guid = text(builder.guid, 4000);
		title = text(builder.title, 1000);
		description = text(builder.description, 64 * 1024);
		author = text(builder.author, 1000);
		permalink = text(builder.permalink, 8000);
		mediaUrl = text(builder.mediaUrl, 16 * 1024);
		mimeType = text(builder.mimeType, 256);
		artworkUrl = text(builder.artworkUrl, 16 * 1024);
		publicationMs = Math.max(builder.publicationMs, 0);
		durationMs = builder.durationMs;
		mediaLength = builder.mediaLength;
		seasonNumber = builder.seasonNumber;
		episodeNumber = builder.episodeNumber;
		explicit = builder.explicit;
	}

	@Nullable
	public static PodcastEpisode build(Builder builder) {
		String identity = text(builder.guid, 4000);
		IdentityKind kind = IdentityKind.GUID;
		if (identity.isEmpty()) {
			identity = text(builder.mediaUrl, 16 * 1024);
			kind = IdentityKind.ENCLOSURE;
		}
		if (identity.isEmpty()) {
			identity = text(builder.permalink, 8000);
			kind = IdentityKind.PERMALINK;
		}
		if (identity.isEmpty()) {
			String title = text(builder.title, 1000);
			if (title.isEmpty()) return null;
			identity = title + '\n' + builder.publicationMs + '\n' + builder.durationMs;
			kind = IdentityKind.METADATA;
		}
		return new PodcastEpisode(builder, kind, identity);
	}

	@NonNull
	public String getKey() {
		return key;
	}

	@NonNull
	public IdentityKind getIdentityKind() {
		return identityKind;
	}

	@NonNull
	public String getIdentity() {
		return identity;
	}

	@NonNull
	public String getGuid() {
		return guid;
	}

	@NonNull
	public String getTitle() {
		return title;
	}

	@NonNull
	public String getDescription() {
		return description;
	}

	@NonNull
	public String getAuthor() {
		return author;
	}

	@NonNull
	public String getPermalink() {
		return permalink;
	}

	@NonNull
	public String getMediaUrl() {
		return mediaUrl;
	}

	@NonNull
	public String getMimeType() {
		return mimeType;
	}

	@NonNull
	public String getArtworkUrl() {
		return artworkUrl;
	}

	public long getPublicationMs() {
		return publicationMs;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public long getMediaLength() {
		return mediaLength;
	}

	public int getSeasonNumber() {
		return seasonNumber;
	}

	public int getEpisodeNumber() {
		return episodeNumber;
	}

	public boolean isExplicit() {
		return explicit;
	}

	public boolean isPlayable() {
		return !mediaUrl.isEmpty();
	}

	private static String text(String value, int max) {
		if (value == null) return "";
		value = value.trim();
		return (value.length() <= max) ? value : value.substring(0, max).trim();
	}

	public static final class Builder {
		public String guid;
		public String title;
		public String description;
		public String author;
		public String permalink;
		public String mediaUrl;
		public String mimeType;
		public String artworkUrl;
		public long publicationMs;
		public long durationMs = -1;
		public long mediaLength = -1;
		public int seasonNumber;
		public int episodeNumber;
		public boolean explicit;
	}
}
