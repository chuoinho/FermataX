package me.aap.fermata.media.service;

import static me.aap.fermata.media.service.PlaybackSnapshot.METADATA_KEY_PREPARATION_STATUS;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Transient preparation data owned by one engine, item and playback revision. */
final class PlaybackPreparationStatus {
	@Nullable
	private Object engine;
	@Nullable
	private Object item;
	private long revision = -1L;
	@NonNull
	private String detail = "";
	private boolean complete;

	void update(@NonNull Object engine, @NonNull Object item, long revision,
			@NonNull String detail) {
		if (matches(engine, item, revision) && complete) return;
		this.engine = engine;
		this.item = item;
		this.revision = revision;
		this.detail = detail.trim();
		complete = false;
	}

	boolean complete(@NonNull Object engine, @NonNull Object item, long revision) {
		if (!matches(engine, item, revision)) return false;
		detail = "";
		complete = true;
		return true;
	}

	@NonNull
	static MediaMetadataCompat clearMetadata(@NonNull MediaMetadataCompat metadata) {
		String detail = metadata.getString(METADATA_KEY_PREPARATION_STATUS);
		if ((detail == null) || detail.isBlank()) return metadata;
		return new MediaMetadataCompat.Builder(metadata)
				.putString(METADATA_KEY_PREPARATION_STATUS, null).build();
	}

	@NonNull
	MediaMetadataCompat merge(@NonNull Object engine, @NonNull Object item, long revision,
			@NonNull MediaMetadataCompat metadata) {
		String detail = detailFor(engine, item, revision);
		if (detail.isEmpty()) return metadata;
		return new MediaMetadataCompat.Builder(metadata)
				.putString(METADATA_KEY_PREPARATION_STATUS, detail).build();
	}

	@NonNull
	String detailFor(@NonNull Object engine, @NonNull Object item, long revision) {
		return matches(engine, item, revision) ? detail : "";
	}

	boolean matches(@NonNull Object engine, @NonNull Object item, long revision) {
		return (this.engine == engine) && (this.item == item) && (this.revision == revision);
	}

	void clear() {
		engine = null;
		item = null;
		revision = -1L;
		detail = "";
		complete = false;
	}
}
