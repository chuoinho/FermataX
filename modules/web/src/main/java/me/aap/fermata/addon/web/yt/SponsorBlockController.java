package me.aap.fermata.addon.web.yt;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Caches requests and shares one native SponsorBlock fetch among concurrent consumers. */
public final class SponsorBlockController implements AutoCloseable {
	static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L;
	static final long EMPTY_CACHE_TTL_MS = 5 * 60 * 1000L;
	static final int MAX_CACHE_ENTRIES = 100;
	private final Source source;
	private final LongSupplier clock;
	private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
	private final Map<String, Flight> flights = new LinkedHashMap<>();
	private boolean closed;

	public SponsorBlockController() {
		this(new SponsorBlockClient()::getSegments, System::currentTimeMillis);
	}

	SponsorBlockController(Source source, LongSupplier clock) {
		this.source = Objects.requireNonNull(source);
		this.clock = Objects.requireNonNull(clock);
	}

	public FutureSupplier<List<SponsorBlockClient.Segment>> getSegments(
			SponsorBlockClient.Request request) {
		Objects.requireNonNull(request);
		String key = request.cacheKey();
		long now = clock.getAsLong();
		Flight flight;
		FutureSupplier<List<SponsorBlockClient.Segment>> result;
		synchronized (cache) {
			if (closed) return failed(new CancellationException("SponsorBlock controller is closed"));
			CacheEntry entry = cache.get(key);
			if ((entry != null) && (entry.expiresAt > now)) return completed(entry.segments);
			if (entry != null) cache.remove(key);
			Flight existing = flights.get(key);
			if (existing != null) {
				if (!existing.isUnusable()) return existing.acquire();
				flights.remove(key, existing);
			}
			flight = new Flight();
			flights.put(key, flight);
			result = flight.acquire();
		}

		flight.future().onCompletion((segments, error) -> {
			synchronized (cache) {
				flights.remove(key, flight);
				if (!closed && (error == null)) {
					long ttl = segments.isEmpty() ? EMPTY_CACHE_TTL_MS : CACHE_TTL_MS;
					cache.put(key, new CacheEntry(List.copyOf(segments),
							clock.getAsLong() + ttl));
					trimCache();
				}
			}
		});

		try {
			flight.start(Objects.requireNonNull(source.load(request)));
		} catch (Throwable error) {
			flight.fail(error);
		}
		return result;
	}

	public void invalidate(SponsorBlockClient.Request request) {
		synchronized (cache) {
			cache.remove(request.cacheKey());
		}
	}

	@Override
	public void close() {
		synchronized (cache) {
			if (closed) return;
			closed = true;
			cache.clear();
			for (Flight flight : flights.values()) flight.cancel();
			flights.clear();
		}
	}

	private void trimCache() {
		while (cache.size() > MAX_CACHE_ENTRIES) {
			Iterator<String> keys = cache.keySet().iterator();
			keys.next();
			keys.remove();
		}
	}

	interface Source {
		FutureSupplier<List<SponsorBlockClient.Segment>> load(SponsorBlockClient.Request request);
	}

	private record CacheEntry(List<SponsorBlockClient.Segment> segments, long expiresAt) {
	}

	private static final class Flight {
		private final Promise<List<SponsorBlockClient.Segment>> future = new Promise<>();
		private FutureSupplier<List<SponsorBlockClient.Segment>> upstream;
		private int subscribers;
		private boolean cancelRequested;

		FutureSupplier<List<SponsorBlockClient.Segment>> future() {
			return future;
		}

		void start(FutureSupplier<List<SponsorBlockClient.Segment>> upstream) {
			boolean cancel;
			boolean duplicate;
			synchronized (this) {
				duplicate = this.upstream != null;
				if (!duplicate) this.upstream = upstream;
				cancel = cancelRequested || future.isCancelled();
			}
			if (duplicate) {
				upstream.cancel();
				return;
			}
			upstream.onCompletion((segments, error) -> future.complete(segments, error));
			if (cancel) upstream.cancel();
		}

		void fail(Throwable error) {
			future.completeExceptionally(error);
		}

		synchronized boolean isUnusable() {
			return future.isCancelled() || (future.isDone() && future.isFailed());
		}

		synchronized FutureSupplier<List<SponsorBlockClient.Segment>> acquire() {
			if (future.isDone()) return future;
			subscribers++;
			FlightPromise result = new FlightPromise(this);
			future.onCompletion(result::completeFromUpstream);
			return result;
		}

		void release() {
			FutureSupplier<List<SponsorBlockClient.Segment>> current;
			synchronized (this) {
				if ((subscribers == 0) || (--subscribers != 0) || future.isDone()) return;
				cancelRequested = true;
				current = upstream;
			}
			future.cancel();
			if (current != null) current.cancel();
		}

		void cancel() {
			FutureSupplier<List<SponsorBlockClient.Segment>> current;
			synchronized (this) {
				cancelRequested = true;
				current = upstream;
			}
			future.cancel();
			if (current != null) current.cancel();
		}
	}

	private static final class FlightPromise extends Promise<List<SponsorBlockClient.Segment>> {
		private Flight flight;

		FlightPromise(Flight flight) {
			this.flight = flight;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			boolean cancelled = super.cancel(mayInterruptIfRunning);
			release();
			return cancelled;
		}

		void completeFromUpstream(List<SponsorBlockClient.Segment> segments, Throwable error) {
			complete(segments, error);
			release();
		}

		private void release() {
			Flight current = flight;
			if (current == null) return;
			flight = null;
			current.release();
		}
	}
}
