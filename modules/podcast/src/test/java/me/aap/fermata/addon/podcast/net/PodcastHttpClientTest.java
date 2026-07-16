package me.aap.fermata.addon.podcast.net;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import javax.net.ssl.SSLException;

import org.junit.Test;

public class PodcastHttpClientTest {
	@Test
	public void rejectsHtmlBeforeJsonParserRunsAndDisconnects() throws Exception {
		FakeConnection connection = new FakeConnection(200, " <html>login</html>");
		PodcastHttpClient client = new FakeClient(connection);

		PodcastException error = assertThrows(PodcastException.class,
				() -> client.requestJson("secret-url", input -> 1));

		assertEquals(PodcastErrorCode.INVALID_CONTENT, error.getCode());
		assertFalse(error.getMessage().contains("secret-url"));
		assertTrue(connection.disconnected);
	}

	@Test
	public void mapsRateLimitAndRetryWithoutReadingBody() throws Exception {
		FakeConnection connection = new FakeConnection(429, "{}");
		connection.retryAfter = "12";

		PodcastException error = assertThrows(PodcastException.class,
				() -> PodcastHttpClient.readJson(connection, input -> 1));

		assertEquals(PodcastErrorCode.RATE_LIMITED, error.getCode());
		assertEquals(429, error.getHttpStatus());
		assertEquals(12_000, error.getRetryAfterMs());
	}

	@Test
	public void mapsAuthenticationNotFoundAndServerStatuses() throws Exception {
		int[] statuses = {401, 403, 404, 500};
		PodcastErrorCode[] codes = {PodcastErrorCode.AUTH_REQUIRED,
				PodcastErrorCode.AUTH_REJECTED, PodcastErrorCode.NOT_FOUND,
				PodcastErrorCode.SERVER};
		for (int i = 0; i < statuses.length; i++) {
			int status = statuses[i];
			PodcastErrorCode code = codes[i];
			FakeConnection connection = new FakeConnection(status, "must not be parsed");
			PodcastException error = assertThrows(PodcastException.class,
					() -> PodcastHttpClient.readJson(connection, input -> 1));
			assertEquals(code, error.getCode());
			assertEquals(status, error.getHttpStatus());
		}
	}

	@Test
	public void boundedJsonAllowsValidPayloadAndRejectsDeclaredOversize() throws Exception {
		FakeConnection valid = new FakeConnection(200, "{\"ok\":true}");
		assertEquals((int) '{', (int) PodcastHttpClient.readJson(valid, InputStream::read));

		FakeConnection large = new FakeConnection(200, "{}");
		large.declaredLength = PodcastHttpClient.MAX_JSON_BYTES + 1L;
		PodcastException error = assertThrows(PodcastException.class,
				() -> PodcastHttpClient.readJson(large, input -> 1));
		assertEquals(PodcastErrorCode.TOO_LARGE, error.getCode());
	}

	@Test
	public void acceptsUtf8BomBeforeJson() throws Exception {
		byte[] json = "\uFEFF  {\"ok\":true}".getBytes(UTF_8);
		try (InputStream input = PodcastHttpClient.prepareJson(new ByteArrayInputStream(json))) {
			assertEquals((int) '{', input.read());
		}
	}

	@Test
	public void parsesRetryAfterSecondsAndHttpDate() {
		long now = 1_700_000_000_000L;
		String date = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now + 90_000),
				java.time.ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);
		assertEquals(12_000, PodcastHttpClient.parseRetryAfter("12", now));
		assertEquals(90_000, PodcastHttpClient.parseRetryAfter(date, now));
		assertEquals(0, PodcastHttpClient.parseRetryAfter("invalid", now));
	}

	@Test
	public void mapsCommonTransportFailuresToStableCodes() throws Exception {
		assertTransportCode(new UnknownHostException("host"), PodcastErrorCode.DNS);
		assertTransportCode(new SocketTimeoutException("slow"), PodcastErrorCode.TIMEOUT);
		assertTransportCode(new SSLException("tls"), PodcastErrorCode.TLS);
		assertTransportCode(new ConnectException("offline"), PodcastErrorCode.OFFLINE);
	}

	@Test
	public void documentRedirectKeepsAuthOnlyForSameOrigin() throws Exception {
		FakeConnection sameRedirect = new FakeConnection(302, "");
		sameRedirect.location = "/feed.xml";
		FakeConnection sameFeed = new FakeConnection(200, "<rss/>");
		QueueClient sameClient = new QueueClient(sameRedirect, sameFeed);
		PodcastHttpClient.DocumentRequest request = new PodcastHttpClient.DocumentRequest(
				"https://example.test/start", "application/xml", "Basic secret", null, null, false);

		PodcastHttpClient.DocumentResponse<Integer> response = sameClient.requestDocument(request,
				(input, type, finalUrl) -> input.read());

		assertEquals((int) '<', (int) response.getBody());
		assertEquals("Basic secret", sameRedirect.requestHeaders.get("Authorization"));
		assertEquals("Basic secret", sameFeed.requestHeaders.get("Authorization"));

		FakeConnection crossRedirect = new FakeConnection(302, "");
		crossRedirect.location = "https://cdn.test/feed.xml";
		FakeConnection crossFeed = new FakeConnection(200, "<rss/>");
		QueueClient crossClient = new QueueClient(crossRedirect, crossFeed);
		crossClient.requestDocument(request, (input, type, finalUrl) -> input.read());
		assertEquals("Basic secret", crossRedirect.requestHeaders.get("Authorization"));
		assertFalse(crossFeed.requestHeaders.containsKey("Authorization"));
	}

	@Test
	public void authenticatedRedirectCannotDowngradeAndConditional304HasNoBody() throws Exception {
		FakeConnection downgrade = new FakeConnection(302, "");
		downgrade.location = "http://example.test/feed.xml";
		QueueClient downgradeClient = new QueueClient(downgrade);
		PodcastHttpClient.DocumentRequest authenticated = new PodcastHttpClient.DocumentRequest(
				"https://example.test/start", "application/xml", "Basic secret", null, null, false);
		PodcastException rejected = assertThrows(PodcastException.class,
				() -> downgradeClient.requestDocument(authenticated,
						(input, type, finalUrl) -> input.read()));
		assertEquals(PodcastErrorCode.REDIRECT_REJECTED, rejected.getCode());

		FakeConnection unchanged = new FakeConnection(304, "must not be read");
		QueueClient conditionalClient = new QueueClient(unchanged);
		PodcastHttpClient.DocumentRequest conditional = new PodcastHttpClient.DocumentRequest(
				"https://example.test/feed", "application/xml", null, "etag-1", "date-1", false);
		PodcastHttpClient.DocumentResponse<Integer> response = conditionalClient.requestDocument(
				conditional, (input, type, finalUrl) -> input.read());
		assertTrue(response.isNotModified());
		assertEquals("etag-1", unchanged.requestHeaders.get("If-None-Match"));
		assertEquals("date-1", unchanged.requestHeaders.get("If-Modified-Since"));
	}

	private static final class FakeClient extends PodcastHttpClient {
		private final HttpURLConnection connection;

		FakeClient(HttpURLConnection connection) {
			this.connection = connection;
		}

		@Override
		HttpURLConnection open(String url) {
			return connection;
		}
	}

	private static final class QueueClient extends PodcastHttpClient {
		private final Queue<HttpURLConnection> connections = new ArrayDeque<>();

		QueueClient(HttpURLConnection... values) {
			for (HttpURLConnection value : values) connections.add(value);
		}

		@Override
		HttpURLConnection openDocument(String url) {
			HttpURLConnection connection = connections.poll();
			if (connection == null) throw new AssertionError("Unexpected connection: " + url);
			return connection;
		}
	}

	private static void assertTransportCode(IOException failure, PodcastErrorCode expected) {
		PodcastException error = assertThrows(PodcastException.class, () ->
				new ThrowingClient(failure).requestJson("https://example.test", input -> 1));
		assertEquals(expected, error.getCode());
	}

	private static final class ThrowingClient extends PodcastHttpClient {
		private final IOException failure;

		ThrowingClient(IOException failure) {
			this.failure = failure;
		}

		@Override
		HttpURLConnection open(String url) throws IOException {
			throw failure;
		}
	}

	private static final class FakeConnection extends HttpURLConnection {
		private final int status;
		private final byte[] body;
		long declaredLength = -1;
		String retryAfter;
		String location;
		boolean disconnected;
		final Map<String, String> requestHeaders = new HashMap<>();

		FakeConnection(int status, String body) throws Exception {
			super(new URL("https://example.test"));
			this.status = status;
			this.body = body.getBytes(UTF_8);
		}

		@Override
		public int getResponseCode() {
			return status;
		}

		@Override
		public long getContentLengthLong() {
			return declaredLength;
		}

		@Override
		public String getHeaderField(String name) {
			if ("Retry-After".equalsIgnoreCase(name)) return retryAfter;
			if ("Location".equalsIgnoreCase(name)) return location;
			return null;
		}

		@Override
		public void setRequestProperty(String key, String value) {
			requestHeaders.put(key, value);
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(body);
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
}
