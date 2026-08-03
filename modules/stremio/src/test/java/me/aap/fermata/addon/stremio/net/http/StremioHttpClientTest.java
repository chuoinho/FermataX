package me.aap.fermata.addon.stremio.net.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.utils.net.TlsTrustPolicy;

public class StremioHttpClientTest {
	private java.util.concurrent.ScheduledExecutorService scheduler;
	private java.util.concurrent.ExecutorService bodyExecutor;
	private FakeTransport transport;
	private StremioHttpClient client;

	@Before
	public void setUp() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		bodyExecutor = Executors.newSingleThreadExecutor();
		transport = new FakeTransport();
		client = new StremioHttpClient(host -> List.of(address("8.8.8.8")),
				transport, scheduler, bodyExecutor);
	}

	@After
	public void tearDown() {
		scheduler.shutdownNow();
		bodyExecutor.shutdownNow();
	}

	@Test
	public void dnsValidationRunsOffTheCallingThread() throws Exception {
		Thread caller = Thread.currentThread();
		AtomicReference<Thread> resolverThread = new AtomicReference<>();
		transport.responses.add(completed(new FakeResponse(200, Map.of(), bytes("ok"))));
		client = new StremioHttpClient(host -> {
			resolverThread.set(Thread.currentThread());
			return List.of(address("8.8.8.8"));
		}, transport, scheduler, bodyExecutor);

		client.execute(request("https://one.example.invalid/a", 1024, Map.of()))
				.response().get(2, TimeUnit.SECONDS);

		assertNotSame(caller, resolverThread.get());
	}

	@Test
	public void followsValidatedRedirectAndStripsCrossOriginSecrets() throws Exception {
		transport.responses.add(completed(new FakeResponse(302,
				Map.of("Location", "https://two.example.invalid/catalog"), new byte[0])));
		var closeEntered = new java.util.concurrent.CountDownLatch(1);
		var allowClose = new java.util.concurrent.CountDownLatch(1);
		FakeResponse finalResponse = new FakeResponse(200, Map.of("ETag", "v1"), bytes("ok"),
				closeEntered, allowClose);
		transport.responses.add(completed(finalResponse));
		var request = request("https://one.example.invalid/start", 1024,
				Map.of("Authorization", "secret", "Cookie", "session", "Accept", "application/json"));

		CompletableFuture<HttpResponseData> result = client.execute(request).response();
		assertTrue(closeEntered.await(2, TimeUnit.SECONDS));
		try {
			assertFalse(result.isDone());
		} finally {
			allowClose.countDown();
		}
		HttpResponseData response = result.get(2, TimeUnit.SECONDS);

		assertArrayEquals(bytes("ok"), response.body());
		assertEquals("v1", response.header("etag"));
		assertEquals(2, transport.requests.size());
		assertEquals(TlsTrustPolicy.TRUST_ALL_USER_SOURCE,
				transport.requests.get(0).tlsTrustPolicy());
		assertEquals(TlsTrustPolicy.STRICT, transport.requests.get(1).tlsTrustPolicy());
		assertEquals(Map.of("accept", "application/json"), transport.requests.get(1).headers());
		assertTrue(finalResponse.closed.get());
	}

	@Test
	public void sameOriginRedirectRetainsUserSourceTrustPolicy() throws Exception {
		transport.responses.add(completed(new FakeResponse(302,
				Map.of("Location", "/catalog"), new byte[0])));
		transport.responses.add(completed(new FakeResponse(200, Map.of(), bytes("ok"))));

		client.execute(request("https://one.example.invalid/start", 1024, Map.of()))
				.response().get(2, TimeUnit.SECONDS);

		assertEquals(2, transport.requests.size());
		assertEquals(TlsTrustPolicy.TRUST_ALL_USER_SOURCE,
				transport.requests.get(0).tlsTrustPolicy());
		assertEquals(TlsTrustPolicy.TRUST_ALL_USER_SOURCE,
				transport.requests.get(1).tlsTrustPolicy());
	}

	@Test
	public void rejectsDeclaredAndStreamingBodiesOverLimitAndClosesResponse() {
		FakeResponse declared = new FakeResponse(200, Map.of("Content-Length", "5"), bytes("12345"));
		transport.responses.add(completed(declared));
		HttpFailure declaredError = failure(client.execute(request("https://one.example.invalid/a", 4, Map.of())));
		assertEquals(HttpFailure.Code.BODY_TOO_LARGE, declaredError.code());
		assertTrue(declared.closed.get());

		FakeResponse streamed = new FakeResponse(200, Map.of(), bytes("12345"));
		transport.responses.add(completed(streamed));
		HttpFailure streamedError = failure(client.execute(request("https://one.example.invalid/b", 4, Map.of())));
		assertEquals(HttpFailure.Code.BODY_TOO_LARGE, streamedError.code());
		assertTrue(streamed.closed.get());
	}

	@Test
	public void reportsHeaderDeadlineAndCancelsTransport() {
		FakeCall pending = new FakeCall(new CompletableFuture<>());
		transport.responses.add(pending);
		HttpRequestSpec request = new HttpRequestSpec(URI.create("https://one.example.invalid/a"), Map.of(), 10,
				NetworkConsent.STRICT, deadlines(40), null);

		HttpFailure error = failure(client.execute(request));

		assertEquals(HttpFailure.Code.HEADER_TIMEOUT, error.code());
		assertTrue(pending.cancelled.get());
	}

	@Test
	public void reportsBodyDeadlineAndClosesBlockingResponse() {
		var closed = new AtomicBoolean();
		TransportResponse blocking = new TransportResponse() {
			@Override
			public int status() {
				return 200;
			}

			@Override
			public Map<String, String> headers() {
				return Map.of();
			}

			@Override
			public InputStream body() {
				return new InputStream() {
					@Override
					public int read() throws java.io.IOException {
						while (!closed.get()) {
							try {
								Thread.sleep(5);
							} catch (InterruptedException ex) {
								Thread.currentThread().interrupt();
								throw new java.io.IOException(ex);
							}
						}
						throw new java.io.IOException("closed");
					}
				};
			}

			@Override
			public void close() {
				closed.set(true);
			}
		};
		transport.responses.add(new FakeCall(CompletableFuture.completedFuture(blocking)));
		HttpDeadlines deadlines = new HttpDeadlines(Duration.ofSeconds(1), Duration.ofSeconds(1),
				Duration.ofMillis(40), Duration.ofSeconds(2));
		HttpRequestSpec request = new HttpRequestSpec(URI.create("https://one.example.invalid/body"), Map.of(),
				10, NetworkConsent.STRICT, deadlines, null);

		HttpFailure error = failure(client.execute(request));

		assertEquals(HttpFailure.Code.BODY_TIMEOUT, error.code());
		assertTrue(closed.get());
	}

	@Test
	public void cancellationPropagatesAndStaleGenerationNeverStartsTransport() {
		FakeCall pending = new FakeCall(new CompletableFuture<>());
		transport.responses.add(pending);
		HttpCall call = client.execute(request("https://one.example.invalid/a", 10, Map.of()));
		await(() -> !transport.requests.isEmpty());
		call.cancel();
		assertEquals(HttpFailure.Code.CANCELLED, failure(call).code());
		assertTrue(pending.cancelled.get());

		var generation = new RequestGeneration();
		var stale = generation.begin();
		generation.begin();
		HttpRequestSpec request = new HttpRequestSpec(URI.create("https://one.example.invalid/stale"), Map.of(),
				10, NetworkConsent.STRICT, HttpDeadlines.DEFAULT, stale);
		assertEquals(HttpFailure.Code.CANCELLED, failure(client.execute(request)).code());
		assertEquals(1, transport.requests.size());
	}

	@Test
	public void exposesAllDeadlinesAndPinnedAddressToTransport() throws Exception {
		transport.responses.add(completed(new FakeResponse(200, Map.of(), bytes("ok"))));
		HttpDeadlines deadlines = new HttpDeadlines(Duration.ofSeconds(1), Duration.ofSeconds(2),
				Duration.ofSeconds(3), Duration.ofSeconds(4));
		HttpRequestSpec request = new HttpRequestSpec(URI.create("https://one.example.invalid/a"), Map.of(),
				99, NetworkConsent.STRICT, deadlines, null);

		client.execute(request).response().get(2, TimeUnit.SECONDS);

		TransportRequest sent = transport.requests.get(0);
		assertEquals(deadlines, sent.deadlines());
		assertEquals(99, sent.maxBodyBytes());
		assertEquals("8.8.8.8", sent.endpoint().pinnedAddress().getHostAddress());
	}

	private static HttpRequestSpec request(String uri, long limit, Map<String, String> headers) {
		return new HttpRequestSpec(URI.create(uri), headers, limit, NetworkConsent.STRICT,
				HttpDeadlines.DEFAULT, null);
	}

	private static HttpDeadlines deadlines(long millis) {
		Duration duration = Duration.ofMillis(millis);
		return new HttpDeadlines(duration, duration, duration.multipliedBy(5),
				duration.multipliedBy(10));
	}

	private static HttpFailure failure(HttpCall call) {
		ExecutionException error = assertThrows(ExecutionException.class,
				() -> call.response().get(2, TimeUnit.SECONDS));
		assertTrue(error.getCause() instanceof HttpFailure);
		return (HttpFailure) error.getCause();
	}

	private static void await(java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= deadline) throw new AssertionError("Condition timed out");
			try {
				Thread.sleep(5);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError(ex);
			}
		}
	}

	private static FakeCall completed(FakeResponse response) {
		return new FakeCall(CompletableFuture.completedFuture(response));
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
		private final ArrayDeque<FakeCall> responses = new ArrayDeque<>();
		private final java.util.ArrayList<TransportRequest> requests = new java.util.ArrayList<>();

		@Override
		public TransportCall execute(TransportRequest request) {
			requests.add(request);
			return responses.removeFirst();
		}
	}

	private static final class FakeCall implements TransportCall {
		private final CompletableFuture<TransportResponse> response;
		private final AtomicBoolean cancelled = new AtomicBoolean();

		private FakeCall(CompletableFuture<? extends TransportResponse> response) {
			this.response = new CompletableFuture<>();
			response.whenComplete((value, error) -> {
				if (error == null) this.response.complete(value);
				else this.response.completeExceptionally(error);
			});
		}

		@Override
		public CompletableFuture<TransportResponse> response() {
			return response;
		}

		@Override
		public void cancel() {
			cancelled.set(true);
		}
	}

	private static final class FakeResponse implements TransportResponse {
		private final int status;
		private final Map<String, String> headers;
		private final byte[] body;
		private final AtomicBoolean closed = new AtomicBoolean();
		private final java.util.concurrent.CountDownLatch closeEntered;
		private final java.util.concurrent.CountDownLatch allowClose;

		private FakeResponse(int status, Map<String, String> headers, byte[] body) {
			this(status, headers, body, null, null);
		}

		private FakeResponse(int status, Map<String, String> headers, byte[] body,
				java.util.concurrent.CountDownLatch closeEntered,
				java.util.concurrent.CountDownLatch allowClose) {
			this.status = status;
			this.headers = new LinkedHashMap<>(headers);
			this.body = body;
			this.closeEntered = closeEntered;
			this.allowClose = allowClose;
		}

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
			return new ByteArrayInputStream(body);
		}

		@Override
		public void close() {
			if (closeEntered != null) closeEntered.countDown();
			if (allowClose != null) {
				try {
					allowClose.await();
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			closed.set(true);
		}
	}
}
