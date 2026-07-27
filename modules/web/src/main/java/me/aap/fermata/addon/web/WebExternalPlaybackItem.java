package me.aap.fermata.addon.web;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonCapability;
import me.aap.fermata.addon.external.ExternalPlaybackRequest;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.vfs.generic.GenericFileSystem;

/** Immutable browser route carrying the original external metadata. */
final class WebExternalPlaybackItem extends ExtPlayable {
	interface EngineFactory {
		MediaEngine create(@Nullable MediaEngine current, MediaEngine.Listener listener,
				ExternalPlaybackRequest request);
	}

	private final ExternalPlaybackRequest request;
	private final EngineFactory engineFactory;

	WebExternalPlaybackItem(DefaultMediaLib lib, ExternalPlaybackRequest request,
			EngineFactory engineFactory) {
		super("web:external:" + request.getContentId(),
				new ExtRoot("web", lib, AddonCapability.WEB),
				GenericFileSystem.getInstance().create(request.getTarget()));
		this.request = request;
		this.engineFactory = engineFactory;
	}

	@Override
	public String getName() {
		return request.getTitle();
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	ExternalPlaybackRequest getExternalPlaybackRequest() {
		return request;
	}

	@Nullable
	@Override
	public MediaEngine getMediaEngine(@Nullable MediaEngine current,
			MediaEngine.Listener listener) {
		return engineFactory.create(current, listener, request);
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_TITLE, request.getTitle())
				.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, request.getDurationMillis());
		if (!request.getArtworkUri().isEmpty()) {
			metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
					request.getArtworkUri());
		}
		return Completed.completed(metadata.build());
	}
}
