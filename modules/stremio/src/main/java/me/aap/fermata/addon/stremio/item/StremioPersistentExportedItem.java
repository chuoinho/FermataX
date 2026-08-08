package me.aap.fermata.addon.stremio.item;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.ContentSubtitleSelectionItem;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PersistentMediaItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.media.lib.PlaybackPresentationItem;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackLifecycleItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.utils.async.FutureSupplier;

/** Collection projection that cannot rewrite a stable Stremio ID back to a stream ID. */
class StremioPersistentExportedItem extends ExtPlayable
		implements PersistentMediaItem, PlaybackProgressItem, PlaybackPresentationItem {
	private final PlayableItem original;
	private final String persistentId;

	static PlayableItem create(PlayableItem original, String exportId, BrowsableItem container) {
		return (original instanceof RemotePlaybackItem) ?
				new Remote(original, exportId, container) :
				new StremioPersistentExportedItem(original, exportId, container);
	}

	StremioPersistentExportedItem(PlayableItem original, String exportId,
			BrowsableItem container) {
		super(exportId, container, original.getResource());
		this.original = original;
		persistentId = (original instanceof PersistentMediaItem persistent) ?
				persistent.getPersistentId() : original.getOrigId();
	}

	@NonNull
	@Override
	public String getName() {
		return original.getName();
	}

	@Override
	public int getIcon() {
		return original.getIcon();
	}

	@Override
	public boolean isCacheable() {
		return false;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return original.getMediaData();
	}

	@Override
	public boolean isVideo() {
		return original.isVideo();
	}

	@Override
	public boolean isExternal() {
		return original.isExternal();
	}

	@Override
	public boolean isRecentEligible() {
		return original.isRecentEligible();
	}

	@Override
	public boolean isLocationSensitive() {
		return original.isLocationSensitive();
	}

	@Override
	public boolean supportsCombinedSubtitles() {
		return original.supportsCombinedSubtitles();
	}

	@Override
	public boolean isSeekable() {
		return original.isSeekable();
	}

	@Override
	public boolean isNetResource() {
		return original.isNetResource();
	}

	@Override
	public boolean isStream() {
		return original.isStream();
	}

	@NonNull
	@Override
	public Uri getLocation() {
		return original.getLocation();
	}

	@NonNull
	@Override
	public FutureSupplier<Long> getDuration() {
		return original.getDuration();
	}

	@Override
	@Nullable
	public String getUserAgent() {
		return original.getUserAgent();
	}

	@NonNull
	@Override
	public Map<String, String> getRequestHeaders() {
		return original.getRequestHeaders();
	}

	@Override
	@Nullable
	public MediaEngine getMediaEngine(@Nullable MediaEngine current, MediaEngine.Listener listener) {
		return original.getMediaEngine(current, listener);
	}

	@Override
	public String getOrigId() {
		return persistentId;
	}

	@Override
	public String getPersistentId() {
		return persistentId;
	}

	@NonNull
	@Override
	public PlayableItem getCanonicalPlaybackItem() {
		return original;
	}

	@NonNull
	@Override
	public PlayableItem export(String exportId, BrowsableItem parent) {
		return create(original, exportId, parent);
	}

	@Override
	public long getResumePosition() {
		return progress().getResumePosition();
	}

	@Override
	public FutureSupplier<Void> savePlaybackProgress(long position, boolean completed) {
		return progress().savePlaybackProgress(position, completed);
	}

	@Override
	public FutureSupplier<Void> savePlaybackProgress(long position, boolean completed,
			long playbackGeneration) {
		return progress().savePlaybackProgress(position, completed, playbackGeneration);
	}

	@Override
	public ProgressMode getPlaybackProgressMode() {
		return progress().getPlaybackProgressMode();
	}

	private PlaybackProgressItem progress() {
		if (original instanceof PlaybackProgressItem progress) return progress;
		throw new IllegalStateException("Stremio export lost progress ownership");
	}

	private static final class Remote extends StremioPersistentExportedItem
			implements RemotePlaybackItem, RemotePlaybackLifecycleItem,
			ContentSubtitleSelectionItem {
		private final RemotePlaybackItem remote;
		private final RemotePlaybackLifecycleItem lifecycle;
		private final ContentSubtitleSelectionItem subtitles;

		Remote(PlayableItem original, String exportId, BrowsableItem container) {
			super(original, exportId, container);
			remote = (RemotePlaybackItem) original;
			lifecycle = (original instanceof RemotePlaybackLifecycleItem owner) ? owner : null;
			subtitles = (ContentSubtitleSelectionItem) original;
		}

		@Override
		public String getSubtitleSelectionKey() {
			return subtitles.getSubtitleSelectionKey();
		}

		@Override
		public Long getPreferredSubtitleTrackId() {
			return subtitles.getPreferredSubtitleTrackId();
		}

		@Override
		public String getPreferredSubtitleLanguagePattern() {
			return subtitles.getPreferredSubtitleLanguagePattern();
		}

		@Override
		public boolean areSubtitlesDisabled() {
			return subtitles.areSubtitlesDisabled();
		}

		@Override
		public me.aap.fermata.media.net.PlaybackRequestProfile getPlaybackRequestProfile() {
			return remote.getPlaybackRequestProfile();
		}

		@Override
		public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
			return remote.prepareRemotePlayback();
		}

		@Override
		public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback(
				java.util.function.Consumer<me.aap.fermata.media.net.RemotePlaybackProgress> progress) {
			return remote.prepareRemotePlayback(progress);
		}

		@Override
		public void onPlaybackAttemptActivated(long requestRevision,
				java.util.function.Consumer<Throwable> failureHandler) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptActivated(requestRevision, failureHandler);
		}

		@Override
		public void onPlaybackAttemptPlayerReady(long requestRevision) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptPlayerReady(requestRevision);
		}

		@Override
		public void onPlaybackAttemptFirstFrame(long requestRevision) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptFirstFrame(requestRevision);
		}

		@Override
		public void onPlaybackAttemptStarted(long requestRevision) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptStarted(requestRevision);
		}

		@Override
		public void onPlaybackAttemptPaused(long requestRevision) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptPaused(requestRevision);
		}

		@Override
		public void onPlaybackAttemptEnded(long requestRevision) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptEnded(requestRevision);
		}

		@Override
		public boolean onPlaybackAttemptFallback(long requestRevision) {
			return (lifecycle == null) || lifecycle.onPlaybackAttemptFallback(requestRevision);
		}

		@Override
		public void onPlaybackAttemptFailed(long requestRevision, Throwable error) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptFailed(requestRevision, error);
		}

		@Override
		public void onPlaybackAttemptCancelled(long requestRevision) {
			if (lifecycle != null) lifecycle.onPlaybackAttemptCancelled(requestRevision);
		}
	}
}
