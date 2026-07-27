package me.aap.fermata.addon.stremio.item;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.browse.SearchResults;
import me.aap.fermata.addon.stremio.browse.StremioBrowseTarget;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregationResult;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;

/** MediaLib-facing boundary. Runtime adapters own network, cache, database and cancellation. */
public interface StremioItemGateway {
	FutureSupplier<List<BrowseProvider>> providers();

	default FutureSupplier<List<CatalogDescriptor>> catalogs() {
		return providers().then(providers -> collectCatalogs(providers, 0, new ArrayList<>()));
	}

	FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid);

	FutureSupplier<CatalogPage> catalog(
			CatalogRoute route, @Nullable String genre, int skip);

	default FutureSupplier<CatalogPage> catalog(CatalogRoute route,
			@Nullable String genre, int skip, Map<String, List<String>> extras) {
		Objects.requireNonNull(extras, "extras");
		if (extras.isEmpty()) return catalog(route, genre, skip);
		return Completed.failed(new UnsupportedOperationException(
				"Catalog extras are not supported by this gateway"));
	}

	FutureSupplier<BrowseDetails> meta(BrowseMedia media);

	/** Persists a secret-free resolver projection before it is added to Unified Favorites. */
	default FutureSupplier<String> preparePersistentItem(
			BrowseMedia media, @Nullable BrowseEpisode episode) {
		return Completed.completedNull();
	}

	FutureSupplier<SearchResults> search(String query);

	/** Restores an opaque durable video/content ID without exposing transport values. */
	default FutureSupplier<StremioBrowseTarget> presentationTarget(String stableId) {
		return Completed.completedNull();
	}

	FutureSupplier<StreamAggregationResult> streams(StreamAggregationRequest request);

	/** Keeps the initiating provider separate from canonical cross-provider playback identity. */
	default FutureSupplier<StreamAggregationResult> streams(
			String sourceUuid, StreamAggregationRequest request) {
		return streams(request);
	}

	/**
	 * Returns the interactive stream snapshot and optionally publishes one final late-result batch.
	 * Implementations that do not aggregate providers retain their existing single-result behavior.
	 */
	default FutureSupplier<StreamAggregationResult> streams(String sourceUuid,
			StreamAggregationRequest request,
			BiConsumer<StreamAggregationResult, Throwable> terminalResult) {
		return streams(sourceUuid, request);
	}

	/** Re-fetches a selected stream. The old descriptor target must never be replayed. */
	FutureSupplier<PlaybackDescriptor> resolve(
			PlaybackDescriptor.DescriptorRefreshRequest request);

	/** Rechecks source revision, enabled state and consent immediately before engine handoff. */
	default FutureSupplier<PlaybackDescriptor> validatePlayback(PlaybackDescriptor descriptor) {
		return Completed.completed(descriptor);
	}

	/** Resolves target-specific work immediately before handing a URL to a media engine. */
	default FutureSupplier<RemotePlaybackRequest> preparePlayback(PlaybackDescriptor descriptor) {
		if ((descriptor.targetValue() == null) || (descriptor.requestProfile() == null)) {
			return Completed.failed(new UnsupportedOperationException(
					"Stremio playback target is unavailable"));
		}
		me.aap.fermata.media.net.PlaybackHeaderResolver headers =
				(this instanceof me.aap.fermata.media.net.PlaybackHeaderResolver resolver) ?
						resolver : null;
		return validatePlayback(descriptor).map(validated -> new RemotePlaybackRequest(
				java.net.URI.create(validated.targetValue()), validated.requestProfile(), headers,
				validated.endpointValidator()));
	}

	default FutureSupplier<RemotePlaybackRequest> preparePlayback(PlaybackDescriptor descriptor,
			Consumer<me.aap.fermata.media.net.RemotePlaybackProgress> progress) {
		return preparePlayback(descriptor);
	}

	/** Resolves the previous/next episode without traversing stream choices as queue items. */
	default FutureSupplier<PlayableItem> adjacentPlayback(
			StremioDirectPlayableItem current, boolean next) {
		return Completed.completedNull();
	}

	/** Resolves sidecar subtitles lazily so stream selection and playback are never blocked. */
	default FutureSupplier<SubtitleAggregationResult> subtitles(
			String type, String videoId) {
		return Completed.completed(new SubtitleAggregationResult(List.of(), List.of(), false));
	}

	default FutureSupplier<SubtitleAggregationResult> subtitles(
			PlaybackDescriptor descriptor, String type, String videoId) {
		return subtitles(type, videoId);
	}

	default FutureSupplier<SubtitleAggregationResult> subtitles(
			PlaybackDescriptor descriptor,
			me.aap.fermata.addon.stremio.playback.ContentIdentitySet identities,
			String type, String videoId) {
		return subtitles(descriptor, type, videoId);
	}

	/** Loads one selected subtitle through the bounded Stremio transport. */
	default FutureSupplier<byte[]> loadSubtitle(SubtitleDescriptor descriptor) {
		return Completed.failed(new UnsupportedOperationException(
				"Stremio subtitle loading is unavailable"));
	}

	FutureSupplier<Void> saveProgress(
			StremioPlaybackIdentity identity, long position, boolean completed);

	default FutureSupplier<Void> saveProgress(StremioPlaybackIdentity identity, long position,
			boolean completed, long playbackGeneration) {
		return saveProgress(identity, position, completed);
	}

	private FutureSupplier<List<CatalogDescriptor>> collectCatalogs(
			List<BrowseProvider> providers, int index, List<CatalogDescriptor> target) {
		if (index >= providers.size()) return Completed.completed(List.copyOf(target));
		BrowseProvider provider = providers.get(index);
		if (!provider.enabled()) return collectCatalogs(providers, index + 1, target);
		return catalogs(provider.sourceUuid()).then(catalogs -> {
			target.addAll(catalogs);
			return collectCatalogs(providers, index + 1, target);
		});
	}
}
