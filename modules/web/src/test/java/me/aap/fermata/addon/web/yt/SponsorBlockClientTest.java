package me.aap.fermata.addon.web.yt;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class SponsorBlockClientTest {
	private static final String VIDEO = "dQw4w9WgXcQ";

	@Test
	public void hashPrefixEndpointAndCategoryOrderAreStable() {
		SponsorBlockClient.Request first = request(SponsorBlockClient.Category.INTRO,
				SponsorBlockClient.Category.SPONSOR);
		SponsorBlockClient.Request second = request(SponsorBlockClient.Category.SPONSOR,
				SponsorBlockClient.Category.INTRO);

		String url = SponsorBlockClient.buildUrl(first);
		assertEquals("5f6b", SponsorBlockClient.hashPrefix(VIDEO));
		assertTrue(url.startsWith("https://sponsor.ajay.app/api/skipSegments/5f6b?"));
		assertTrue(url.contains("categories=%5B%22sponsor%22%2C%22intro%22%5D"));
		assertTrue(url.contains("actionTypes=%5B%22skip%22%5D"));
		assertEquals(first.cacheKey(), second.cacheKey());
	}

	@Test
	public void parserFiltersHashCollisionsAndNormalizesSkipSegments() throws Exception {
		String json = """
				[
				  {"videoID":"collision","segments":[
				    {"segment":[1,2],"UUID":"wrong-video","category":"sponsor","actionType":"skip"}
				  ]},
				  {"videoID":"dQw4w9WgXcQ","segments":[
				    {"segment":[20,25],"UUID":"later","category":"intro","actionType":"skip"},
				    {"segment":[-2,4.5],"UUID":"first","category":"sponsor","actionType":"skip"},
				    {"segment":[5,8],"UUID":"mute","category":"sponsor","actionType":"mute"},
				    {"segment":[9,7],"UUID":"backwards","category":"sponsor","actionType":"skip"},
				    {"segment":[10,12],"UUID":"not-requested","category":"outro","actionType":"skip"},
				    {"segment":[20,30],"UUID":"later","category":"intro","actionType":"skip"},
				    {"segment":[6,7],"category":"sponsor"}
				  ]}
				]
				""";

		List<SponsorBlockClient.Segment> segments = SponsorBlockClient.parseResponse(json,
				request(SponsorBlockClient.Category.SPONSOR, SponsorBlockClient.Category.INTRO));

		assertEquals(3, segments.size());
		assertEquals(0.0, segments.get(0).startSeconds(), 0.0);
		assertEquals(4.5, segments.get(0).endSeconds(), 0.0);
		assertEquals(SponsorBlockClient.Category.SPONSOR, segments.get(0).category());
		assertEquals(6.0, segments.get(1).startSeconds(), 0.0);
		assertEquals(20.0, segments.get(2).startSeconds(), 0.0);
		assertEquals(25.0, segments.get(2).endSeconds(), 0.0);
	}

	@Test
	public void parserRejectsMalformedEnvelope() {
		assertThrows(IOException.class,
				() -> SponsorBlockClient.parseResponse("{\"videoID\":\"x\"}", request(
						SponsorBlockClient.Category.SPONSOR)));
		assertThrows(IOException.class,
				() -> SponsorBlockClient.parseResponse("[{]", request(
						SponsorBlockClient.Category.SPONSOR)));
	}

	@Test
	public void parserMergesOverlappingAndAdjacentSkipRanges() throws Exception {
		String json = """
				[{"videoID":"dQw4w9WgXcQ","segments":[
				  {"segment":[10,15],"UUID":"a","category":"sponsor","actionType":"skip"},
				  {"segment":[14.8,20],"UUID":"b","category":"intro","actionType":"skip"},
				  {"segment":[20.1,21],"UUID":"c","category":"sponsor","actionType":"skip"}
				]}]
				""";

		List<SponsorBlockClient.Segment> segments = SponsorBlockClient.parseResponse(json,
				request(SponsorBlockClient.Category.SPONSOR, SponsorBlockClient.Category.INTRO));

		assertEquals(1, segments.size());
		assertEquals(10.0, segments.get(0).startSeconds(), 0.0);
		assertEquals(21.0, segments.get(0).endSeconds(), 0.0);
	}

	@Test
	public void emptyCategoriesAndNotFoundReturnEmptyWithoutRetry() throws Exception {
		FakeTransport transport = new FakeTransport();
		SponsorBlockClient client = new SponsorBlockClient(transport);

		assertTrue(client.getSegments(new SponsorBlockClient.Request(VIDEO, SetFactory.none())).get()
				.isEmpty());
		assertEquals(0, transport.calls);

		transport.enqueue(completed(new SponsorBlockClient.Response(404, "")));
		assertTrue(client.getSegments(request(SponsorBlockClient.Category.SPONSOR)).get().isEmpty());
		assertEquals(1, transport.calls);
	}

	@Test
	public void timeoutAndServerFailuresUseControllerBackoff() throws Exception {
		FakeTransport timeout = new FakeTransport();
		timeout.enqueue(failed(new SocketTimeoutException("slow")));
		SponsorBlockClient timeoutClient = new SponsorBlockClient(timeout);

		assertTrue(timeoutClient.getSegments(request(SponsorBlockClient.Category.SPONSOR)).isFailed());
		assertEquals(1, timeout.calls);
		assertEquals(List.of(SponsorBlockClient.REQUEST_TIMEOUT_MS), timeout.timeouts);

		FakeTransport server = new FakeTransport();
		server.enqueue(completed(new SponsorBlockClient.Response(503, "")));
		FutureSupplier<List<SponsorBlockClient.Segment>> failedRequest =
				new SponsorBlockClient(server).getSegments(request(SponsorBlockClient.Category.SPONSOR));
		assertTrue(failedRequest.isFailed());
		assertEquals(1, server.calls);
		assertEquals(503, ((SponsorBlockClient.HttpException) failedRequest.getFailure()).status());

		FakeTransport protocol = new FakeTransport();
		protocol.enqueue(failed(new SponsorBlockClient.ProtocolException("too large")));
		FutureSupplier<List<SponsorBlockClient.Segment>> invalidResponse =
				new SponsorBlockClient(protocol).getSegments(request(SponsorBlockClient.Category.SPONSOR));
		assertTrue(invalidResponse.isFailed());
		assertEquals(1, protocol.calls);
	}

	@Test
	public void cancellationPropagatesToActiveAttemptAndStopsRetry() {
		FakeTransport transport = new FakeTransport();
		Promise<SponsorBlockClient.Response> first = new Promise<>();
		transport.enqueue(first);
		FutureSupplier<List<SponsorBlockClient.Segment>> result = new SponsorBlockClient(transport)
				.getSegments(request(SponsorBlockClient.Category.SPONSOR));

		assertTrue(result.cancel(true));
		assertTrue(first.isCancelled());
		assertEquals(1, transport.calls);
	}

	@Test
	public void urlConnectionTransportAppliesTimeoutHeadersAndDisconnects() throws Exception {
		FakeConnection connection = new FakeConnection(200, "[]");
		SponsorBlockClient.UrlConnectionTransport transport =
				new SponsorBlockClient.UrlConnectionTransport(Runnable::run) {
					@Override
					HttpURLConnection open(String url) {
						return connection;
					}
				};

		assertEquals(200, transport.get("https://example.test", 3210).get().status());
		assertEquals(3210, connection.connectTimeout);
		assertEquals(3210, connection.readTimeout);
		assertEquals("application/json", connection.headers.get("Accept"));
		assertTrue(connection.disconnected);
	}

	@Test
	public void urlConnectionCancellationDisconnectsBlockingRequest() throws Exception {
		BlockingConnection connection = new BlockingConnection();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			SponsorBlockClient.UrlConnectionTransport transport =
					new SponsorBlockClient.UrlConnectionTransport(executor) {
						@Override
						HttpURLConnection open(String url) {
							return connection;
						}
					};
			FutureSupplier<SponsorBlockClient.Response> result =
					transport.get("https://example.test", 1000);
			assertTrue(connection.entered.await(2, TimeUnit.SECONDS));

			assertTrue(result.cancel(true));
			assertTrue(connection.released.await(2, TimeUnit.SECONDS));
			assertTrue(connection.disconnected);
		} finally {
			executor.shutdownNow();
		}
	}

	private static SponsorBlockClient.Request request(SponsorBlockClient.Category... categories) {
		return new SponsorBlockClient.Request(VIDEO,
				(categories.length == 0) ? SetFactory.none() : EnumSet.copyOf(List.of(categories)));
	}

	private static final class SetFactory {
		private static <T> java.util.Set<T> none() {
			return java.util.Set.of();
		}
	}

	private static final class FakeTransport implements SponsorBlockClient.Transport {
		private final Queue<FutureSupplier<SponsorBlockClient.Response>> responses = new ArrayDeque<>();
		private final List<Integer> timeouts = new java.util.ArrayList<>();
		private int calls;

		void enqueue(FutureSupplier<SponsorBlockClient.Response> response) {
			responses.add(response);
		}

		@Override
		public FutureSupplier<SponsorBlockClient.Response> get(String url, int timeoutMs) {
			calls++;
			timeouts.add(timeoutMs);
			FutureSupplier<SponsorBlockClient.Response> response = responses.poll();
			if (response == null) throw new AssertionError("Unexpected request: " + url);
			return response;
		}
	}

	private static class FakeConnection extends HttpURLConnection {
		private final int status;
		private final byte[] body;
		private final Map<String, String> headers = new HashMap<>();
		private int connectTimeout;
		private int readTimeout;
		boolean disconnected;

		FakeConnection(int status, String body) throws Exception {
			super(new URL("https://example.test"));
			this.status = status;
			this.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}

		@Override
		public int getResponseCode() throws IOException {
			return status;
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(body);
		}

		@Override
		public void setConnectTimeout(int timeout) {
			connectTimeout = timeout;
		}

		@Override
		public void setReadTimeout(int timeout) {
			readTimeout = timeout;
		}

		@Override
		public void setRequestProperty(String key, String value) {
			headers.put(key, value);
		}

		@Override
		public void disconnect() {
			disconnected = true;
		}

		@Override
		public boolean usingProxy() {
			return false;
		}

		@Override
		public void connect() {
		}
	}

	private static final class BlockingConnection extends FakeConnection {
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch released = new CountDownLatch(1);

		BlockingConnection() throws Exception {
			super(200, "[]");
		}

		@Override
		public int getResponseCode() throws IOException {
			entered.countDown();
			try {
				released.await();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new SocketTimeoutException("cancelled");
			}
			return 200;
		}

		@Override
		public void disconnect() {
			super.disconnect();
			released.countDown();
		}
	}
}
