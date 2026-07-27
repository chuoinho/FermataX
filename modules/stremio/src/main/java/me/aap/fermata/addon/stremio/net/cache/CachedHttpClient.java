package me.aap.fermata.addon.stremio.net.cache;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

import me.aap.fermata.addon.stremio.net.http.HttpCall;
import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.net.http.HttpRequestSpec;
import me.aap.fermata.addon.stremio.net.http.StremioHttpClient;

public final class CachedHttpClient {
	private final StremioHttpClient client;
	private final BoundedLruCache cache;
	private final LongSupplier clockMillis;
	private final SingleFlight<CacheKey, CachedResponse> requests = new SingleFlight<>();

	public CachedHttpClient(StremioHttpClient client, BoundedLruCache cache, LongSupplier clockMillis) {
		this.client = Objects.requireNonNull(client, "client");
		this.cache = Objects.requireNonNull(cache, "cache");
		this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
	}

	public CachedCall fetch(CacheKey key, HttpRequestSpec request, CachePolicy policy) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(request, "request");
		CacheLookup lookup = cache.lookup(key, policy, clockMillis.getAsLong());
		if (lookup.state() == CacheState.FRESH) {
			return completed(new CachedResponse(
					lookup.entry().payload(), CachedResponse.Origin.FRESH_CACHE));
		}
		if (lookup.state() == CacheState.STALE) {
			// Detached refreshes inherit the request validity lease and cannot commit after revoke.
			requests.execute(key, () -> network(request, key, lookup.entry())).response()
					.exceptionally(error -> null);
			return completed(new CachedResponse(
					lookup.entry().payload(), CachedResponse.Origin.STALE_CACHE));
		}

		SingleFlight.Call<CachedResponse> call = requests.execute(
				key, () -> network(request, key, lookup.entry()));
		return new CachedCall() {
			@Override
			public CompletableFuture<CachedResponse> response() {
				return call.response();
			}

			@Override
			public void cancel() {
				call.cancel();
			}
		};
	}

	public int activeRequestCount() {
		return requests.activeCount();
	}

	private SingleFlight.Operation<CachedResponse> network(
			HttpRequestSpec request, CacheKey key, CacheEntry prior) {
		var headers = new LinkedHashMap<>(request.headers());
		if (prior != null) {
			if ((prior.etag() != null) && !headers.containsKey("if-none-match")) {
				headers.put("if-none-match", prior.etag());
			}
			if ((prior.lastModified() != null) && !headers.containsKey("if-modified-since")) {
				headers.put("if-modified-since", prior.lastModified());
			}
		}
		HttpCall httpCall = client.execute(request.withHeaders(headers));
		var result = new CompletableFuture<CachedResponse>();
		httpCall.response().whenComplete((response, error) -> {
			if (error != null) {
				result.completeExceptionally(error);
				return;
			}
			long now = clockMillis.getAsLong();
			if (!request.isCurrent()) {
				result.completeExceptionally(new HttpFailure(HttpFailure.Code.CANCELLED,
						"Stale request cannot update cache"));
				return;
			}
			if (response.status() == 304) {
				if (prior == null) {
					result.completeExceptionally(new HttpFailure(HttpFailure.Code.TRANSPORT,
							"Received 304 without a cached representation"));
					return;
				}
				String etag = (response.header("etag") != null) ? response.header("etag") : prior.etag();
				String modified = (response.header("last-modified") != null) ?
						response.header("last-modified") : prior.lastModified();
				CacheEntry refreshed = new CacheEntry(prior.payload(), etag, modified, now);
				cache.put(key, refreshed);
				result.complete(new CachedResponse(refreshed.payload(),
						CachedResponse.Origin.REVALIDATED_CACHE));
				return;
			}

			CacheEntry replacement = new CacheEntry(response.payload(), response.header("etag"),
					response.header("last-modified"), now);
			cache.put(key, replacement);
			result.complete(new CachedResponse(
					replacement.payload(), CachedResponse.Origin.NETWORK));
		});
		return new SingleFlight.Operation<>() {
			@Override
			public CompletableFuture<CachedResponse> response() {
				return result;
			}

			@Override
			public void cancel() {
				httpCall.cancel();
			}
		};
	}

	private static CachedCall completed(CachedResponse response) {
		var future = CompletableFuture.completedFuture(response);
		return new CachedCall() {
			@Override
			public CompletableFuture<CachedResponse> response() {
				return future;
			}

			@Override
			public void cancel() {
			}
		};
	}
}
