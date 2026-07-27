package me.aap.fermata.addon.stremio.integration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.protocol.CapabilityMatcher;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.StremioAddon;
import me.aap.fermata.addon.stremio.net.cache.CacheKey;
import me.aap.fermata.addon.stremio.net.cache.CachePolicy;
import me.aap.fermata.addon.stremio.net.cache.CachedCall;
import me.aap.fermata.addon.stremio.runtime.StremioRuntime;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregationResult;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregator;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.addon.stremio.subtitle.SubtitleFormat;
import me.aap.fermata.addon.stremio.subtitle.SubtitleLanguageFilter;
import me.aap.fermata.addon.stremio.subtitle.SubtitleProvider;
import me.aap.fermata.addon.stremio.subtitle.SubtitleCandidate;
import me.aap.fermata.addon.stremio.subtitle.SubtitleRequestContext;
import me.aap.fermata.addon.stremio.subtitle.SubtitlePayloadProcessor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.ContentIdentitySet;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

/** Product bridge from provider subtitle resources to bounded, engine-readable sidecars. */
public final class StremioSubtitlePlaybackBridge implements AutoCloseable {
	private static final CachePolicy FILE_CACHE = new CachePolicy(
			Duration.ofHours(1), Duration.ofHours(24));

	private final StremioRuntimeAccess runtime;
	private final StremioProviderCatalog providers;
	private final StremioProtocolClient protocolClient;
	private final SubtitleAggregator aggregator;
	private final Executor executor;
	private final RequestGeneration generation = new RequestGeneration();
	private final AtomicBoolean closed = new AtomicBoolean();

	StremioSubtitlePlaybackBridge(StremioRuntimeAccess runtime,
			StremioProviderCatalog providers, StremioProtocolClient protocolClient,
			SubtitleAggregator aggregator, Executor executor) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.providers = Objects.requireNonNull(providers, "providers");
		this.protocolClient = Objects.requireNonNull(protocolClient, "protocolClient");
		this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	public StremioSubtitlePlaybackBridge(StremioRuntime runtime,
			StremioProviderCatalog providers, StremioProtocolClient protocolClient,
			SubtitleAggregator aggregator, Executor executor) {
		this(StremioRuntimeAccess.from(runtime), providers, protocolClient, aggregator, executor);
	}

	public FutureSupplier<SubtitleAggregationResult> resolve(String type, String videoId) {
		return resolve(null, type, videoId);
	}

	public FutureSupplier<SubtitleAggregationResult> resolve(
			PlaybackDescriptor descriptor, String type, String videoId) {
		return resolve(descriptor, ContentIdentitySet.legacy(type, videoId), type, videoId);
	}

	public FutureSupplier<SubtitleAggregationResult> resolve(PlaybackDescriptor descriptor,
			ContentIdentitySet identities, String type, String videoId) {
		if (closed.get()) return StremioFutureBridge.from(closedFuture());
		RequestGeneration.Token token = generation.begin();
		List<SubtitleCandidate> embedded = embedded(descriptor);
		SubtitleRequestContext context = (descriptor == null) ?
				SubtitleRequestContext.forVideo(videoId) : new SubtitleRequestContext(
					videoId, descriptor.videoHash(), descriptor.videoSize(), descriptor.filename());
		CompletableFuture<SubtitleAggregationResult> result = providers.browseProviders()
				.thenApplyAsync(list -> list.stream()
						.filter(provider -> provider.manifest().resources().stream()
							.anyMatch(resource -> resource.name().equals("subtitles")))
						.map(provider -> subtitleProvider(provider, identities, type, context))
						.filter(Objects::nonNull)
						.toList(), executor)
				.thenCompose(subtitleProviders -> aggregator.aggregate(embedded,
						subtitleProviders, preferredLanguages(), token).result())
				.thenApply(aggregated -> SubtitleLanguageFilter.apply(aggregated,
						StremioAddon.configuredSubtitleLanguages()))
				.toCompletableFuture();
		return StremioFutureBridge.from(result);
	}

	private SubtitleProvider subtitleProvider(
			BrowseProvider provider, ContentIdentitySet identities, String type,
			SubtitleRequestContext context) {
		String hash = context.videoHash();
		if (hash != null && CapabilityMatcher.supports(provider.manifest(),
				new StremioRequest("subtitles", type, hash))) {
			return new StremioSubtitleProviderAdapter(protocolClient, provider.sourceUuid(),
					provider.manifest().id(), provider.displayName(), type, context, hash,
					java.time.Clock.systemUTC());
		}
		String requestId = identities.select(provider.manifest(), provider.sourceUuid(),
				"subtitles").orElse(null);
		if (requestId != null) {
			return new StremioSubtitleProviderAdapter(protocolClient, provider.sourceUuid(),
					provider.manifest().id(), provider.displayName(), type, context,
					requestId, java.time.Clock.systemUTC());
		}
		return null;
	}

	private static List<SubtitleCandidate> embedded(PlaybackDescriptor descriptor) {
		if (descriptor == null || descriptor.embeddedSubtitles().isEmpty()) return List.of();
		Instant expiresAt = Instant.ofEpochMilli(descriptor.expiresAtEpochMillis());
		return descriptor.embeddedSubtitles().stream().map(subtitle -> new SubtitleCandidate(
				subtitle.id(), subtitle.url(), subtitle.language(),
				descriptor.providerSourceUuid(), descriptor.providerName(),
				SubtitleCandidate.Source.STREAM_EMBEDDED, null, -1L, null,
				descriptor.providerSnapshot().sourceLease(), expiresAt)).toList();
	}

	static List<String> preferredLanguages() {
		return StremioAddon.preferredSubtitleLanguages();
	}

	public FutureSupplier<byte[]> load(SubtitleDescriptor descriptor) {
		if (closed.get()) return StremioFutureBridge.from(closedFuture());
		return load(runtime, descriptor);
	}

	static FutureSupplier<byte[]> load(
			StremioRuntimeAccess runtime, SubtitleDescriptor descriptor) {
		Objects.requireNonNull(runtime, "runtime");
		Objects.requireNonNull(descriptor, "descriptor");
		if (!descriptor.isPlayable(Instant.now())) {
			return StremioFutureBridge.from(CompletableFuture.failedFuture(
					new IllegalStateException("Subtitle descriptor is unavailable or expired")));
		}
		if (!descriptor.format().isSupported() &&
				!descriptor.format().isEngineReadable(descriptor.location())) {
			return StremioFutureBridge.from(CompletableFuture.failedFuture(
					new UnsupportedOperationException(
							"Subtitle format is not readable by the existing engines")));
		}
		if (descriptor.requestHeaders() != null) {
			return StremioFutureBridge.from(CompletableFuture.failedFuture(
					new UnsupportedOperationException(
							"Authenticated subtitle sidecar is unavailable")));
		}

		UUID sourceUuid;
		try {
			sourceUuid = UUID.fromString(descriptor.providerKey());
		} catch (IllegalArgumentException ex) {
			return StremioFutureBridge.from(CompletableFuture.failedFuture(
					new IllegalStateException("Subtitle provider identity is invalid", ex)));
		}
		StremioSourceLease lease = descriptor.sourceLease();
		CacheKey key = CacheKey.derive(sourceUuid, "subtitle-file",
				descriptor.identity() + '\u0000' + lease.cacheBinding());
		AtomicReference<CachedCall> active = new AtomicReference<>();
		AtomicReference<AutoCloseable> sourceObservation = new AtomicReference<>();
		Promise<byte[]> result = new Promise<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				boolean cancelled = super.cancel(mayInterruptIfRunning);
				CachedCall call = active.get();
				if (call != null) call.cancel();
				StremioSubtitlePlaybackBridge.close(sourceObservation.getAndSet(null));
				return cancelled;
			}
		};
		result.onCompletion((value, error) -> close(sourceObservation.getAndSet(null)));
		try {
			AutoCloseable observation = runtime.observeSources(snapshot -> {
				if (!lease.isBound() || lease.matches(snapshot)) return;
				CachedCall call = active.get();
				if (call != null) call.cancel();
				result.completeExceptionally(
						new IllegalStateException("Subtitle provider changed"));
			});
			sourceObservation.set(observation);
			if (result.isDone()) close(sourceObservation.getAndSet(null));
		} catch (Throwable error) {
			result.completeExceptionally(error);
			return result;
		}
		runtime.sources().whenComplete((snapshot, sourceError) -> {
			if (sourceError != null) {
				result.completeExceptionally(sourceError);
				return;
			}
			if (result.isCancelled()) return;
			var source = snapshot.source(descriptor.providerKey());
			if ((source == null) || !source.enabled() ||
					(lease.isBound() && !lease.matches(snapshot))) {
				result.completeExceptionally(
						new IllegalStateException("Subtitle provider is unavailable"));
				return;
			}
			var consent = lease.isBound() ? lease.consent() : source.networkConsent();
			CachedCall call = runtime.fetch(key, descriptor.location(),
					Map.of("accept", "text/vtt, application/x-subrip, text/plain"),
					SubtitleAggregator.MAX_SUBTITLE_FILE_BYTES, FILE_CACHE,
					consent, () -> !lease.isBound() || lease.isCurrent());
			active.set(call);
			if (result.isCancelled()) {
				call.cancel();
				return;
			}
			call.response().whenComplete((response, error) -> {
				if ((error == null) && lease.isBound() && !lease.isCurrent()) {
					result.completeExceptionally(
							new IllegalStateException("Subtitle provider changed"));
				} else if (error == null) {
					try {
						result.complete(processPayload(response.body(), descriptor.format()));
					} catch (IOException invalidPayload) {
						result.completeExceptionally(invalidPayload);
					}
				}
				else result.completeExceptionally(error);
			});
		});
		return result;
	}

	static byte[] normalizePayload(byte[] payload) throws IOException {
		Objects.requireNonNull(payload, "payload");
		byte[] normalized = payload;
		if (startsWith(normalized, 0x1f, 0x8b)) {
			normalized = readBounded(new GZIPInputStream(new ByteArrayInputStream(normalized)));
		} else if (startsWith(normalized, 'P', 'K')) {
			try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(normalized))) {
				while (true) {
					var entry = zip.getNextEntry();
					if (entry == null) throw new IOException("Subtitle archive is empty");
					if (!entry.isDirectory()) {
						normalized = readBounded(zip);
						break;
					}
				}
			}
		}

		if (startsWith(normalized, 0xff, 0xfe)) {
			return new String(normalized, 2, normalized.length - 2,
					StandardCharsets.UTF_16LE).getBytes(StandardCharsets.UTF_8);
		}
		if (startsWith(normalized, 0xfe, 0xff)) {
			return new String(normalized, 2, normalized.length - 2,
					StandardCharsets.UTF_16BE).getBytes(StandardCharsets.UTF_8);
		}
		if ((normalized.length >= 3) && ((normalized[0] & 0xff) == 0xef) &&
				((normalized[1] & 0xff) == 0xbb) && ((normalized[2] & 0xff) == 0xbf)) {
			return java.util.Arrays.copyOfRange(normalized, 3, normalized.length);
		}
		return normalized;
	}

	static byte[] processPayload(byte[] payload, SubtitleFormat declared) throws IOException {
		return SubtitlePayloadProcessor.process(normalizePayload(payload), declared);
	}

	private static boolean startsWith(byte[] value, int first, int second) {
		return (value.length >= 2) && ((value[0] & 0xff) == first) &&
				((value[1] & 0xff) == second);
	}

	private static byte[] readBounded(java.io.InputStream input) throws IOException {
		int limit = (int) SubtitleAggregator.MAX_SUBTITLE_FILE_BYTES;
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 32 * 1024));
		byte[] buffer = new byte[8192];
		for (int total = 0; ; ) {
			int read = input.read(buffer);
			if (read == -1) return output.toByteArray();
			total += read;
			if (total > limit) throw new IOException("Subtitle payload exceeds the size limit");
			output.write(buffer, 0, read);
		}
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		generation.close();
	}

	private static <T> CompletableFuture<T> closedFuture() {
		return CompletableFuture.failedFuture(
				new IllegalStateException("Stremio subtitle bridge is closed"));
	}

	private static void close(AutoCloseable closeable) {
		if (closeable == null) return;
		try {
			closeable.close();
		} catch (Exception ignored) {
		}
	}
}
