package me.aap.fermata.addon.stremio.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.NetworkLimits;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.net.http.HttpTransport;
import me.aap.fermata.addon.stremio.net.http.StremioHttpClient;
import me.aap.fermata.addon.stremio.net.http.TransportCall;
import me.aap.fermata.addon.stremio.net.http.TransportRequest;
import me.aap.fermata.addon.stremio.net.http.TransportResponse;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;
import me.aap.fermata.addon.stremio.source.StremioManifestClient.Request;
import me.aap.fermata.addon.stremio.source.StremioSourceException;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;

public class ProductionStremioManifestClientTest {
	private java.util.concurrent.ScheduledExecutorService scheduler;
	private java.util.concurrent.ExecutorService io;
	private RequestGeneration lifecycle;
	private FakeTransport transport;
	private ProductionStremioManifestClient client;

	@Before
	public void setUp() throws Exception {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		io = Executors.newSingleThreadExecutor();
		lifecycle = new RequestGeneration();
		transport = new FakeTransport();
		var http = new StremioHttpClient(host -> List.of(
				InetAddress.getByAddress(new byte[]{8, 8, 8, 8})),
				transport, scheduler, io);
		client = new ProductionStremioManifestClient(http, NetworkConsent.STRICT,
				scheduler, lifecycle.begin());
	}

	@After
	public void tearDown() {
		client.close();
		lifecycle.close();
		scheduler.shutdownNow();
		io.shutdownNow();
	}

	@Test
	public void stremioSchemeAndConditionalHeadersReachBoundedHttpsRequest() throws Exception {
		transport.complete(200, Map.of("ETag", "next", "Last-Modified", "today"),
				"{\"id\":\"fixture\"}".getBytes(StandardCharsets.UTF_8));
		Request request = new Request(new StremioSourceSecret(
				"stremio://provider.invalid/config/manifest.json", "secret-token"),
				"previous", "yesterday", () -> true);

		var response = client.fetch(request).get(2, TimeUnit.SECONDS);

		assertEquals(URI.create("https://provider.invalid/config/manifest.json"),
				transport.request.endpoint().endpoint().uri());
		assertEquals("previous", transport.request.headers().get("if-none-match"));
		assertEquals("yesterday", transport.request.headers().get("if-modified-since"));
		assertEquals(NetworkLimits.MAX_MANIFEST_BODY_BYTES, transport.request.maxBodyBytes());
		assertEquals("next", response.etag());
		assertEquals("today", response.lastModified());
		assertFalse(response.notModified());
		assertFalse(response.toString().contains("secret-token"));
	}

	@Test
	public void notModifiedIsMappedWithoutBody() throws Exception {
		transport.complete(304, Map.of("etag", "same"), new byte[0]);

		var response = client.fetch(request("https://provider.invalid/manifest.json", () -> true))
				.get(2, TimeUnit.SECONDS);

		assertTrue(response.notModified());
		assertEquals("same", response.etag());
	}

	@Test
	public void malformedUrlAndNonManifestRouteFailSafelyBeforeTransport() {
		ExecutionException wrongPath = assertThrows(ExecutionException.class, () ->
				client.fetch(request("https://provider.invalid/catalog.json", () -> true))
						.get(2, TimeUnit.SECONDS));
		ExecutionException wrongScheme = assertThrows(ExecutionException.class, () ->
				client.fetch(request("file:///token/manifest.json", () -> true))
						.get(2, TimeUnit.SECONDS));

		assertEquals(Code.INVALID_TRANSPORT, code(wrongPath));
		assertEquals(Code.INVALID_TRANSPORT, code(wrongScheme));
		assertEquals(0, transport.executions);
		assertFalse(wrongPath.toString().contains("provider.invalid"));
	}

	@Test
	public void inactiveSourceCancelsPendingTransport() throws Exception {
		AtomicBoolean active = new AtomicBoolean(true);
		CompletableFuture<TransportResponse> pending = transport.pending();
		var response = client.fetch(request(
				"https://provider.invalid/manifest.json", active::get));
		awaitExecutions(1);

		active.set(false);
		ExecutionException failure = assertThrows(ExecutionException.class,
				() -> response.get(2, TimeUnit.SECONDS));

		assertEquals(Code.CANCELLED, code(failure));
		assertTrue(transport.cancelled.get());
		assertFalse(pending.isDone());
		assertEquals(0, client.activeCallCount());
	}

	@Test
	public void malformedUtf8MapsToInvalidManifest() {
		transport.complete(200, Map.of(), new byte[]{(byte) 0xc3, 0x28});

		ExecutionException failure = assertThrows(ExecutionException.class, () ->
				client.fetch(request("https://provider.invalid/manifest.json", () -> true))
						.get(2, TimeUnit.SECONDS));

		assertEquals(Code.INVALID_MANIFEST, code(failure));
	}

	@Test
	public void manifestUsesRequestSpecificCleartextConsent() throws Exception {
		transport.complete(200, Map.of(), "{\"id\":\"fixture\"}"
				.getBytes(StandardCharsets.UTF_8));
		Request allowed = new Request(new StremioSourceSecret(
				"http://provider.invalid/manifest.json", null), null, null, () -> true,
				new NetworkConsent(true, false));

		client.fetch(allowed).get(2, TimeUnit.SECONDS);
		assertEquals("http", transport.request.endpoint().endpoint().scheme());
	}

	private static Request request(String url, java.util.function.BooleanSupplier active) {
		return new Request(new StremioSourceSecret(url, null), null, null, active);
	}

	private static Code code(ExecutionException failure) {
		return ((StremioSourceException) failure.getCause()).code();
	}

	private void awaitExecutions(int count) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while ((transport.executions < count) && (System.nanoTime() < deadline)) {
			Thread.sleep(5);
		}
		assertEquals(count, transport.executions);
	}

	private static final class FakeTransport implements HttpTransport {
		private volatile TransportRequest request;
		private volatile CompletableFuture<TransportResponse> next = new CompletableFuture<>();
		private final AtomicBoolean cancelled = new AtomicBoolean();
		private volatile int executions;

		@Override
		public TransportCall execute(TransportRequest request) {
			this.request = request;
			executions++;
			CompletableFuture<TransportResponse> response = next;
			return new TransportCall() {
				@Override
				public CompletableFuture<TransportResponse> response() {
					return response;
				}

				@Override
				public void cancel() {
					cancelled.set(true);
				}
			};
		}

		private CompletableFuture<TransportResponse> pending() {
			next = new CompletableFuture<>();
			return next;
		}

		private void complete(int status, Map<String, String> headers, byte[] body) {
			CompletableFuture<TransportResponse> response = new CompletableFuture<>();
			response.complete(new Response(status, headers, body));
			next = response;
		}
	}

	private record Response(int status, Map<String, String> headers, byte[] bytes)
			implements TransportResponse {
		@Override
		public InputStream body() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public void close() {
		}
	}
}
