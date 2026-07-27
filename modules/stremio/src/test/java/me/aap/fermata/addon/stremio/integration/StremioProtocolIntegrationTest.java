package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.cache.CacheKey;
import me.aap.fermata.addon.stremio.net.cache.CachePolicy;
import me.aap.fermata.addon.stremio.net.cache.CachedCall;
import me.aap.fermata.addon.stremio.net.cache.CachedResponse;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.ManifestValidator;
import me.aap.fermata.addon.stremio.protocol.model.StremioRequest;
import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.source.StremioSourceSnapshot;
import me.aap.fermata.addon.stremio.subtitle.SubtitleRequestContext;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StremioProtocolIntegrationTest {
	private static final String SOURCE_ID = "11111111-1111-4111-8111-111111111111";
	private static final String DISABLED_ID = "22222222-2222-4222-8222-222222222222";
	private static final String ADDON_ID = "org.example.safe";
	private static final String SECRET = "private-provider-token";
	private static final String MANIFEST = """
			{
			  "id":"org.example.safe",
			  "name":"Safe provider",
			  "description":"Fixture",
			  "version":"1.0.0",
			  "types":["movie","series"],
			  "idPrefixes":["tt"],
			  "resources":["catalog","meta","stream","subtitles"],
			  "catalogs":[{
			    "type":"movie",
			    "id":"top",
			    "name":"Top",
			    "extra":[{"name":"search"},{"name":"skip"}]
			  }]
			}
			""";
	private static final String HASH_SUBTITLE_MANIFEST = MANIFEST.replace(
			"\"idPrefixes\":[\"tt\"],", "");

	private ExecutorService executor;
	private ScheduledExecutorService scheduler;
	private FakeRuntime runtime;
	private StremioProtocolClient client;

	@Before
	public void setUp() {
		executor = Executors.newFixedThreadPool(3, runnable ->
				new Thread(runnable, "stremio-integration-test"));
		scheduler = Executors.newSingleThreadScheduledExecutor();
		runtime = new FakeRuntime(snapshot(true));
		client = new StremioProtocolClient(runtime, source ->
				CompletableFuture.completedFuture(new StremioSourceSecret(
						"https://provider.example.invalid/" + SECRET + "/manifest.json", "config-secret")),
				executor, scheduler);
	}

	@After
	public void tearDown() throws Exception {
		client.close();
		executor.shutdownNow();
		scheduler.shutdownNow();
		executor.awaitTermination(2, TimeUnit.SECONDS);
		scheduler.awaitTermination(2, TimeUnit.SECONDS);
	}

	@Test
	public void browseUsesEnabledSnapshotSecureBaseEncodingAndCatalogCachePolicy() throws Exception {
		runtime.enqueue(CachedResponse.Origin.STALE_CACHE,
				"{\"metas\":[{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"One\"}]}");
		var manifest = ManifestValidator.parse(MANIFEST);
		var provider = new BrowseProvider(SOURCE_ID, "Safe provider", manifest, true, 0);
		var request = new StremioRequest("catalog", "movie", "top",
				Map.of("search", "Xin chao", "skip", "10"));
		var generation = new RequestGeneration();

		var payload = new StremioBrowseGatewayAdapter(client)
				.get(provider, request, generation.begin()).toCompletableFuture()
				.get(2, TimeUnit.SECONDS);

		assertTrue(payload.stale());
		assertTrue(new String(payload.body(), java.nio.charset.StandardCharsets.UTF_8)
				.contains("tt1"));
		assertEquals("/" + SECRET +
				"/catalog/movie/top/search=Xin%20chao&skip=10.json", runtime.lastUri.getRawPath());
		assertEquals(Map.of("accept", "application/json"), runtime.lastHeaders);
		assertEquals(5, runtime.lastPolicy.freshFor().toMinutes());
		assertEquals(30, runtime.lastPolicy.staleFor().toMinutes());
		assertEquals("catalog", runtime.lastKey.resource());
		assertEquals(new NetworkConsent(true, true), runtime.lastConsent);
		assertTrue(runtime.sourceThread.startsWith("stremio-integration-test"));
		assertFalse(runtime.lastKey.toString().contains(SECRET));
	}

	@Test
	public void disabledOrChangedProviderNeverReachesTransportAndErrorIsRedacted() {
		runtime.snapshot = snapshot(false);
		var request = new StremioRequest("meta", "movie", "tt1");
		var call = client.fetch(SOURCE_ID, ADDON_ID, request, null);

		CompletionException error = assertThrows(CompletionException.class,
				call.response()::join);
		assertTrue(error.getCause() instanceof StremioIntegrationException);
		assertEquals(StremioIntegrationException.Code.SOURCE_DISABLED,
				((StremioIntegrationException) error.getCause()).code());
		assertEquals(0, runtime.fetchCount);
		String rendered = error.getCause().toString() + error.getCause().getMessage();
		assertFalse(rendered.contains(SECRET));
		assertFalse(rendered.contains("provider.example"));
	}

	@Test
	public void streamAdapterParsesResponseAndExplicitCancelStopsTransport() throws Exception {
		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"{\"streams\":[{\"name\":\"HD\",\"url\":\"https://cdn.example.invalid/movie.m3u8\"}]}");
		var provider = new StreamProvider(SOURCE_ID, ADDON_ID, "Safe provider", 0, true);
		var request = new StreamAggregationRequest(
				StremioPlaybackIdentity.canonical("movie", "tt1", "tt1"),
				"movie", "tt1", "tt1",
				new StremioPlaybackMetadata("Movie", null, 60_000L));
		var call = new StremioStreamProviderClientAdapter(client).fetch(provider, request);

		var streams = call.response().get(2, TimeUnit.SECONDS);
		assertEquals(1, streams.size());
		assertTrue(streams.get(0).target() instanceof DirectStreamTarget);
		assertEquals("stream", runtime.lastKey.resource());
		assertEquals(30, runtime.lastPolicy.freshFor().toSeconds());

		FakeCachedCall pending = runtime.enqueuePending();
		var cancelled = new StremioStreamProviderClientAdapter(client).fetch(provider, request);
		await(() -> runtime.fetchCount == 2);
		cancelled.cancel();
		await(() -> pending.cancelled);
		assertTrue(pending.cancelled);
	}

	@Test
	public void subtitleAdapterParsesCandidatesAndStaleGenerationCancelsTransport() throws Exception {
		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"{\"subtitles\":[{\"id\":\"en\",\"url\":\"https://sub.example.invalid/a.vtt\",\"lang\":\"eng\"}]}");
		Clock clock = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);
		var provider = new StremioSubtitleProviderAdapter(client, SOURCE_ID, ADDON_ID,
				"Safe provider", "movie", "tt1", clock);
		var generation = new RequestGeneration();
		var candidates = provider.load(generation.begin()).response().get(2, TimeUnit.SECONDS);

		assertEquals(1, candidates.size());
		assertEquals("eng", candidates.get(0).languageLabel());
		assertEquals(Instant.parse("2026-07-21T00:15:00Z"), candidates.get(0).expiresAt());
		assertTrue(candidates.get(0).sourceLease().isBound());
		assertTrue(candidates.get(0).sourceLease().isCurrent());
		assertEquals("subtitles", runtime.lastKey.resource());
		assertNotNull(runtime.lastUri);

		FakeCachedCall pending = runtime.enqueuePending();
		RequestGeneration.Token old = generation.begin();
		var stale = provider.load(old);
		await(() -> runtime.fetchCount == 2);
		generation.begin();
		await(() -> pending.cancelled);
		assertThrows(CompletionException.class, stale.response()::join);
	}

	@Test
	public void subtitleAdapterUsesVideoHashAndContextExtrasWhenAvailable() throws Exception {
		runtime.publish(snapshotWithManifest(HASH_SUBTITLE_MANIFEST));
		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"{\"subtitles\":[{\"id\":\"en\",\"url\":\"https://sub.example.invalid/a.vtt\",\"lang\":\"eng\"}]}");
		var provider = new StremioSubtitleProviderAdapter(client, SOURCE_ID, ADDON_ID,
				"Safe provider", "movie", new SubtitleRequestContext(
					"tt1", "0123456789abcdef0123456789abcdef01234567", 123456L,
					"movie.mkv"), "0123456789abcdef0123456789abcdef01234567", Clock.systemUTC());

		provider.load(new RequestGeneration().begin()).response().get(2, TimeUnit.SECONDS);

		assertTrue("URI=" + runtime.lastUri, runtime.lastUri.getPath().contains(
				"/subtitles/movie/0123456789abcdef0123456789abcdef01234567.json"));
		assertEquals("filename=movie.mkv&videoId=tt1&videoSize=123456",
				runtime.lastUri.getRawQuery());
	}

	@Test
	public void subtitleAdapterKeepsVideoIdForLegacyProviderWhenHashIsUnsupported() throws Exception {
		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"{\"subtitles\":[]}");
		var provider = new StremioSubtitleProviderAdapter(client, SOURCE_ID, ADDON_ID,
				"Safe provider", "movie", new SubtitleRequestContext(
					"tt1", "0123456789abcdef0123456789abcdef01234567", 123456L,
					"movie.mkv"), "tt1", Clock.systemUTC());

		provider.load(new RequestGeneration().begin()).response().get(2, TimeUnit.SECONDS);

		assertTrue("URI=" + runtime.lastUri,
			runtime.lastUri.getPath().contains("/subtitles/movie/tt1.json"));
		assertEquals("filename=movie.mkv&videoId=tt1&videoSize=123456",
				runtime.lastUri.getRawQuery());
	}

	@Test
	public void providerCatalogExcludesDisabledSourcesAndPreservesOrder() throws Exception {
		runtime.snapshot = new StremioSourceSnapshot(1,
				List.of(source(SOURCE_ID, true, 0), source(DISABLED_ID, false, 1)), true);
		var catalog = new StremioProviderCatalog(runtime, executor);
		var request = streamRequest("movie", "tt1");

		var browse = catalog.browseProviders().toCompletableFuture().get(2, TimeUnit.SECONDS);
		var streams = catalog.streamProviders(request).toCompletableFuture()
				.get(2, TimeUnit.SECONDS);

		assertEquals(List.of(SOURCE_ID), browse.stream().map(BrowseProvider::sourceUuid).toList());
		assertEquals(List.of(SOURCE_ID), streams.stream().map(StreamProvider::sourceUuid).toList());
		assertTrue(streams.get(0).hasSourceBinding());
		assertTrue(catalog.isCurrent(streams.get(0)).toCompletableFuture()
				.get(2, TimeUnit.SECONDS));
		runtime.snapshot = new StremioSourceSnapshot(2,
				List.of(source(SOURCE_ID, false, 0)), true);
		assertFalse(catalog.isCurrent(streams.get(0)).toCompletableFuture()
				.get(2, TimeUnit.SECONDS));
		assertTrue(runtime.sourceThread.startsWith("stremio-integration-test"));
	}

	@Test
	public void providerCatalogOnlyAggregatesRequestCompatibleStreamSources() throws Exception {
		String metadataOnly = MANIFEST.replace(
				"\"resources\":[\"catalog\",\"meta\",\"stream\",\"subtitles\"]",
				"\"resources\":[\"catalog\",\"meta\"]")
				.replace(ADDON_ID, "org.example.metadata");
		String subtitlesOnly = MANIFEST.replace(
				"\"resources\":[\"catalog\",\"meta\",\"stream\",\"subtitles\"]",
				"\"resources\":[\"subtitles\"]")
				.replace(ADDON_ID, "org.example.subtitles");
		String seriesOnly = MANIFEST.replace(
				"\"resources\":[\"catalog\",\"meta\",\"stream\",\"subtitles\"]",
				"\"resources\":[{\"name\":\"stream\",\"types\":[\"series\"],"
						+ "\"idPrefixes\":[\"series:\"]}]")
				.replace(ADDON_ID, "org.example.series");
		runtime.snapshot = new StremioSourceSnapshot(1, List.of(
				source("metadata-source", "org.example.metadata", metadataOnly, true, 0),
				source("subtitle-source", "org.example.subtitles", subtitlesOnly, true, 1),
				source("series-source", "org.example.series", seriesOnly, true, 2),
				source(SOURCE_ID, true, 3)), true);
		var catalog = new StremioProviderCatalog(runtime, executor);

		var movieProviders = catalog.streamProviders(streamRequest("movie", "tt1"))
				.toCompletableFuture().get(2, TimeUnit.SECONDS);
		var seriesProviders = catalog.streamProviders(streamRequest("series", "series:42"))
				.toCompletableFuture().get(2, TimeUnit.SECONDS);

		assertEquals(List.of(SOURCE_ID), movieProviders.stream()
				.map(StreamProvider::sourceUuid).toList());
		assertEquals(List.of("series-source"), seriesProviders.stream()
				.map(StreamProvider::sourceUuid).toList());
	}

	@Test
	public void malformedProviderBodyMapsToRedactedInvalidResponse() {
		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"<html>" + SECRET + " https://provider.example.invalid/private</html>");
		var provider = new StreamProvider(SOURCE_ID, ADDON_ID, "Safe provider", 0, true);
		var request = new StreamAggregationRequest(
				StremioPlaybackIdentity.canonical("movie", "tt1", "tt1"),
				"movie", "tt1", "tt1",
				new StremioPlaybackMetadata("Movie", null, 60_000L));

		CompletionException error = assertThrows(CompletionException.class,
				() -> new StremioStreamProviderClientAdapter(client).fetch(provider, request)
						.response().join());
		assertTrue(error.getCause() instanceof StremioIntegrationException);
		assertEquals(StremioIntegrationException.Code.INVALID_RESPONSE,
				((StremioIntegrationException) error.getCause()).code());
		String rendered = error.getCause().toString() + error.getCause().getMessage();
		assertFalse(rendered.contains(SECRET));
		assertFalse(rendered.contains("provider.example"));
	}

	@Test
	public void sourceEditRejectsLateResponseAndChangesCacheIdentity() throws Exception {
		FakeCachedCall pending = runtime.enqueuePending();
		StremioRequest request = new StremioRequest("meta", "movie", "tt1");
		StremioProtocolClient.ProtocolCall call = client.fetch(SOURCE_ID, ADDON_ID, request, null);
		await(() -> runtime.fetchCount == 1);
		CacheKey firstKey = runtime.lastKey;

		runtime.snapshot = new StremioSourceSnapshot(2,
				List.of(source(SOURCE_ID, false, 0)), true);
		pending.response.complete(new CachedResponse(
				"{\"meta\":{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"Old\"}}"
						.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				CachedResponse.Origin.NETWORK));
		CompletionException changed = assertThrows(CompletionException.class,
				call.response()::join);
		assertEquals(StremioIntegrationException.Code.SOURCE_CHANGED,
				((StremioIntegrationException) changed.getCause()).code());

		runtime.snapshot = new StremioSourceSnapshot(3,
				List.of(source(SOURCE_ID, true, 0).withTransport(
						"fingerprint-edited", "https://provider.example.invalid/manifest.json",
						"secure:" + SOURCE_ID, 3L)), true);
		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"{\"meta\":{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"New\"}}");
		client.fetch(SOURCE_ID, ADDON_ID, request, null).response().get(2, TimeUnit.SECONDS);
		assertNotEquals(firstKey, runtime.lastKey);
	}

	@Test
	public void unrelatedSourceRevisionDoesNotCancelOrPartitionProviderRequest()
			throws Exception {
		FakeCachedCall pending = runtime.enqueuePending();
		StremioRequest request = new StremioRequest("meta", "movie", "tt1");
		StremioProtocolClient.ProtocolCall call = client.fetch(
				SOURCE_ID, ADDON_ID, request, null);
		await(() -> runtime.fetchCount == 1);
		CacheKey firstKey = runtime.lastKey;

		runtime.publish(new StremioSourceSnapshot(2, List.of(
				source(SOURCE_ID, true, 0), source(DISABLED_ID, true, 1)), true));
		pending.response.complete(new CachedResponse(
				"{\"meta\":{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"Current\"}}"
						.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				CachedResponse.Origin.NETWORK));
		call.response().get(2, TimeUnit.SECONDS);

		runtime.enqueue(CachedResponse.Origin.NETWORK,
				"{\"meta\":{\"id\":\"tt1\",\"type\":\"movie\",\"name\":\"Again\"}}");
		client.fetch(SOURCE_ID, ADDON_ID, request, null).response()
				.get(2, TimeUnit.SECONDS);
		assertEquals(firstKey, runtime.lastKey);
	}

	@Test
	public void sourceRevocationCancelsTransportBeforeLateResponse() throws Exception {
		FakeCachedCall pending = runtime.enqueuePending();
		StremioProtocolClient.ProtocolCall call = client.fetch(SOURCE_ID, ADDON_ID,
				new StremioRequest("meta", "movie", "tt1"), null);
		await(() -> runtime.fetchCount == 1);

		runtime.publish(new StremioSourceSnapshot(2,
				List.of(source(SOURCE_ID, false, 0)), true));

		await(() -> pending.cancelled);
		CompletionException changed = assertThrows(CompletionException.class,
				call.response()::join);
		assertEquals(StremioIntegrationException.Code.SOURCE_CHANGED,
				((StremioIntegrationException) changed.getCause()).code());
	}

	private static StremioSourceSnapshot snapshot(boolean enabled) {
		return snapshotWithManifest(MANIFEST, enabled);
	}

	private static StremioSourceSnapshot snapshotWithManifest(String manifest) {
		return snapshotWithManifest(manifest, true);
	}

	private static StremioSourceSnapshot snapshotWithManifest(String manifest, boolean enabled) {
		return new StremioSourceSnapshot(1,
				List.of(source(SOURCE_ID, ADDON_ID, manifest, enabled, 0)), true);
	}

	private static StremioSourceRecord source(String id, boolean enabled, int position) {
		return source(id, ADDON_ID, MANIFEST, enabled, position);
	}

	private static StremioSourceRecord source(String id, String addonId, String manifest,
			boolean enabled, int position) {
		return new StremioSourceRecord(id, "fingerprint-" + id, addonId, "Safe provider",
				"1.0.0", "https://provider.example.invalid/manifest.json", "secure:" + id,
				enabled, position, manifest, null, null, 0, 0, null, 0, 0, true, true);
	}

	private static StreamAggregationRequest streamRequest(String type, String videoId) {
		return new StreamAggregationRequest(
				StremioPlaybackIdentity.canonical(type, videoId, videoId),
				type, videoId, videoId,
				new StremioPlaybackMetadata("Title", null, 60_000L));
	}

	private static void await(Check condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.done()) {
			if (System.nanoTime() >= deadline) throw new AssertionError("condition timed out");
			Thread.sleep(5L);
		}
	}

	@FunctionalInterface
	private interface Check {
		boolean done();
	}

	private static final class FakeRuntime implements StremioRuntimeAccess {
		private final Queue<FakeCachedCall> calls = new ArrayDeque<>();
		private final CopyOnWriteArrayList<Consumer<StremioSourceSnapshot>> observers =
				new CopyOnWriteArrayList<>();
		private volatile StremioSourceSnapshot snapshot;
		private volatile String sourceThread;
		private volatile URI lastUri;
		private volatile Map<String, String> lastHeaders;
		private volatile CachePolicy lastPolicy;
		private volatile CacheKey lastKey;
		private volatile NetworkConsent lastConsent;
		private volatile int fetchCount;

		private FakeRuntime(StremioSourceSnapshot snapshot) {
			this.snapshot = snapshot;
		}

		private synchronized void enqueue(CachedResponse.Origin origin, String body) {
			FakeCachedCall call = new FakeCachedCall();
			call.response.complete(new CachedResponse(
					body.getBytes(java.nio.charset.StandardCharsets.UTF_8), origin));
			calls.add(call);
		}

		private synchronized FakeCachedCall enqueuePending() {
			FakeCachedCall call = new FakeCachedCall();
			calls.add(call);
			return call;
		}

		@Override
		public CompletableFuture<StremioSourceSnapshot> sources() {
			sourceThread = Thread.currentThread().getName();
			return CompletableFuture.completedFuture(snapshot);
		}

		@Override
		public AutoCloseable observeSources(Consumer<StremioSourceSnapshot> observer) {
			observers.add(observer);
			return () -> observers.remove(observer);
		}

		private void publish(StremioSourceSnapshot value) {
			snapshot = value;
			for (Consumer<StremioSourceSnapshot> observer : observers) observer.accept(value);
		}

		@Override
		public synchronized CachedCall fetch(CacheKey key, URI uri,
				Map<String, String> headers, long maxBodyBytes, CachePolicy policy,
				NetworkConsent consent) {
			if (calls.isEmpty()) throw new AssertionError("No fake response queued");
			fetchCount++;
			lastKey = key;
			lastUri = uri;
			lastHeaders = headers;
			lastPolicy = policy;
			lastConsent = consent;
			assertEquals(1024L * 1024L, maxBodyBytes);
			return calls.remove();
		}
	}

	private static final class FakeCachedCall implements CachedCall {
		private final CompletableFuture<CachedResponse> response = new CompletableFuture<>();
		private volatile boolean cancelled;

		@Override
		public CompletableFuture<CachedResponse> response() {
			return response;
		}

		@Override
		public void cancel() {
			cancelled = true;
			response.completeExceptionally(new java.util.concurrent.CancellationException());
		}
	}
}
