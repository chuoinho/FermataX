package me.aap.fermata.addon.stremio.item;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.P2P_STREAMING;
import static me.aap.utils.async.Completed.completed;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.addon.stremio.StremioAddon;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackAttemptSupervisor;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.security.StremioArtworkLoader;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.ContentSubtitleSelectionItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.media.lib.PersistentMediaItem;
import me.aap.fermata.media.net.PlaybackHeaderResolver;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.fermata.media.net.RemotePlaybackLifecycleItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.utils.async.FutureSupplier;

/** Direct finite-media skeleton. Engine handoff must call {@link #resolveForPlayback()}. */
public final class StremioDirectPlayableItem extends ExtPlayable
		implements PlaybackProgressItem, PersistentMediaItem, RemotePlaybackItem,
		RemotePlaybackLifecycleItem, ContentSubtitleSelectionItem {
	private final StremioItemGateway gateway;
	private final PlaybackHeaderResolver headerResolver;
	private final LongSupplier clock;
	private final PlaybackRequestProfile pendingProfile;
	private final StremioPlaybackResource playbackResource;
	private final String subtitleSelectionKey;
	private final PlaybackAttemptSupervisor playbackAttempts = new PlaybackAttemptSupervisor();
	private volatile PlaybackDescriptor descriptor;
	private volatile long resumePosition;

	public StremioDirectPlayableItem(BrowsableItem parent, StremioItemGateway gateway,
			PlaybackDescriptor descriptor, long resumePosition) {
		this(parent, gateway, descriptor, null, resumePosition, System::currentTimeMillis);
	}

	StremioDirectPlayableItem(BrowsableItem parent, StremioItemGateway gateway,
			PlaybackDescriptor descriptor, long resumePosition, LongSupplier clock) {
		this(parent, gateway, descriptor, null, resumePosition, clock);
	}

	public StremioDirectPlayableItem(BrowsableItem parent, StremioItemGateway gateway,
			PlaybackDescriptor descriptor, StreamAggregationRequest request,
			long resumePosition) {
		this(parent, gateway, descriptor, request, resumePosition, System::currentTimeMillis);
	}

	private StremioDirectPlayableItem(BrowsableItem parent, StremioItemGateway gateway,
			PlaybackDescriptor descriptor, StreamAggregationRequest request,
			long resumePosition, LongSupplier clock) {
		super(StremioItemIds.stream(requirePlayable(descriptor)), parent,
				(request == null) ?
						me.aap.utils.vfs.generic.GenericFileSystem.getInstance().create(
								playbackLocation(descriptor)) :
						new StremioPlaybackResource(descriptor, gateway, request));
		this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
		playbackResource = (getResource() instanceof StremioPlaybackResource resource) ?
				resource : null;
		headerResolver = (gateway instanceof PlaybackHeaderResolver resolver) ? resolver : null;
		if (!StremioRootItem.ID.equals(parent.getRoot().getId())) {
			throw new IllegalArgumentException("Stremio playable requires the Stremio root");
		}
		this.descriptor = descriptor;
		subtitleSelectionKey = descriptor.identity().videoKey();
		PlaybackRequestProfile.Builder pending = PlaybackRequestProfile.builder(
				URI.create(playbackLocation(descriptor)), descriptor.descriptorId());
		if (descriptor.targetKind() == PlaybackDescriptor.TargetKind.TORRENT) {
			pending.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY);
			pending.requireCapability(P2P_STREAMING);
		}
		pendingProfile = pending.build();
		this.resumePosition = Math.max(resumePosition, 0);
		this.clock = java.util.Objects.requireNonNull(clock, "clock");
	}

	public PlaybackDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public String getSubtitleSelectionKey() {
		return subtitleSelectionKey;
	}

	@Override
	public Long getPreferredSubtitleTrackId() {
		StremioSubtitleSelectionStore.Selection selection =
				StremioSubtitleSelectionStore.get(subtitleSelectionKey);
		return (selection == null) ? null : selection.trackId();
	}

	@Override
	public String getPreferredSubtitleLanguagePattern() {
		StremioSubtitleSelectionStore.Selection selection =
				StremioSubtitleSelectionStore.get(subtitleSelectionKey);
		return ((selection == null) || selection.language().isBlank()) ?
				StremioAddon.preferredSubtitlePattern() : selection.language();
	}

	@Override
	public boolean areSubtitlesDisabled() {
		StremioSubtitleSelectionStore.Selection selection =
				StremioSubtitleSelectionStore.get(subtitleSelectionKey);
		return (selection != null) && selection.disabled();
	}

	public PlaybackRequestProfile requestProfile() {
		PlaybackRequestProfile profile = descriptor.requestProfile();
		return (profile == null) ? pendingProfile : profile;
	}

	@Override
	public PlaybackRequestProfile getPlaybackRequestProfile() {
		return requestProfile();
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback() {
		return prepareRemotePlayback(null);
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> prepareRemotePlayback(
			java.util.function.Consumer<me.aap.fermata.media.net.RemotePlaybackProgress> progress) {
		long operationId = playbackAttempts.currentOperationId();
		if (operationId < 0L) {
			operationId = playbackAttempts.begin(descriptor, -1L, ignored -> {});
		}
		final long attemptId = operationId;
		playbackAttempts.preparationStarted(attemptId);
		FutureSupplier<RemotePlaybackRequest> result = resolveFreshDescriptor()
				.then(resolved -> gateway.preparePlayback(resolved, progress))
				.map(request -> {
					playbackAttempts.dataReady(attemptId, request);
					return request;
				});
		result.onCompletion((request, error) -> {
			if (error != null) playbackAttempts.failedCurrent(attemptId, error);
		});
		return result;
	}

	@Override
	public void onPlaybackAttemptActivated(long requestRevision,
			Consumer<Throwable> failureHandler) {
		if (playbackResource != null) playbackResource.activateSubtitles();
		playbackAttempts.begin(descriptor, requestRevision, failureHandler);
	}

	@Override
	public void onPlaybackAttemptPlayerReady(long requestRevision) {
		playbackAttempts.playerReady(requestRevision);
	}

	@Override
	public void onPlaybackAttemptFirstFrame(long requestRevision) {
		playbackAttempts.firstFrame(requestRevision);
	}

	@Override
	public void onPlaybackAttemptStarted(long requestRevision) {
		playbackAttempts.started(requestRevision);
	}

	@Override
	public void onPlaybackAttemptPaused(long requestRevision) {
		playbackAttempts.paused(requestRevision);
	}

	@Override
	public void onPlaybackAttemptEnded(long requestRevision) {
		playbackAttempts.ended(requestRevision);
		if (playbackResource != null) playbackResource.cancelSubtitles();
	}

	@Override
	public boolean onPlaybackAttemptFallback(long requestRevision) {
		return playbackAttempts.claimDecoderFallback(requestRevision);
	}

	@Override
	public void onPlaybackAttemptFailed(long requestRevision, Throwable error) {
		playbackAttempts.failed(requestRevision, error);
		if (playbackResource != null) playbackResource.cancelSubtitles();
	}

	@Override
	public void onPlaybackAttemptCancelled(long requestRevision) {
		playbackAttempts.cancel(requestRevision);
		if (playbackResource != null) playbackResource.cancelSubtitles();
	}

	/** Returns a fresh immutable choice or resolves it from durable video identity. */
	public FutureSupplier<PlaybackDescriptor> resolveForPlayback() {
		return resolveFreshDescriptor().then(gateway::validatePlayback);
	}

	private FutureSupplier<PlaybackDescriptor> resolveFreshDescriptor() {
		PlaybackDescriptor current = descriptor;
		long now = clock.getAsLong();
		if (!current.isExpired(now)) return me.aap.utils.async.Completed.completed(current);

		return gateway.resolve(current.refreshRequest()).map(resolved -> {
			requirePlayable(resolved);
			if (!current.identity().equals(resolved.identity()) ||
					!current.metadata().equals(resolved.metadata())) {
				throw new IllegalStateException("Resolved stream identity mismatch");
			}
			resolved.requireFresh(clock.getAsLong());
			return resolved;
		}).map(resolved -> {
			descriptor = resolved;
			if (playbackResource != null) playbackResource.updateDescriptor(resolved);
			return resolved;
		});
	}

	@NonNull
	@Override
	public String getName() {
		return descriptor.metadata().title();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.stremio;
	}

	@NonNull
	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(descriptor.metadata().title());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		String stream = descriptor.streamTitle();
		if ((stream == null) || stream.isBlank()) stream = descriptor.streamName();
		if ((stream == null) || stream.isBlank()) stream = descriptor.providerName();
		return completed(stream);
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return StremioArtworkLoader.load(descriptor.metadata().artwork());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		StremioPlaybackMetadata metadata = descriptor.metadata();
		MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
				.putString(METADATA_KEY_TITLE, metadata.title());
		if (metadata.durationMillis() >= 0) {
			builder.putLong(METADATA_KEY_DURATION, metadata.durationMillis());
		}
		return getIconUri().map(icon -> {
			if (icon != null) builder.putString(
					MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, icon.toString());
			return builder.build();
		});
	}

	@NonNull
	@Override
	public Uri getLocation() {
		PlaybackDescriptor current = descriptor;
		if (current.isExpired(clock.getAsLong()) &&
				current.targetKind() != PlaybackDescriptor.TargetKind.TORRENT) {
			throw new IllegalStateException(
					"Expired Stremio stream must be resolved before playback");
		}
		return Uri.parse(playbackLocation(current));
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@Override
	public boolean isStream() {
		return false;
	}

	@Override
	public boolean isSeekable() {
		// Unknown-duration HLS/DASH may be live; the engine refines this after prepare.
		return descriptor.metadata().durationMillis() > 0L;
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getPrevPlayable() {
		return adjacent(false);
	}

	@NonNull
	@Override
	public FutureSupplier<PlayableItem> getNextPlayable() {
		return adjacent(true);
	}

	@Override
	public boolean isExternal() {
		return false;
	}

	@Override
	public boolean isLocationSensitive() {
		return true;
	}

	@Override
	public boolean supportsCombinedSubtitles() {
		return false;
	}

	@NonNull
	@Override
	public FutureSupplier<Long> getDuration() {
		return completed(descriptor.metadata().durationMillis());
	}

	@Override
	public String getOrigId() {
		return descriptor.identity().videoKey();
	}

	@Override
	public String getPersistentId() {
		return descriptor.identity().videoKey();
	}

	@NonNull
	@Override
	public PlayableItem export(String exportId, BrowsableItem parent) {
		return StremioPersistentExportedItem.create(this, exportId, parent);
	}

	@Override
	public long getResumePosition() {
		return resumePosition;
	}

	@NonNull
	@Override
	public FutureSupplier<Void> savePlaybackProgress(long position, boolean completed) {
		long normalized = completed ? 0 : Math.max(position, 0);
		resumePosition = normalized;
		return gateway.saveProgress(descriptor.identity(), normalized, completed);
	}

	@NonNull
	@Override
	public FutureSupplier<Void> savePlaybackProgress(long position, boolean completed,
			long playbackGeneration) {
		long normalized = completed ? 0 : Math.max(position, 0);
		resumePosition = normalized;
		return gateway.saveProgress(descriptor.identity(), normalized, completed,
				playbackGeneration);
	}

	@Override
	public ProgressMode getPlaybackProgressMode() {
		return ProgressMode.MANAGED;
	}

	private static PlaybackDescriptor requirePlayable(PlaybackDescriptor descriptor) {
		java.util.Objects.requireNonNull(descriptor, "descriptor");
		boolean playable = switch (descriptor.targetKind()) {
			case DIRECT_HTTP, HLS, DASH -> descriptor.targetValue() != null;
			case TORRENT -> descriptor.sourceTarget() instanceof
					me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
			default -> false;
		};
		if (!playable) {
			throw new IllegalArgumentException("Playable requires a supported Stremio target");
		}
		return descriptor;
	}

	private static String playbackLocation(PlaybackDescriptor descriptor) {
		if (descriptor.targetValue() != null) return descriptor.targetValue();
		return "http://127.0.0.1/stremio-pending/" + descriptor.descriptorId();
	}

	private FutureSupplier<PlayableItem> adjacent(boolean next) {
		return gateway.adjacentPlayback(this, next);
	}
}
