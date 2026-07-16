package me.aap.fermata.addon.podcast.provider;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import me.aap.fermata.addon.podcast.model.PodcastSearchRequest;
import me.aap.fermata.addon.podcast.model.PodcastSearchResult;
import me.aap.fermata.addon.podcast.net.PodcastHttpClient;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public final class PodcastSearchCoordinator implements AutoCloseable {
	private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
	private static final long FAILURE_WINDOW_MS = 10 * 60 * 1000L;
	private static final long SECONDARY_OPEN_MS = 30 * 60 * 1000L;
	private final PodcastSearchProvider primary;
	private final PodcastSearchProvider secondary;
	private final Map<String, CacheEntry> cache = new LinkedHashMap<>();
	private final Map<String, SearchFlight> flights = new LinkedHashMap<>();
	private int secondaryFailures;
	private long secondaryFailureWindow;
	private long secondaryOpenUntil;
	private boolean closed;

	public PodcastSearchCoordinator() {
		PodcastHttpClient http = new PodcastHttpClient();
		primary = new AppleSearchProvider(http);
		secondary = new FyydSearchProvider(http);
	}

	PodcastSearchCoordinator(PodcastSearchProvider primary, PodcastSearchProvider secondary) {
		this.primary = primary;
		this.secondary = secondary;
	}

	public FutureSupplier<List<PodcastSearchResult>> search(PodcastSearchRequest request) {
		if (request.getQuery().isEmpty()) return completedEmptyList();
		String key = request.cacheKey();
		long now = System.currentTimeMillis();
		synchronized (cache) {
			if (closed) return failed(new CancellationException("Podcast search stopped"));
			CacheEntry entry = cache.get(key);
			if ((entry != null) && (entry.expiresAt > now)) return completed(entry.results);
			if (entry != null) cache.remove(key);
			SearchFlight flight = flights.get(key);
			if (flight != null) return flight.acquire();
		}

		FutureSupplier<List<PodcastSearchResult>> source = primary.search(request).then(results -> {
			List<PodcastSearchResult> normalized = dedupe(results, request.getLimit());
			if (!normalized.isEmpty()) return completed(normalized);
			return fallback(request, null);
		}, primaryError -> {
			if (isCancellation(primaryError)) return failed(primaryError);
			return fallback(request, primaryError);
		});
		SearchFlight flight = new SearchFlight(source);
		synchronized (cache) {
			if (closed) {
				source.cancel();
				return failed(new CancellationException("Podcast search stopped"));
			}
			SearchFlight existing = flights.putIfAbsent(key, flight);
			if (existing != null) {
				source.cancel();
				return existing.acquire();
			}
		}
		source.onCompletion((results, error) -> {
			synchronized (cache) {
				flights.remove(key, flight);
				if (!closed && (error == null)) {
					cache.put(key, new CacheEntry(List.copyOf(results),
							System.currentTimeMillis() + CACHE_TTL_MS));
				}
			}
		});
		return source.isDone() ? source : flight.acquire();
	}

	public void invalidate(PodcastSearchRequest request) {
		synchronized (cache) {
			cache.remove(request.cacheKey());
		}
	}

	@Override
	public void close() {
		synchronized (cache) {
			closed = true;
			cache.clear();
			for (SearchFlight flight : flights.values()) flight.cancel();
			flights.clear();
		}
	}

	private FutureSupplier<List<PodcastSearchResult>> fallback(PodcastSearchRequest request,
			Throwable primaryError) {
		long now = System.currentTimeMillis();
		synchronized (cache) {
			if (secondaryOpenUntil > now) {
				return (primaryError == null) ? completedEmptyList() : failed(primaryError);
			}
		}

		return secondary.search(request).then(results -> {
			recordSecondarySuccess();
			return completed(dedupe(results, request.getLimit()));
		}, secondaryError -> {
			if (isCancellation(secondaryError)) return failed(secondaryError);
			recordSecondaryFailure();
			return (primaryError == null) ? completedEmptyList() : failed(primaryError);
		});
	}

	private void recordSecondarySuccess() {
		synchronized (cache) {
			secondaryFailures = 0;
			secondaryFailureWindow = 0;
			secondaryOpenUntil = 0;
		}
	}

	private void recordSecondaryFailure() {
		long now = System.currentTimeMillis();
		synchronized (cache) {
			if ((secondaryFailureWindow == 0) ||
					((now - secondaryFailureWindow) > FAILURE_WINDOW_MS)) {
				secondaryFailureWindow = now;
				secondaryFailures = 1;
			} else {
				secondaryFailures++;
			}
			if (secondaryFailures >= 3) secondaryOpenUntil = now + SECONDARY_OPEN_MS;
		}
	}

	private static List<PodcastSearchResult> dedupe(List<PodcastSearchResult> input, int limit) {
		Map<String, PodcastSearchResult> unique = new LinkedHashMap<>();
		if (input != null) {
			for (PodcastSearchResult result : input) {
				if ((result == null) || result.dedupeKey().isEmpty()) continue;
				unique.putIfAbsent(result.dedupeKey(), result);
				if (unique.size() >= limit) break;
			}
		}
		return new ArrayList<>(unique.values());
	}

	private record CacheEntry(List<PodcastSearchResult> results, long expiresAt) {
	}

	private final class SearchFlight {
		private final FutureSupplier<List<PodcastSearchResult>> source;
		private int consumers;

		SearchFlight(FutureSupplier<List<PodcastSearchResult>> source) {
			this.source = source;
		}

		synchronized FutureSupplier<List<PodcastSearchResult>> acquire() {
			if (source.isDone()) return source;
			consumers++;
			FlightPromise promise = new FlightPromise(this);
			source.onCompletion(promise::completeFromSource);
			return promise;
		}

		synchronized void release() {
			if ((consumers > 0) && (--consumers == 0) && !source.isDone()) source.cancel();
		}

		void cancel() {
			source.cancel();
		}
	}

	private static final class FlightPromise extends Promise<List<PodcastSearchResult>> {
		private SearchFlight flight;

		FlightPromise(SearchFlight flight) {
			this.flight = flight;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			boolean cancelled = super.cancel(mayInterruptIfRunning);
			release();
			return cancelled;
		}

		void completeFromSource(List<PodcastSearchResult> result, Throwable error) {
			complete(result, error);
			release();
		}

		private void release() {
			SearchFlight current = flight;
			if (current == null) return;
			flight = null;
			current.release();
		}
	}
}
