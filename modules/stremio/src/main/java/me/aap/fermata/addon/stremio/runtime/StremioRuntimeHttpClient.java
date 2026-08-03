package me.aap.fermata.addon.stremio.runtime;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.net.cache.CacheKey;
import me.aap.fermata.addon.stremio.net.cache.CachePolicy;
import me.aap.fermata.addon.stremio.net.cache.CachedCall;
import me.aap.fermata.addon.stremio.net.cache.CachedHttpClient;
import me.aap.fermata.addon.stremio.net.cache.CachedResponse;
import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.net.http.HttpCall;
import me.aap.fermata.addon.stremio.net.http.HttpFailure;
import me.aap.fermata.addon.stremio.net.http.HttpRequestSpec;
import me.aap.fermata.addon.stremio.net.http.HttpResponseData;
import me.aap.fermata.addon.stremio.net.http.StremioHttpClient;

/** Lifecycle-safe cached request facade for catalog, meta, stream and subtitle phases. */
public final class StremioRuntimeHttpClient implements AutoCloseable {
	private final CachedHttpClient client;
	private final StremioHttpClient rawClient;
	private final NetworkConsent consent;
	private final RequestGeneration.Token lifecycle;
	private final Set<CachedCall> activeCalls = ConcurrentHashMap.newKeySet();
	private final Set<HttpCall> activeRawCalls = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean closed = new AtomicBoolean();

	StremioRuntimeHttpClient(CachedHttpClient client, StremioHttpClient rawClient,
			NetworkConsent consent,
			RequestGeneration.Token lifecycle) {
		this.client = Objects.requireNonNull(client, "client");
		this.rawClient = Objects.requireNonNull(rawClient, "rawClient");
		this.consent = Objects.requireNonNull(consent, "consent");
		this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
	}

	/** Uncached bounded request for provider configuration resources. */
	public HttpCall fetchRaw(URI uri, Map<String, String> headers, long maxBodyBytes,
			NetworkConsent requestConsent) {
		if (closed.get() || !lifecycle.isCurrent()) return cancelledRaw();
		HttpCall call = rawClient.execute(new HttpRequestSpec(uri, headers, maxBodyBytes,
				Objects.requireNonNull(requestConsent, "requestConsent"),
				HttpDeadlines.DEFAULT, lifecycle));
		activeRawCalls.add(call);
		call.response().whenComplete((response, error) -> activeRawCalls.remove(call));
		if (closed.get() || !lifecycle.isCurrent()) {
			call.cancel();
			activeRawCalls.remove(call);
		}
		return call;
	}

	public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy) {
		return fetch(key, uri, headers, maxBodyBytes, policy, consent);
	}

	public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy, NetworkConsent requestConsent) {
		return fetch(key, uri, headers, maxBodyBytes, policy, requestConsent, () -> true);
	}

	public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy, NetworkConsent requestConsent,
			BooleanSupplier validity) {
		return fetch(key, uri, headers, maxBodyBytes, policy, requestConsent,
				HttpDeadlines.DEFAULT, validity);
	}

	public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy, NetworkConsent requestConsent,
			HttpDeadlines deadlines, BooleanSupplier validity) {
		if (closed.get() || !lifecycle.isCurrent()) return cancelled();
		HttpRequestSpec request = new HttpRequestSpec(uri, headers, maxBodyBytes,
				Objects.requireNonNull(requestConsent, "requestConsent"),
				Objects.requireNonNull(deadlines, "deadlines"), lifecycle,
				Objects.requireNonNull(validity, "validity"));
		CachedCall delegate = client.fetch(key, request, policy);
		activeCalls.add(delegate);
		delegate.response().whenComplete((response, error) -> activeCalls.remove(delegate));
		if (closed.get() || !lifecycle.isCurrent()) {
			delegate.cancel();
			activeCalls.remove(delegate);
			return cancelled();
		}
		return new CachedCall() {
			@Override
			public CompletableFuture<CachedResponse> response() {
				return delegate.response();
			}

			@Override
			public void cancel() {
				activeCalls.remove(delegate);
				delegate.cancel();
			}
		};
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		for (CachedCall call : activeCalls) call.cancel();
		activeCalls.clear();
		for (HttpCall call : activeRawCalls) call.cancel();
		activeRawCalls.clear();
	}

	int activeCallCount() {
		return activeCalls.size();
	}

	private static CachedCall cancelled() {
		CompletableFuture<CachedResponse> result = StremioFutures.failedFuture(
				new HttpFailure(HttpFailure.Code.CANCELLED, "Stremio runtime is closed"));
		return new CachedCall() {
			@Override
			public CompletableFuture<CachedResponse> response() {
				return result;
			}

			@Override
			public void cancel() {
			}
		};
	}

	private static HttpCall cancelledRaw() {
		CompletableFuture<HttpResponseData> result = StremioFutures.failedFuture(
				new HttpFailure(HttpFailure.Code.CANCELLED, "Stremio runtime is closed"));
		return new HttpCall() {
			@Override
			public CompletableFuture<HttpResponseData> response() {
				return result;
			}

			@Override
			public void cancel() {
			}
		};
	}
}
