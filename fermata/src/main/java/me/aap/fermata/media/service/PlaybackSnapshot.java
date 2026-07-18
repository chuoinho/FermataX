package me.aap.fermata.media.service;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * Immutable view of the media service playback state at one revision.
 */
public final class PlaybackSnapshot {
	private final long revision;
	@Nullable
	private final PlayableItem item;
	@NonNull
	private final PlaybackStateCompat state;
	@Nullable
	private final MediaMetadataCompat metadata;

	PlaybackSnapshot(long revision, @Nullable PlayableItem item,
			@NonNull PlaybackStateCompat state, @Nullable MediaMetadataCompat metadata) {
		this.revision = revision;
		this.item = item;
		this.state = state;
		this.metadata = metadata;
	}

	public long getRevision() {
		return revision;
	}

	@Nullable
	public PlayableItem getItem() {
		return item;
	}

	@NonNull
	public PlaybackStateCompat getState() {
		return state;
	}

	@Nullable
	public MediaMetadataCompat getMetadata() {
		return metadata;
	}

	@NonNull
	public CharSequence getDisplayTitle() {
		return resolveDisplayTitle(item, metadata);
	}

	@NonNull
	public static CharSequence resolveDisplayTitle(@Nullable PlayableItem item,
			@Nullable MediaMetadataCompat metadata) {
		String title = metadataTitle(metadata, MediaMetadataCompat.METADATA_KEY_TITLE);
		String displayTitle = metadataTitle(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
		return resolveDisplayTitle(title, displayTitle, (item == null) ? "" : item.getName());
	}

	@NonNull
	static CharSequence resolveDisplayTitle(@Nullable String title, @Nullable String displayTitle,
			@NonNull CharSequence fallback) {
		String normalized = normalizeTitle(title);
		if (normalized == null) normalized = normalizeTitle(displayTitle);
		return (normalized == null) ? fallback : normalized;
	}

	@Nullable
	private static String metadataTitle(@Nullable MediaMetadataCompat metadata, String key) {
		if (metadata == null) return null;
		return normalizeTitle(metadata.getString(key));
	}

	@Nullable
	private static String normalizeTitle(@Nullable String title) {
		if (title == null) return null;
		title = title.trim();
		return title.isEmpty() ? null : title;
	}

	public boolean hasSameItem(@Nullable PlaybackSnapshot other) {
		return Objects.equals(item, (other == null) ? null : other.item);
	}

	/** Transient hand-off states do not own durable Podcast/Audiobook progress. */
	public boolean canPersistProgress() {
		return canPersistProgress(state.getState());
	}

	public static boolean canPersistProgress(int state) {
		return (state == PlaybackStateCompat.STATE_PLAYING) ||
				(state == PlaybackStateCompat.STATE_PAUSED) ||
				(state == PlaybackStateCompat.STATE_STOPPED);
	}
}
