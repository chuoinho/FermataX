package me.aap.fermata.addon.stremio.integration;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.browse.StremioBrowseTarget;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.item.StremioDirectPlayableItem;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.session.StremioFavoriteUpdate;
import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioLibraryItem;
import me.aap.fermata.addon.stremio.session.StremioProgressSnapshot;
import me.aap.fermata.addon.stremio.session.StremioProgressState;
import me.aap.fermata.addon.stremio.session.StremioProviderState;
import me.aap.fermata.addon.stremio.session.StremioRestorePoint;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioSessionGateway;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;
import me.aap.fermata.addon.stremio.session.StremioVoiceCandidate;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregationResult;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/** Production Phase 5 projection over the existing Stremio repository and item gateway. */
public final class StremioSessionGatewayAdapter implements StremioSessionGateway,
		StremioItemGateway, AutoCloseable {

	private final StremioRepository repository;
	private final Supplier<CompletionStage<List<me.aap.fermata.addon.stremio.browse.BrowseProvider>>>
			providerSource;
	private final StremioItemGateway items;
	private final Executor executor;
	private final StremioSessionCoordinator sessions;
	private final StremioProjectionStore projectionStore;
	private final StremioSessionReadStore sessionStore;
	private final StremioProgressStore progressStore;
	private final StremioFavoriteStore favoriteStore;
	private final StremioRestoreStore restoreStore;
	private final StremioMediaItemResolver mediaResolver;
	private final StremioAdjacentPlaybackResolver adjacentResolver;
	private final StremioVoiceSearchResolver voiceSearch;
	private volatile boolean closed;

	public StremioSessionGatewayAdapter(StremioRepository repository,
			StremioProviderCatalog providers, StremioItemGateway items, Executor executor) {
		this(repository, providers::browseProviders, items, executor);
	}

	StremioSessionGatewayAdapter(StremioRepository repository,
			Supplier<CompletionStage<List<me.aap.fermata.addon.stremio.browse.BrowseProvider>>>
					providerSource,
			StremioItemGateway items, Executor executor) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.providerSource = Objects.requireNonNull(providerSource, "providerSource");
		this.items = Objects.requireNonNull(items, "items");
		this.executor = Objects.requireNonNull(executor, "executor");
		sessions = new StremioSessionCoordinator(this);
		projectionStore = new StremioProjectionStore(this.repository, this.providerSource,
				this.items, () -> closed);
		sessionStore = new StremioSessionReadStore(this.repository, projectionStore, () -> closed);
		progressStore = new StremioProgressStore(this.repository, sessions, () -> closed);
		favoriteStore = new StremioFavoriteStore(this.repository, () -> closed,
				projectionStore::load, projectionStore::persist);
		restoreStore = new StremioRestoreStore(this.repository, () -> closed,
				projectionStore::load);
		mediaResolver = new StremioMediaItemResolver(this.repository, projectionStore, this,
				favoriteStore::providerState, () -> closed);
		adjacentResolver = new StremioAdjacentPlaybackResolver(sessions, mediaResolver);
		voiceSearch = new StremioVoiceSearchResolver(this.providerSource, this.items,
				projectionStore, this.executor);
	}

	public StremioSessionCoordinator sessions() {
		return sessions;
	}

	@Override
	public CompletionStage<List<StremioContinueEntry>> loadContinue(int limit) {
		return sessionStore.loadContinue(limit);
	}

	@Override
	public CompletionStage<List<StremioLibraryItem>> loadLibraryFavorites(int limit) {
		return sessionStore.loadLibraryFavorites(limit);
	}

	@Override
	public CompletionStage<Map<String, StremioSessionItem>> loadItemsBatch(
			Collection<String> stableIds) {
		return sessionStore.loadItemsBatch(stableIds);
	}

	@Override
	public CompletionStage<Map<String, StremioProgressState>> loadProgressBatch(
			Collection<String> stableIds) {
		return sessionStore.loadProgressBatch(stableIds);
	}

	@Override
	public CompletionStage<Map<String, Boolean>> loadFavoriteStates(
			Collection<String> stableIds) {
		return sessionStore.loadFavoriteStates(stableIds);
	}

	@Override
	public CompletionStage<Void> dismissContinue(String stableId) {
		return sessionStore.dismissContinue(stableId);
	}

	@Override
	public CompletionStage<StremioSessionItem> loadItem(String stableId) {
		return sessionStore.loadItem(stableId);
	}

	@Override
	public FutureSupplier<List<me.aap.fermata.addon.stremio.browse.BrowseProvider>> providers() {
		return items.providers();
	}

	@Override
	public FutureSupplier<List<me.aap.fermata.addon.stremio.browse.CatalogDescriptor>> catalogs(
			String sourceUuid) {
		return items.catalogs(sourceUuid);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.browse.CatalogPage> catalog(
			me.aap.fermata.addon.stremio.browse.CatalogRoute route,
			@Nullable String genre, int skip) {
		return items.catalog(route, genre, skip);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.browse.CatalogPage> catalog(
			me.aap.fermata.addon.stremio.browse.CatalogRoute route,
			@Nullable String genre, int skip, Map<String, List<String>> extras) {
		return items.catalog(route, genre, skip, extras);
	}

	@Override
	public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
		return items.meta(media);
	}

	@Override
	public FutureSupplier<String> preparePersistentItem(
			BrowseMedia media, @Nullable BrowseEpisode episode) {
		return items.preparePersistentItem(media, episode);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.browse.SearchResults> search(String query) {
		return items.search(query);
	}

	@Override
	public FutureSupplier<StremioBrowseTarget> presentationTarget(String stableId) {
		CompletableFuture<StremioBrowseTarget> result = projectionStore.load(stableId)
				.thenApply(projection -> {
					if (projection == null) return null;
					if (projection.episode() == null) {
						return new StremioBrowseTarget(projection.media(), null, null);
					}
					List<BrowseEpisode> episodes = projection.siblings().isEmpty() ?
							List.of(projection.episode()) : projection.siblings();
					return new StremioBrowseTarget(projection.media(), projection.episode(),
							new BrowseSeason(projection.item().seasonNumber(), episodes));
				}).toCompletableFuture();
		return StremioFutureBridge.from(result);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.playback.StreamAggregationResult> streams(
			me.aap.fermata.addon.stremio.playback.StreamAggregationRequest request) {
		return items.streams(request);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.playback.StreamAggregationResult> streams(
			String sourceUuid,
			me.aap.fermata.addon.stremio.playback.StreamAggregationRequest request) {
		return items.streams(sourceUuid, request);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.playback.StreamAggregationResult> streams(
			String sourceUuid,
			me.aap.fermata.addon.stremio.playback.StreamAggregationRequest request,
			java.util.function.BiConsumer<
					me.aap.fermata.addon.stremio.playback.StreamAggregationResult, Throwable>
					terminalResult) {
		return items.streams(sourceUuid, request, terminalResult);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.playback.PlaybackDescriptor> resolve(
			me.aap.fermata.addon.stremio.playback.PlaybackDescriptor.DescriptorRefreshRequest request) {
		return items.resolve(request);
	}

	@Override
	public FutureSupplier<me.aap.fermata.addon.stremio.playback.PlaybackDescriptor>
	validatePlayback(me.aap.fermata.addon.stremio.playback.PlaybackDescriptor descriptor) {
		return items.validatePlayback(descriptor);
	}

	@Override
	public FutureSupplier<me.aap.fermata.media.net.RemotePlaybackRequest> preparePlayback(
			me.aap.fermata.addon.stremio.playback.PlaybackDescriptor descriptor) {
		return items.preparePlayback(descriptor);
	}

	@Override
	public FutureSupplier<me.aap.fermata.media.net.RemotePlaybackRequest> preparePlayback(
			me.aap.fermata.addon.stremio.playback.PlaybackDescriptor descriptor,
			java.util.function.Consumer<me.aap.fermata.media.net.RemotePlaybackProgress> progress) {
		return items.preparePlayback(descriptor, progress);
	}

	@Override
	public FutureSupplier<SubtitleAggregationResult> subtitles(String type, String videoId) {
		return items.subtitles(type, videoId);
	}

	@Override
	public FutureSupplier<SubtitleAggregationResult> subtitles(
			me.aap.fermata.addon.stremio.playback.PlaybackDescriptor descriptor,
			String type, String videoId) {
		return items.subtitles(descriptor, type, videoId);
	}

	@Override
	public FutureSupplier<SubtitleAggregationResult> subtitles(
			me.aap.fermata.addon.stremio.playback.PlaybackDescriptor descriptor,
			me.aap.fermata.addon.stremio.playback.ContentIdentitySet identities,
			String type, String videoId) {
		return items.subtitles(descriptor, identities, type, videoId);
	}

	@Override
	public FutureSupplier<byte[]> loadSubtitle(SubtitleDescriptor descriptor) {
		return items.loadSubtitle(descriptor);
	}

	@Override
	public FutureSupplier<Void> saveProgress(StremioPlaybackIdentity identity,
			long position, boolean completed) {
		return progressStore.save(identity, position, completed);
	}

	@Override
	public FutureSupplier<Void> saveProgress(StremioPlaybackIdentity identity,
			long position, boolean completed, long playbackGeneration) {
		return progressStore.save(identity, position, completed, playbackGeneration);
	}

	@Override
	public CompletionStage<StremioProviderState> getProviderState(String sourceUuid) {
		return favoriteStore.providerState(sourceUuid);
	}

	@Override
	public CompletionStage<Void> synchronizeFavorite(StremioFavoriteUpdate update) {
		return favoriteStore.synchronize(update);
	}

	@Override
	public CompletionStage<Void> writeProgress(StremioProgressSnapshot snapshot) {
		return progressStore.write(snapshot);
	}

	@Override
	public CompletionStage<Void> saveRestorePoint(StremioRestorePoint restorePoint) {
		return restoreStore.save(restorePoint);
	}

	@Override
	public CompletionStage<StremioRestorePoint> loadRestorePoint() {
		return restoreStore.load();
	}

	@Override
	public CompletionStage<List<StremioSessionItem>> loadEpisodeQueue(String episodeQueueId) {
		return projectionStore.loadEpisodeQueue(episodeQueueId);
	}

	@Override
	public CompletionStage<List<StremioVoiceCandidate>> search(
			String normalizedQuery, Locale locale, int limit) {
		return voiceSearch.search(normalizedQuery, locale, limit);
	}

	/** Resolves a durable content/video ID without traversing provider catalogs. */
	public FutureSupplier<Item> resolveMediaItem(
			DefaultMediaLib lib, StremioRootItem root, String stableId) {
		return mediaResolver.resolve(lib, root, stableId, null);
	}

	@Override
	public FutureSupplier<PlayableItem> adjacentPlayback(
			StremioDirectPlayableItem currentItem, boolean next) {
		return adjacentResolver.resolve(currentItem, next);
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		progressStore.close();
		if (items instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
			}
		}
	}

}
