package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioFutureBridge.toCompletable;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseLoadState;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.browse.SearchResults;
import me.aap.fermata.addon.stremio.browse.StremioBrowseRepository;
import me.aap.fermata.addon.stremio.data.StremioMetaProviderRecord;
import me.aap.fermata.addon.stremio.data.StremioMetaRecord;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioVideoRecord;
import me.aap.fermata.addon.stremio.item.StremioCanonicalIdentity;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioStreamRequestFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationCall;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.playback.StreamAggregator;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregationResult;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.media.net.PlaybackHeaderResolver;
import me.aap.fermata.media.net.PlaybackRequestProfile.HeaderReference;
import me.aap.fermata.media.net.PlaybackRequestValidationException;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.addon.stremio.torrent.StremioTorrentEngine;
import me.aap.utils.async.FutureSupplier;

/** Production MediaLib gateway over the bounded Stremio domain services. */
public final class StremioItemGatewayAdapter implements StremioItemGateway,
		PlaybackHeaderResolver, AutoCloseable {
	private static final int MAX_DESCRIPTOR_REQUESTS = 256;
	private final StremioProviderCatalog providerCatalog;
	private final StremioBrowseRepository browse;
	private final StreamAggregator streamAggregator;
	private final StremioRepository repository;
	private final Executor executor;
	private final PlaybackHeaderResolver headerResolver;
	private final StremioSubtitlePlaybackBridge subtitles;
	private final StremioTorrentEngine torrents;
	private final Map<String, RequestContext> descriptorRequests =
			Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(
						Map.Entry<String, RequestContext> eldest) {
					return size() > MAX_DESCRIPTOR_REQUESTS;
				}
			});
	private volatile boolean closed;

	public StremioItemGatewayAdapter(StremioProviderCatalog providerCatalog,
			StremioBrowseRepository browse, StreamAggregator streamAggregator,
			StremioRepository repository, Executor executor,
			PlaybackHeaderResolver headerResolver,
			StremioSubtitlePlaybackBridge subtitles,
			StremioTorrentEngine torrents) {
		this.providerCatalog = Objects.requireNonNull(providerCatalog, "providerCatalog");
		this.browse = Objects.requireNonNull(browse, "browse");
		this.streamAggregator = Objects.requireNonNull(streamAggregator, "streamAggregator");
		this.repository = Objects.requireNonNull(repository, "repository");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.headerResolver = Objects.requireNonNull(headerResolver, "headerResolver");
		this.subtitles = Objects.requireNonNull(subtitles, "subtitles");
		this.torrents = Objects.requireNonNull(torrents, "torrents");
	}

	@Override
	public Map<String, String> resolve(HeaderReference reference)
			throws PlaybackRequestValidationException {
		if (closed) {
			throw new PlaybackRequestValidationException("Stremio runtime is closed");
		}
		return headerResolver.resolve(reference);
	}

	@Override
	public FutureSupplier<List<BrowseProvider>> providers() {
		return bridge(open(providerCatalog.browseProviders().toCompletableFuture()));
	}

	@Override
	public FutureSupplier<List<CatalogDescriptor>> catalogs() {
		return bridge(open(providerCatalog.browseProviders().thenApplyAsync(
				browse::catalogs, executor).toCompletableFuture()));
	}

	@Override
	public FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid) {
		return bridge(open(providerCatalog.browseProviders().thenApplyAsync(providers ->
				browse.catalogs(providers).stream().filter(catalog ->
						catalog.route().sourceUuid().equals(sourceUuid)).toList(), executor)
				.toCompletableFuture()));
	}

	@Override
	public FutureSupplier<CatalogPage> catalog(CatalogRoute route,
			@Nullable String genre, int skip) {
		return catalog(route, genre, skip, Map.of());
	}

	@Override
	public FutureSupplier<CatalogPage> catalog(CatalogRoute route,
			@Nullable String genre, int skip, Map<String, List<String>> extras) {
		Objects.requireNonNull(extras, "extras");
		CompletableFuture<CatalogPage> result = providerCatalog.browseProviders()
				.thenComposeAsync(providers -> {
					CatalogDescriptor descriptor = browse.catalogs(providers).stream()
							.filter(catalog -> catalog.route().equals(route)).findFirst()
							.orElseThrow(() -> new IllegalStateException("Catalog is unavailable"));
					return browse.loadCatalog(providers, route, genre, skip, extras, null).result()
							.thenApply(state -> value(state,
									new CatalogPage(descriptor, genre, skip, skip,
											false, List.of())));
				}, executor).toCompletableFuture();
		return bridge(open(result));
	}

	@Override
	public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
		CompletableFuture<BrowseDetails> result = providerCatalog.browseProviders()
				.thenComposeAsync(providers -> browse.loadDetails(providers, media.sourceUuid(),
						media.type(), media.id(), null).result()
						.thenApply(state -> requiredValue(state, "Metadata is unavailable")), executor)
				.toCompletableFuture();
		return bridge(open(result));
	}

	@Override
	public FutureSupplier<String> preparePersistentItem(
			BrowseMedia media, @Nullable BrowseEpisode episode) {
		StreamAggregationRequest request = StremioStreamRequestFactory.create(media, episode);
		return bridge(repository.getVideo(request.identity().videoKey()).thenCompose(existing ->
				(existing == null) ? persistVideo(media.sourceUuid(), request) :
						CompletableFuture.completedFuture(null))
				.thenApply(ignored -> request.identity().videoKey()));
	}

	@Override
	public FutureSupplier<SearchResults> search(String query) {
		CompletableFuture<SearchResults> result = providerCatalog.browseProviders()
				.thenComposeAsync(providers -> browse.search(providers, query, null).result()
						.thenApply(state -> value(state, new SearchResults(query, List.of()))), executor)
				.toCompletableFuture();
		return bridge(open(result));
	}

	@Override
	public FutureSupplier<StreamAggregationResult> streams(StreamAggregationRequest request) {
		CompletableFuture<StreamAggregationResult> result = repository.getSourceState()
				.thenCompose(state -> {
					String sourceUuid = findSourceUuid(request, state.sources());
					if (sourceUuid == null) return CompletableFuture.failedFuture(
							new IllegalStateException(
									"Stremio playback source identity is unavailable"));
					return toCompletable(streams(sourceUuid, request));
				});
		return bridge(open(result));
	}

	@Override
	public FutureSupplier<StreamAggregationResult> streams(
			String sourceUuid, StreamAggregationRequest request) {
		return streams(sourceUuid, request, (ignored, error) -> {
		});
	}

	@Override
	public FutureSupplier<StreamAggregationResult> streams(String sourceUuid,
			StreamAggregationRequest request,
			BiConsumer<StreamAggregationResult, Throwable> terminalResult) {
		Objects.requireNonNull(terminalResult, "terminalResult");
		CompletableFuture<StreamAggregationResult> result = persistVideo(sourceUuid, request)
				.thenCompose(ignored -> providerCatalog.streamProviders(request))
				.thenComposeAsync(providers -> aggregate(
						sourceUuid, request, providers, terminalResult), executor)
				.toCompletableFuture();
		return bridge(open(result));
	}

	private CompletableFuture<StreamAggregationResult> aggregate(String sourceUuid,
			StreamAggregationRequest request,
			List<me.aap.fermata.addon.stremio.playback.StreamProvider> providers,
			BiConsumer<StreamAggregationResult, Throwable> terminalResult) {
		StreamAggregationCall call = streamAggregator.aggregate(request, providers);
		CompletableFuture<StreamAggregationResult> initial = call.response()
				.thenCompose(result -> persistAndRemember(sourceUuid, request, result));
		call.completion().thenCombine(initial, (finalResult, initialResult) ->
				new ResultPair(initialResult, finalResult))
				.thenCompose(pair -> requiresLatePublication(pair.initial(), pair.finalResult()) ?
						persistAndRemember(sourceUuid, request, pair.finalResult()) :
						CompletableFuture.completedFuture(pair.finalResult()))
				.whenComplete((finalResult, error) -> {
					if (closed) return;
					try {
						terminalResult.accept(finalResult, unwrap(error));
					} catch (RuntimeException ignored) {
						// UI invalidation cannot invalidate a completed provider aggregation.
					}
				});
		return initial;
	}

	private static Throwable unwrap(Throwable error) {
		while ((error instanceof java.util.concurrent.CompletionException ||
				error instanceof java.util.concurrent.ExecutionException) && error.getCause() != null) {
			error = error.getCause();
		}
		return error;
	}

	@Override
	public FutureSupplier<PlaybackDescriptor> resolve(
			PlaybackDescriptor.DescriptorRefreshRequest refresh) {
		RequestContext context = descriptorRequests.get(refresh.previousDescriptorId());
		if ((context == null) || !context.request().identity().equals(refresh.identity())) {
			return bridge(CompletableFuture.failedFuture(
					new IllegalStateException("Playback choice can no longer be resolved")));
		}
		CompletableFuture<PlaybackDescriptor> result = finalStreams(
				context.sourceUuid(), context.request())
				.thenApply(aggregation -> selectRefreshedDescriptor(
						aggregation.descriptors(), refresh));
		return bridge(result);
	}

	static PlaybackDescriptor selectRefreshedDescriptor(List<PlaybackDescriptor> descriptors,
			PlaybackDescriptor.DescriptorRefreshRequest refresh) {
		for (PlaybackDescriptor descriptor : descriptors) {
			if (descriptor.identity().equals(refresh.identity()) &&
					descriptor.providerSourceUuid().equals(refresh.providerSourceUuid()) &&
					descriptor.selectionFingerprint().equals(refresh.selectionFingerprint())) {
				return descriptor;
			}
		}
		throw new IllegalStateException(
				"The selected stream is no longer available; choose another stream");
	}

	private CompletableFuture<StreamAggregationResult> finalStreams(String sourceUuid,
			StreamAggregationRequest request) {
		return persistVideo(sourceUuid, request)
				.thenCompose(ignored -> providerCatalog.streamProviders(request))
				.thenComposeAsync(providers -> streamAggregator.aggregate(request, providers)
						.completion(), executor)
				.thenCompose(result -> persistAndRemember(sourceUuid, request, result));
	}

	@Override
	public FutureSupplier<PlaybackDescriptor> validatePlayback(PlaybackDescriptor descriptor) {
		if (closed) return bridge(CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed")));
		try {
			me.aap.fermata.addon.stremio.playback.DirectPlaybackValidator.validate(
					descriptor, System.currentTimeMillis());
		} catch (Throwable error) {
			return bridge(CompletableFuture.failedFuture(error));
		}
		CompletableFuture<PlaybackDescriptor> result = providerCatalog
				.isCurrent(descriptor.providerSnapshot()).thenApply(current -> {
					if (!current) throw new IllegalStateException(
							"Stremio provider changed before playback");
					return descriptor;
				}).toCompletableFuture();
		return bridge(result);
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> preparePlayback(PlaybackDescriptor descriptor) {
		return preparePlayback(descriptor, null);
	}

	@Override
	public FutureSupplier<RemotePlaybackRequest> preparePlayback(PlaybackDescriptor descriptor,
			java.util.function.Consumer<me.aap.fermata.media.net.RemotePlaybackProgress> progress) {
		return validatePlayback(descriptor).then(validated -> {
			if (validated.targetKind() == PlaybackDescriptor.TargetKind.TORRENT &&
					validated.sourceTarget() instanceof InfoHashStreamTarget target) {
				return bridge(torrents.prepare(target,
						validated.providerSnapshot().sourceLease(), progress).thenApply(prepared -> {
					PlaybackRequestProfile profile = PlaybackRequestProfile.builder(
							prepared.location(), validated.descriptorId())
							.redirectPolicy(PlaybackRequestProfile.RedirectPolicy.DENY)
							.requireCapability(PlaybackRequestProfile.EngineCapability.P2P_STREAMING)
							.build();
					return new RemotePlaybackRequest(prepared.location(), profile, null, null,
							() -> torrents.release(prepared));
				}));
			}
			if (validated.targetValue() == null || validated.requestProfile() == null) {
				return bridge(CompletableFuture.failedFuture(
						new IllegalStateException("Unsupported Stremio playback target")));
			}
			return bridge(CompletableFuture.completedFuture(new RemotePlaybackRequest(
					java.net.URI.create(validated.targetValue()), validated.requestProfile(),
					headerResolver, validated.endpointValidator())));
		});
	}

	@Override
	public FutureSupplier<SubtitleAggregationResult> subtitles(
			String type, String videoId) {
		return subtitles.resolve(type, videoId);
	}

	@Override
	public FutureSupplier<SubtitleAggregationResult> subtitles(
			PlaybackDescriptor descriptor, String type, String videoId) {
		return subtitles.resolve(descriptor, type, videoId);
	}

	@Override
	public FutureSupplier<SubtitleAggregationResult> subtitles(
			PlaybackDescriptor descriptor,
			me.aap.fermata.addon.stremio.playback.ContentIdentitySet identities,
			String type, String videoId) {
		return subtitles.resolve(descriptor, identities, type, videoId);
	}

	@Override
	public FutureSupplier<byte[]> loadSubtitle(SubtitleDescriptor descriptor) {
		return subtitles.load(descriptor);
	}

	@Override
	public FutureSupplier<Void> saveProgress(StremioPlaybackIdentity identity,
			long position, boolean completed) {
		return bridge(CompletableFuture.failedFuture(new IllegalStateException(
				"Stremio progress must be written through the session coordinator")));
	}

	@Override
	public void close() {
		closed = true;
		descriptorRequests.clear();
		providerCatalog.close();
	}

	private StreamAggregationResult remember(String sourceUuid, StreamAggregationRequest request,
			StreamAggregationResult result) {
		boolean hasTorrent = false;
		for (PlaybackDescriptor descriptor : result.descriptors()) {
			descriptorRequests.put(descriptor.descriptorId(),
					new RequestContext(sourceUuid, request));
			if (descriptor.targetKind() == PlaybackDescriptor.TargetKind.TORRENT) {
				hasTorrent = true;
			}
		}
		if (hasTorrent) torrents.warmUp().exceptionally(ignored -> null);
		return result;
	}

	private CompletableFuture<StreamAggregationResult> persistAndRemember(String sourceUuid,
			StreamAggregationRequest request, StreamAggregationResult result) {
		return persistProviders(request, result)
				.thenApply(ignored -> remember(sourceUuid, request, result));
	}

	private static boolean requiresLatePublication(StreamAggregationResult initial,
			StreamAggregationResult finalResult) {
		if (initial.hasPendingProviders() && !finalResult.hasPendingProviders()) return true;
		if (finalResult.descriptors().size() <= initial.descriptors().size()) return false;
		java.util.HashSet<String> initialIds = new java.util.HashSet<>();
		for (PlaybackDescriptor descriptor : initial.descriptors()) {
			initialIds.add(descriptor.descriptorId());
		}
		for (PlaybackDescriptor descriptor : finalResult.descriptors()) {
			if (!initialIds.contains(descriptor.descriptorId())) return true;
		}
		return false;
	}

	int descriptorRequestCount() {
		return descriptorRequests.size();
	}

	private CompletableFuture<Void> persistVideo(
			String sourceUuid, StreamAggregationRequest request) {
		return repository.getSource(sourceUuid).thenCompose(source -> {
			if ((source == null) || !source.enabled()) return CompletableFuture.failedFuture(
					new IllegalStateException("Stremio playback source is unavailable"));
			long now = System.currentTimeMillis();
			long duration = request.metadata().durationMillis();
			StremioCanonicalIdentity canonical =
					StremioCanonicalIdentity.from(request.type(), request.contentId());
			String scope = (canonical == null) ? sourceUuid : canonical.scope();
			String contentId = (canonical == null) ? request.contentId() :
					canonical.durableProviderId();
			String canonicalId = (canonical == null) ? null : canonical.contentId();
			StremioMetaRecord meta = new StremioMetaRecord(request.identity().contentKey(),
					scope, request.type(), contentId, canonicalId,
					request.metadata().title(), "", request.metadata().artwork(), null, null,
					null, duration, "[]", now);
			StremioMetaProviderRecord owner = new StremioMetaProviderRecord(
					request.identity().contentKey(), sourceUuid, request.contentId(),
					source.position(), now);
			StremioVideoRecord video = new StremioVideoRecord(request.identity().videoKey(),
					request.identity().contentKey(), request.type(), request.videoId(),
					request.metadata().title(),
					request.isEpisode() ? request.seasonNumber() : null,
					request.isEpisode() ? request.episodeNumber() : null, 0, duration,
					request.metadata().artwork(), now);
			return repository.putOwnedMeta(meta, owner)
					.thenCompose(ignored -> repository.putVideo(video));
		});
	}

	@Nullable
	static String findSourceUuid(StreamAggregationRequest request,
			List<me.aap.fermata.addon.stremio.data.StremioSourceRecord> sources) {
		if (StremioCanonicalIdentity.from(request.type(), request.contentId()) != null) {
			for (var source : sources) if (source.enabled()) return source.sourceUuid();
			return null;
		}
		for (var source : sources) {
			StremioPlaybackIdentity candidate = StremioPlaybackIdentity.scoped(
					source.sourceUuid(), request.type(), request.contentId(), request.videoId());
			if (candidate.equals(request.identity())) return source.sourceUuid();
		}
		return null;
	}

	private CompletableFuture<Void> persistProviders(StreamAggregationRequest request,
			StreamAggregationResult result) {
		long now = System.currentTimeMillis();
		CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
		for (var group : result.providerGroups()) {
			StremioMetaProviderRecord provider = new StremioMetaProviderRecord(
					request.identity().contentKey(), group.provider().sourceUuid(),
					request.contentId(), group.provider().position(), now);
			chain = chain.thenCompose(ignored -> repository.putMetaProvider(provider));
		}
		return chain;
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> stage) {
		if (closed) return CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed"));
		return stage;
	}

	private static <T> T value(BrowseLoadState<T> state, T empty) {
		if (state instanceof BrowseLoadState.Content<T> content) return content.value();
		if (state instanceof BrowseLoadState.Empty<T>) return empty;
		throw new IllegalStateException("Stremio provider request failed");
	}

	private static <T> T requiredValue(BrowseLoadState<T> state, String message) {
		if (state instanceof BrowseLoadState.Content<T> content) return content.value();
		throw new IllegalStateException(message);
	}

	private static <T> FutureSupplier<T> bridge(CompletableFuture<T> stage) {
		return StremioFutureBridge.from(stage);
	}
	private record RequestContext(String sourceUuid, StreamAggregationRequest request) {
	}

	private record ResultPair(
			StreamAggregationResult initial, StreamAggregationResult finalResult) {
	}
}
