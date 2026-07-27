package me.aap.fermata.addon.stremio.net.cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;
import me.aap.fermata.addon.stremio.net.http.HttpRequestSpec;
import me.aap.fermata.addon.stremio.net.http.HttpTransport;
import me.aap.fermata.addon.stremio.net.http.StremioHttpClient;
import me.aap.fermata.addon.stremio.net.http.TransportCall;
import me.aap.fermata.addon.stremio.net.http.TransportRequest;
import me.aap.fermata.addon.stremio.net.http.TransportResponse;

public class CachedHttpClientTest {
	private java.util.concurrent.ScheduledExecutorService scheduler;
	private FakeTransport transport;
	private AtomicLong clock;
	private BoundedLruCache cache;
	private CachedHttpClient client;
	private CacheKey key;
	private HttpRequestSpec request;
	private CachePolicy policy;

	@Before
	public void setUp() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		transport = new FakeTransport();
		clock = new AtomicLong(1_000);
		cache = new BoundedLruCache(8, 64 * 1024);
		var http = new StremioHttpClient(host -> List.of(address("8.8.8.8")), transport,
				scheduler, Runnable::run);
		client = new CachedHttpClient(http, cache, clock::get);
		key = CacheKey.derive(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
				"manifest", "manifest-v1");
		request = new HttpRequestSpec(URI.create("https://provider.example.invalid/manifest.json"),
				Map.of("accept", "application/json"), 1024, NetworkConsent.STRICT,
				HttpDeadlines.DEFAULT, null);
		policy = new CachePolicy(Duration.ofMillis(100), Duration.ofMillis(200));
	}

	@After
	public void tearDown() {
		scheduler.shutdownNow();
	}

	@Test
	public void storesNetworkResultAndReturnsFreshWithoutAnotherRequest() throws Exception {
		transport.enqueue(response(200, Map.of("ETag", "v1", "Last-Modified", "yesterday"), "body"));

		CachedResponse network = client.fetch(key, request, policy).response().get(1, TimeUnit.SECONDS);
		CachedResponse fresh = client.fetch(key, request, policy).response().get(1, TimeUnit.SECONDS);

		assertEquals(CachedResponse.Origin.NETWORK, network.origin());
		assertEquals(CachedResponse.Origin.FRESH_CACHE, fresh.origin());
		assertArrayEquals(bytes("body"), fresh.body());
		assertEquals(1, transport.requests.size());
	}

	@Test
	public void staleReturnsImmediatelyAndRevalidatesWithBothValidators() throws Exception {
		cache.put(key, new CacheEntry(bytes("old"), "v1", "yesterday", clock.get()));
		clock.addAndGet(101);
		transport.enqueue(response(304, Map.of(), ""));

		CachedResponse stale = client.fetch(key, request, policy).response().get(1, TimeUnit.SECONDS);
		CachedResponse refreshed = client.fetch(key, request, policy).response().get(1, TimeUnit.SECONDS);

		assertEquals(CachedResponse.Origin.STALE_CACHE, stale.origin());
		assertEquals(CachedResponse.Origin.FRESH_CACHE, refreshed.origin());
		assertArrayEquals(bytes("old"), refreshed.body());
		assertEquals("v1", transport.requests.get(0).headers().get("if-none-match"));
		assertEquals("yesterday", transport.requests.get(0).headers().get("if-modified-since"));
	}

	@Test
	public void expiredConcurrentRequestsShareOneTransportCall() throws Exception {
		cache.put(key, new CacheEntry(bytes("old"), "v1", null, clock.get()));
		clock.addAndGet(301);
		var pending = new CompletableFuture<TransportResponse>();
		transport.calls.add(new FakeCall(pending));

		CachedCall first = client.fetch(key, request, policy);
		CachedCall second = client.fetch(key, request, policy);
		assertEquals(1, transport.requests.size());
		assertEquals(1, client.activeRequestCount());

		pending.complete(response(200, Map.of("ETag", "v2"), "new"));
		assertArrayEquals(bytes("new"), first.response().get(1, TimeUnit.SECONDS).body());
		assertArrayEquals(bytes("new"), second.response().get(1, TimeUnit.SECONDS).body());
		assertEquals(0, client.activeRequestCount());
	}

	@Test
	public void taintedResponseValidatorsAreNeverReplayed() throws Exception {
		transport.enqueue(response(200, Map.of("ETag", "token-secret",
				"Last-Modified", "session-credential"), "body"));
		client.fetch(key, request, policy).response().get(1, TimeUnit.SECONDS);
		clock.addAndGet(101);
		transport.enqueue(response(200, Map.of(), "new"));

		client.fetch(key, request, policy).response().get(1, TimeUnit.SECONDS);
		assertFalse(transport.requests.get(1).headers().containsKey("if-none-match"));
		assertFalse(transport.requests.get(1).headers().containsKey("if-modified-since"));
	}

	private static TransportResponse response(int status, Map<String, String> headers, String body) {
		return new TransportResponse() {
			@Override
			public int status() {
				return status;
			}

			@Override
			public Map<String, String> headers() {
				return headers;
			}

			@Override
			public InputStream body() {
				return new ByteArrayInputStream(bytes(body));
			}

			@Override
			public void close() {
			}
		};
	}

	private static byte[] bytes(String value) {
		return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private static InetAddress address(String value) {
		try {
			return InetAddress.getByName(value);
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	private static final class FakeTransport implements HttpTransport {
		private final ArrayDeque<FakeCall> calls = new ArrayDeque<>();
		private final ArrayList<TransportRequest> requests = new ArrayList<>();

		private void enqueue(TransportResponse response) {
			calls.add(new FakeCall(CompletableFuture.completedFuture(response)));
		}

		@Override
		public TransportCall execute(TransportRequest request) {
			requests.add(request);
			return calls.removeFirst();
		}
	}

	private static final class FakeCall implements TransportCall {
		private final CompletableFuture<TransportResponse> response;

		private FakeCall(CompletableFuture<TransportResponse> response) {
			this.response = response;
		}

		@Override
		public CompletableFuture<TransportResponse> response() {
			return response;
		}

		@Override
		public void cancel() {
		}
	}
}
