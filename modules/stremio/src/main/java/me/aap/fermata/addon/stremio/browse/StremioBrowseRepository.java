package me.aap.fermata.addon.stremio.browse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.protocol.CapabilityMatcher;
import me.aap.fermata.addon.stremio.protocol.model.CatalogCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.protocol.response.StremioMeta;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseException;
import me.aap.fermata.addon.stremio.protocol.response.StremioResponseParser;
import me.aap.fermata.addon.stremio.protocol.response.StremioVideo;

/** Pure browse/search/details domain. Android UI and persistence stay outside this boundary. */
public final class StremioBrowseRepository implements AutoCloseable {
	public static final int DEFAULT_MAX_CONCURRENCY = 4;
	public static final int DEFAULT_MAX_PAGE_ITEMS = 100;
	public static final int DEFAULT_MAX_SEARCH_RESULTS = 200;
	public static final long DEFAULT_SEARCH_DEBOUNCE_MILLIS = 300;

	private final StremioBrowseGateway gateway;
	private final Executor parserExecutor;
	private final ScheduledExecutorService scheduler;
	private final int maxConcurrency;
	private final int maxPageItems;
	private final int maxSearchResults;
	private final long searchDebounceMillis;
	private final RequestGeneration searchGeneration = new RequestGeneration();
	private final Object searchLock = new Object();
	private BrowseOperation<SearchResults> activeSearch;
	private ScheduledFuture<?> scheduledSearch;
	private boolean closed;

	public StremioBrowseRepository(StremioBrowseGateway gateway, Executor parserExecutor,
			ScheduledExecutorService scheduler) {
		this(gateway, parserExecutor, scheduler, DEFAULT_MAX_CONCURRENCY,
				DEFAULT_MAX_PAGE_ITEMS, DEFAULT_MAX_SEARCH_RESULTS,
				DEFAULT_SEARCH_DEBOUNCE_MILLIS);
	}

	public StremioBrowseRepository(StremioBrowseGateway gateway, Executor parserExecutor,
			ScheduledExecutorService scheduler, int maxConcurrency, int maxPageItems,
			int maxSearchResults, long searchDebounceMillis) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.parserExecutor = Objects.requireNonNull(parserExecutor, "parserExecutor");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		if ((maxConcurrency < 1) || (maxPageItems < 1) || (maxSearchResults < 1) ||
				(searchDebounceMillis < 0)) {
			throw new IllegalArgumentException("Browse limits must be positive");
		}
		this.maxConcurrency = maxConcurrency;
		this.maxPageItems = maxPageItems;
		this.maxSearchResults = maxSearchResults;
		this.searchDebounceMillis = searchDebounceMillis;
	}

	public List<CatalogDescriptor> catalogs(List<BrowseProvider> providers) {
		var result = new ArrayList<CatalogDescriptor>();
		orderedEnabled(providers).forEach(provider -> {
			var catalogs = provider.manifest().catalogs();
			for (int i = 0; i < catalogs.size(); i++) {
				var catalog = catalogs.get(i);
				result.add(descriptor(provider, catalog, i));
			}
		});
		return List.copyOf(result);
	}

	public BrowseOperation<CatalogPage> loadCatalog(List<BrowseProvider> providers,
			CatalogRoute route, String genre, int skip, CatalogPage previous) {
		return loadCatalog(providers, route, genre, skip, Map.of(), previous);
	}

	public BrowseOperation<CatalogPage> loadCatalog(List<BrowseProvider> providers,
			CatalogRoute route, String genre, int skip,
			Map<String, List<String>> requestedExtras, CatalogPage previous) {
		Objects.requireNonNull(route, "route");
		Objects.requireNonNull(requestedExtras, "requestedExtras");
		if (skip < 0) throw new IllegalArgumentException("skip cannot be negative");
		var provider = exactProvider(providers, route.sourceUuid());
		var catalog = provider.flatMap(p -> p.manifest().catalog(route.type(), route.catalogId()));
		if (provider.isEmpty() || catalog.isEmpty()) {
			return completedFailure(previous, route.sourceUuid(), "catalog-route", false);
		}

		var extras = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, List<String>> entry : requestedExtras.entrySet()) {
			String name = Objects.requireNonNull(entry.getKey(), "extra name");
			if (name.isBlank() || name.equalsIgnoreCase("genre") ||
					name.equalsIgnoreCase("skip")) {
				throw new IllegalArgumentException("Invalid catalog extra: " + name);
			}
			List<String> values = List.copyOf(
					Objects.requireNonNull(entry.getValue(), "extra values"));
			if (!values.isEmpty()) extras.put(name, values);
		}
		if ((genre != null) && !genre.isBlank()) extras.put("genre", genre);
		if (skip > 0) extras.put("skip", Integer.toString(skip));
		var request = new StremioRequest("catalog", route.type(), route.catalogId(), extras);
		if (!CapabilityMatcher.supports(provider.get().manifest(), request)) {
			return completedFailure(previous, route.sourceUuid(), "catalog-capability", false);
		}

		var scope = requestScope();
		var token = scope.token();
		var result = invokeGateway(provider.get(), request, token).thenApplyAsync(payload -> {
			token.throwIfStale();
			var metas = StremioResponseParser.parseCatalog(payload.body()).metas();
			if (metas.stream().anyMatch(meta -> !meta.type().equals(route.type()))) {
				throw new BrowseGatewayException("Catalog type mismatch", false);
			}
			var bounded = metas.subList(0, Math.min(metas.size(), maxPageItems));
			var items = normalize(provider.get().sourceUuid(), bounded);
			// The Stremio protocol does not expose a total count or a provider page size.
			// A short non-empty response may still have another page through the skip extra.
			boolean hasNext = !items.isEmpty() && catalog.get().extra("skip").isPresent();
			var page = new CatalogPage(descriptor(provider.get(), catalog.get(),
					provider.get().manifest().catalogs().indexOf(catalog.get())), genre, skip,
					skip + items.size(), hasNext, items);
			return items.isEmpty() ? new BrowseLoadState.Empty<CatalogPage>(payload.stale(), List.of())
					: terminal(page, payload.stale(), List.of());
		}, parserExecutor).exceptionally(error -> failure(route.sourceUuid(), "catalog", error));
		return operation(previous, result, scope.generation()::close);
	}

	public BrowseOperation<BrowseDetails> loadDetails(List<BrowseProvider> providers,
			String sourceUuid, String type, String id, BrowseDetails previous) {
		var request = new StremioRequest("meta", type, id);
		List<BrowseProvider> candidates = orderedEnabled(providers).stream()
				.filter(provider -> CapabilityMatcher.supports(provider.manifest(), request))
				.sorted(Comparator.comparing((BrowseProvider provider) ->
						!provider.sourceUuid().equals(sourceUuid))
						.thenComparingInt(BrowseProvider::position))
				.toList();
		if (candidates.isEmpty()) {
			return completedFailure(previous, sourceUuid, "meta-capability", false);
		}

		var scope = requestScope();
		var token = scope.token();
		CompletableFuture<BrowseLoadState<BrowseDetails>> result = new CompletableFuture<>();
		loadMetaCandidate(candidates, 0, sourceUuid, type, id, request, token, null, result);
		return operation(previous, result, scope.generation()::close);
	}

	private void loadMetaCandidate(List<BrowseProvider> candidates, int index,
			String sourceUuid, String type, String id, StremioRequest request,
			RequestGeneration.Token token, Throwable previousFailure,
			CompletableFuture<BrowseLoadState<BrowseDetails>> target) {
		if (target.isDone()) return;
		try {
			token.throwIfStale();
		} catch (CancellationException cancelled) {
			target.completeExceptionally(cancelled);
			return;
		}
		if (index >= candidates.size()) {
			target.complete((previousFailure == null) ?
					new BrowseLoadState.Failure<>(List.of(new ProviderFailure(
							sourceUuid, "meta", false))) :
					failure(sourceUuid, "meta", previousFailure));
			return;
		}
		BrowseProvider provider = candidates.get(index);
		invokeGateway(provider, request, token).whenCompleteAsync((payload, error) -> {
			if (error != null) {
				loadMetaCandidate(candidates, index + 1, sourceUuid, type, id, request,
						token, error, target);
				return;
			}
			try {
				token.throwIfStale();
				StremioMeta meta = StremioResponseParser.parseMeta(payload.body()).meta();
				if (!meta.type().equals(type) || !meta.id().equals(id)) {
					throw new BrowseGatewayException("Meta identity mismatch", false);
				}
				target.complete(terminal(details(sourceUuid, meta), payload.stale(), List.of()));
			} catch (Throwable invalid) {
				loadMetaCandidate(candidates, index + 1, sourceUuid, type, id, request,
						token, invalid, target);
			}
		}, parserExecutor);
	}

	public BrowseOperation<SearchResults> search(List<BrowseProvider> providers, String query,
			SearchResults previous) {
		Objects.requireNonNull(query, "query");
		synchronized (searchLock) {
			ensureOpen();
			if (scheduledSearch != null) scheduledSearch.cancel(false);
			if (activeSearch != null) activeSearch.cancel();
			var token = searchGeneration.begin();
			var future = new CompletableFuture<BrowseLoadState<SearchResults>>();
			var operation = operation(previous, future, () -> {
				if (token.isCurrent()) searchGeneration.cancelAll();
			});
			activeSearch = operation;
			if (query.isBlank()) {
				future.complete(new BrowseLoadState.Empty<>(false, List.of()));
				return operation;
			}
			scheduledSearch = scheduler.schedule(
					() -> executeSearch(providers, query, token, future),
					searchDebounceMillis, TimeUnit.MILLISECONDS);
			return operation;
		}
	}

	private void executeSearch(List<BrowseProvider> providers, String query,
			RequestGeneration.Token token,
			CompletableFuture<BrowseLoadState<SearchResults>> target) {
		if (!token.isCurrent() || target.isCancelled()) return;
		var calls = new ArrayList<SearchCall>();
		for (var provider : orderedEnabled(providers)) {
			for (var catalog : provider.manifest().catalogs()) {
				var request = new StremioRequest("catalog", catalog.type(), catalog.id(),
						Map.of("search", query));
				if (CapabilityMatcher.supports(provider.manifest(), request)) {
					calls.add(new SearchCall(provider, request));
				}
			}
		}
		if (calls.isEmpty()) {
			target.complete(new BrowseLoadState.Empty<>(false, List.of()));
			return;
		}

		var jobs = calls.stream().<Supplier<CompletionStage<SearchOutcome>>>map(call -> () ->
				invokeGateway(call.provider(), call.request(), token).thenApplyAsync(payload -> {
					token.throwIfStale();
					var metas = StremioResponseParser.parseCatalog(payload.body()).metas();
					if (metas.stream().anyMatch(meta ->
							!meta.type().equals(call.request().type()))) {
						throw new BrowseGatewayException("Search catalog type mismatch", false);
					}
					return SearchOutcome.success(
							normalize(call.provider().sourceUuid(), metas), payload.stale());
				}, parserExecutor).exceptionally(error -> SearchOutcome.failed(
						call.provider().sourceUuid(), retryable(error)))).toList();

		bounded(jobs).whenComplete((outcomes, error) -> {
			if (!token.isCurrent() || target.isCancelled()) return;
			if (error != null) {
				target.complete(failure("search", "search", error));
				return;
			}
			var items = new LinkedHashMap<String, BrowseMedia>();
			var failures = new ArrayList<ProviderFailure>();
			boolean stale = false;
			for (var outcome : outcomes) {
				stale |= outcome.stale();
				if (outcome.failure() != null) failures.add(outcome.failure());
				for (var item : outcome.items()) {
					if (items.size() >= maxSearchResults) break;
					items.putIfAbsent(item.scopedId(), item);
				}
			}
			var result = new SearchResults(query, List.copyOf(items.values()));
			target.complete(result.items().isEmpty()
					? failures.isEmpty() ? new BrowseLoadState.Empty<>(stale, List.of())
						: new BrowseLoadState.Failure<>(failures)
					: new BrowseLoadState.Content<>(result, stale, failures));
		});
	}

	private <T> CompletableFuture<List<T>> bounded(
			List<Supplier<CompletionStage<T>>> jobs) {
		var result = new CompletableFuture<List<T>>();
		var runner = new BoundedRunner<T>(jobs, maxConcurrency, result);
		runner.start();
		return result;
	}

	private final class BoundedRunner<T> {
		private final ArrayDeque<IndexedJob<T>> pending = new ArrayDeque<>();
		private final Object[] values;
		private final int limit;
		private final CompletableFuture<List<T>> result;
		private int active;
		private int completed;

		BoundedRunner(List<Supplier<CompletionStage<T>>> jobs, int limit,
				CompletableFuture<List<T>> result) {
			this.limit = limit;
			this.result = result;
			this.values = new Object[jobs.size()];
			for (int i = 0; i < jobs.size(); i++) pending.add(new IndexedJob<>(i, jobs.get(i)));
		}

		void start() {
			synchronized (this) {
				launch();
			}
		}

		private void launch() {
			while ((active < limit) && !pending.isEmpty() && !result.isDone()) {
				var job = pending.removeFirst();
				active++;
				CompletionStage<T> stage;
				try {
					stage = job.task().get();
				} catch (Throwable error) {
					finished(job.index(), null, error);
					continue;
				}
				stage.whenCompleteAsync(
						(value, error) -> finished(job.index(), value, error), parserExecutor);
			}
			if ((completed == values.length) && !result.isDone()) {
				var ordered = new ArrayList<T>(values.length);
				for (var value : values) {
					@SuppressWarnings("unchecked") var cast = (T) value;
					ordered.add(cast);
				}
				result.complete(List.copyOf(ordered));
			}
		}

		private synchronized void finished(int index, T value, Throwable error) {
			active--;
			completed++;
			if (error != null) {
				result.completeExceptionally(unwrap(error));
				return;
			}
			values[index] = value;
			launch();
		}
	}

	private BrowseDetails details(String sourceUuid, StremioMeta meta) {
		var media = normalize(sourceUuid, meta);
		var unique = new LinkedHashMap<String, StremioVideo>();
		for (var video : meta.videos()) unique.putIfAbsent(video.id(), video);
		var sorted = new ArrayList<>(unique.values());
		sorted.sort(Comparator
				.comparingInt((StremioVideo video) -> optionalNumber(video.season()))
				.thenComparingInt(video -> optionalNumber(video.episode()))
				.thenComparing(StremioVideo::title)
				.thenComparing(StremioVideo::id));
		var grouped = new TreeMap<Integer, List<BrowseEpisode>>();
		for (var video : sorted) {
			int season = optionalNumber(video.season());
			grouped.computeIfAbsent(season, ignored -> new ArrayList<>()).add(
					new BrowseEpisode(sourceUuid, meta.type(), meta.id(), video.id(),
					video.title(), season, optionalNumber(video.episode()), video.released(),
					video.thumbnail(), video.overview(), video.duration()));
		}
		var immutable = grouped.entrySet().stream()
				.map(entry -> new BrowseSeason(entry.getKey(), entry.getValue()))
				.toList();
		return new BrowseDetails(media, immutable);
	}

	private static int optionalNumber(Integer value) {
		return value == null ? 0 : value;
	}

	private static List<BrowseMedia> normalize(String sourceUuid, List<StremioMeta> metas) {
		var unique = new LinkedHashMap<String, BrowseMedia>();
		for (var meta : metas) {
			var item = normalize(sourceUuid, meta);
			unique.putIfAbsent(item.scopedId(), item);
		}
		return List.copyOf(unique.values());
	}

	private static BrowseMedia normalize(String sourceUuid, StremioMeta meta) {
		return new BrowseMedia(sourceUuid, meta.type(), meta.id(), meta.name(), meta.poster(),
				meta.background(), meta.description(), meta.releaseInfo(), meta.imdbRating(), meta.runtime(),
				meta.genres(), meta.language());
	}

	private static CatalogDescriptor descriptor(BrowseProvider provider,
			CatalogCapability catalog, int catalogPosition) {
		var genres = catalog.extra("genre").map(extra -> extra.options()).orElse(List.of());
		return new CatalogDescriptor(new CatalogRoute(provider.sourceUuid(), catalog.type(),
				catalog.id()), provider.displayName(), catalog.displayName(), genres,
				catalog.extra("search").isPresent(), provider.position(), catalogPosition,
				catalog.extras());
	}

	private static List<BrowseProvider> orderedEnabled(List<BrowseProvider> providers) {
		Objects.requireNonNull(providers, "providers");
		return providers.stream().filter(BrowseProvider::enabled)
				.sorted(Comparator.comparingInt(BrowseProvider::position))
				.toList();
	}

	private static Optional<BrowseProvider> exactProvider(
			List<BrowseProvider> providers, String sourceUuid) {
		return orderedEnabled(providers).stream()
				.filter(provider -> provider.sourceUuid().equals(sourceUuid)).findFirst();
	}

	private RequestScope requestScope() {
		synchronized (searchLock) {
			ensureOpen();
			var generation = new RequestGeneration();
			return new RequestScope(generation, generation.begin());
		}
	}

	private CompletionStage<BrowsePayload> invokeGateway(BrowseProvider provider,
			StremioRequest request, RequestGeneration.Token token) {
		return CompletableFuture.supplyAsync(() -> gateway.get(provider, request, token),
				parserExecutor).thenCompose(stage -> stage);
	}

	private <T> BrowseOperation<T> operation(T previous,
			CompletionStage<BrowseLoadState<T>> stage, Runnable cancellation) {
		var result = stage.toCompletableFuture();
		return new BrowseOperation<>(new BrowseLoadState.Loading<>(previous, previous != null),
				result, cancellation);
	}

	private <T> BrowseOperation<T> completedFailure(T previous, String sourceUuid,
			String operation, boolean retryable) {
		var state = new BrowseLoadState.Failure<T>(
				List.of(new ProviderFailure(sourceUuid, operation, retryable)));
		return new BrowseOperation<>(new BrowseLoadState.Loading<>(previous, previous != null),
				CompletableFuture.completedFuture(state), () -> {
				});
	}

	private static <T> BrowseLoadState<T> terminal(
			T value, boolean stale, List<ProviderFailure> failures) {
		return new BrowseLoadState.Content<>(value, stale, failures);
	}

	private static <T> BrowseLoadState<T> failure(
			String sourceUuid, String operation, Throwable error) {
		var cause = unwrap(error);
		if (cause instanceof CancellationException) throw (CancellationException) cause;
		return new BrowseLoadState.Failure<>(List.of(
				new ProviderFailure(sourceUuid, operation, retryable(cause))));
	}

	private static boolean retryable(Throwable error) {
		var cause = unwrap(error);
		if (cause instanceof BrowseGatewayException gatewayError) return gatewayError.retryable();
		return !(cause instanceof IllegalArgumentException) &&
				!(cause instanceof StremioResponseException);
	}

	private static Throwable unwrap(Throwable error) {
		while (((error instanceof CompletionException) ||
				(error instanceof java.util.concurrent.ExecutionException)) &&
				(error.getCause() != null)) error = error.getCause();
		return error;
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Browse repository is closed");
	}

	@Override
	public void close() {
		synchronized (searchLock) {
			if (closed) return;
			closed = true;
			if (scheduledSearch != null) scheduledSearch.cancel(false);
			if (activeSearch != null) activeSearch.cancel();
			searchGeneration.close();
		}
	}

	private record SearchCall(BrowseProvider provider, StremioRequest request) {
	}

	private record SearchOutcome(
			List<BrowseMedia> items, boolean stale, ProviderFailure failure) {
		static SearchOutcome success(List<BrowseMedia> items, boolean stale) {
			return new SearchOutcome(items, stale, null);
		}

		static SearchOutcome failed(String sourceUuid, boolean retryable) {
			return new SearchOutcome(List.of(), false,
					new ProviderFailure(sourceUuid, "search", retryable));
		}
	}

	private record IndexedJob<T>(int index, Supplier<CompletionStage<T>> task) {
	}

	private record RequestScope(
			RequestGeneration generation, RequestGeneration.Token token) {
	}
}
