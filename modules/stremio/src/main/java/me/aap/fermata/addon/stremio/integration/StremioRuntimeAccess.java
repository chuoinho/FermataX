package me.aap.fermata.addon.stremio.integration;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.cache.CacheKey;
import me.aap.fermata.addon.stremio.net.cache.CachePolicy;
import me.aap.fermata.addon.stremio.net.cache.CachedCall;
import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.runtime.StremioRuntime;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;

public interface StremioRuntimeAccess {
	CompletionStage<StremioSourceSnapshot> sources();

	default AutoCloseable observeSources(Consumer<StremioSourceSnapshot> observer) {
		return () -> {
		};
	}

	CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy, NetworkConsent consent);

	default CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy, NetworkConsent consent,
			BooleanSupplier validity) {
		return fetch(key, uri, headers, maxBodyBytes, policy, consent);
	}

	default CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
			long maxBodyBytes, CachePolicy policy, NetworkConsent consent,
			HttpDeadlines deadlines, BooleanSupplier validity) {
		return fetch(key, uri, headers, maxBodyBytes, policy, consent, validity);
	}

	static StremioRuntimeAccess from(StremioRuntime runtime) {
		Objects.requireNonNull(runtime, "runtime");
		return new StremioRuntimeAccess() {
			@Override
			public CompletionStage<StremioSourceSnapshot> sources() {
				return runtime.sources().sources();
			}

			@Override
			public AutoCloseable observeSources(Consumer<StremioSourceSnapshot> observer) {
				return runtime.sources().observe(observer);
			}

			@Override
			public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
					long maxBodyBytes, CachePolicy policy, NetworkConsent consent) {
				return runtime.httpClient().fetch(key, uri, headers, maxBodyBytes, policy, consent);
			}

			@Override
			public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
					long maxBodyBytes, CachePolicy policy, NetworkConsent consent,
					BooleanSupplier validity) {
				return runtime.httpClient().fetch(key, uri, headers, maxBodyBytes, policy, consent,
						validity);
			}

			@Override
			public CachedCall fetch(CacheKey key, URI uri, Map<String, String> headers,
					long maxBodyBytes, CachePolicy policy, NetworkConsent consent,
					HttpDeadlines deadlines, BooleanSupplier validity) {
				return runtime.httpClient().fetch(key, uri, headers, maxBodyBytes, policy, consent,
						deadlines, validity);
			}
		};
	}
}
