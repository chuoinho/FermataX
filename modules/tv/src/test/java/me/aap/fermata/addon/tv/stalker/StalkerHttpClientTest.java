package me.aap.fermata.addon.tv.stalker;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import me.aap.utils.async.FutureSupplier;
import me.aap.fermata.FermataApplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = FermataApplication.class)
public class StalkerHttpClientTest {
	private static final String MAC = "00:1A:79:01:02:03";

	@Test
	public void healthCheckPassesThroughCompressedCatalogAndBoundedProbe() throws Exception {
		AtomicReference<String> range = new AtomicReference<>();
		AtomicReference<String> cookie = new AtomicReference<>();
		AtomicReference<String> command = new AtomicReference<>();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				if ("/stream/master.m3u8".equals(request.path())) {
					range.set(request.header("Range"));
					request.respond(206, "#", null);
					return;
				}
				if (!"/server/load.php".equals(request.path())) {
					request.respond(404, "", null);
					return;
				}
				Map<String, String> query = request.query();
				cookie.set(request.header("Cookie"));
				switch (query.get("action")) {
					case "handshake" -> request.respond(200,
							"{\"js\":{\"token\":\"token-one\"}}", null);
					case "get_profile" -> request.respond(200,
							"{\"js\":{\"id\":\"1\"}}", null);
					case "get_genres" -> request.respond(200,
							"{\"js\":[{\"id\":\"1\",\"title\":\"News\"}]}", "gzip");
					case "get_all_channels" -> request.respond(200,
							"{\"js\":{\"data\":[{\"id\":\"10\",\"name\":\"World\",\"tv_genre_id\":\"1\",\"cmd\":\"ffmpeg http://upstream.invalid/live/10|User-Agent=Portal%20Agent\"}]}}",
							"deflate");
					case "create_link" -> {
						command.set(query.get("cmd"));
						request.respond(200, "{\"js\":{\"cmd\":\"ffmpeg " +
								server.url("/stream/master.m3u8") + "\"}}", null);
					}
					default -> request.respond(400, "", null);
				}
			});

			StalkerAccount account = account(server.url("/c/"), 3);
			StalkerHttpClient http = new StalkerHttpClient(account, null);
			StalkerHealth health = await(new StalkerHealthChecker(account, http, null).check());

			assertEquals(StalkerHealth.Status.PASS, health.getStatus());
			assertEquals(1, health.getCategoryCount());
			assertEquals(1, health.getChannelCount());
			assertEquals(1, health.getStreamAttempts());
			assertEquals(206, health.getStreamStatusCode());
			assertEquals("bytes=0-0", range.get());
			assertTrue(cookie.get().contains(MAC));
			assertEquals("ffmpeg http://upstream.invalid/live/10|User-Agent=Portal%20Agent",
					command.get());
			assertTrue(health.getSteps().get(StalkerHealth.Stage.HANDSHAKE).successful());
			assertTrue(health.getSteps().get(StalkerHealth.Stage.STREAM_PROBE).successful());
		}
	}

	@Test
	public void returnsDegradedAfterThreeFailedStreamSamples() throws Exception {
		AtomicInteger createLinks = new AtomicInteger();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				if (!"/server/load.php".equals(request.path())) {
					request.respond(404, "missing", null);
					return;
				}
				String action = request.query().get("action");
				if ("get_genres".equals(action)) {
					request.respond(200,
							"{\"js\":[{\"id\":\"1\",\"title\":\"News\"}]}", null);
				} else if ("get_all_channels".equals(action)) {
					request.respond(200, "{\"js\":{\"data\":[" + channel("1") + ',' +
							channel("2") + ',' + channel("3") + ',' + channel("4") + "]}}", null);
				} else if ("create_link".equals(action)) {
					int id = createLinks.incrementAndGet();
					request.respond(200, "{\"js\":{\"cmd\":\"" + server.url("/dead/" + id) +
							"\"}}", null);
				} else {
					defaultPortalResponse(request, action);
				}
			});
			StalkerAccount account = account(server.url("/"), 3);
			StalkerHttpClient http = new StalkerHttpClient(account, null);
			StalkerHealth health = await(new StalkerHealthChecker(account, http, null).check());

			assertTrue(health.isDegraded());
			assertEquals(3, health.getStreamAttempts());
			assertEquals(3, createLinks.get());
			assertEquals(404, health.getStreamStatusCode());
			assertFalse(health.getSteps().get(StalkerHealth.Stage.STREAM_PROBE).successful());
			assertNotNull(health.getWarning());
		}
	}

	@Test
	public void discoversHostOnlyStalkerPortalFallback() throws Exception {
		List<String> attempted = new CopyOnWriteArrayList<>();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				attempted.add(request.path());
				if (!"/stalker_portal/server/load.php".equals(request.path())) {
					request.respond(404, "", null);
					return;
				}
				defaultPortalResponse(request, request.query().get("action"));
			});
			StalkerHttpClient http = new StalkerHttpClient(account(server.url("/"), 3), null);

			await(http.authenticate());

			assertTrue(attempted.contains("/server/load.php"));
			assertTrue(attempted.contains("/portal.php"));
			assertTrue(attempted.contains("/stalker_portal/server/load.php"));
			assertEquals(200, http.getLastStatusCode());
		}
	}

	@Test
	public void retriesExpiredTokenExactlyOnce() throws Exception {
		AtomicInteger handshakes = new AtomicInteger();
		AtomicInteger profiles = new AtomicInteger();
		AtomicInteger categories = new AtomicInteger();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				String action = request.query().get("action");
				if ("handshake".equals(action)) {
					int count = handshakes.incrementAndGet();
					request.respond(200,
							"{\"js\":{\"token\":\"token-" + count + "\"}}", null);
				} else if ("get_profile".equals(action)) {
					profiles.incrementAndGet();
					request.respond(200, "{\"js\":{\"id\":\"1\"}}", null);
				} else if ("get_genres".equals(action) && (categories.incrementAndGet() == 1)) {
					request.respond(401, "", null);
				} else if ("get_genres".equals(action)) {
					request.respond(200,
							"{\"js\":[{\"id\":\"1\",\"title\":\"News\"}]}", null);
				} else {
					request.respond(404, "", null);
				}
			});
			StalkerHttpClient http = new StalkerHttpClient(account(server.url("/"), 3), null);

			assertEquals(1, await(http.getCategories()).size());
			assertEquals(2, handshakes.get());
			assertEquals(2, profiles.get());
			assertEquals(2, categories.get());
		}
	}

	@Test
	public void loadsPagedVodSeriesAndBuildsPlaybackCommands() throws Exception {
		AtomicReference<String> vodCategory = new AtomicReference<>();
		AtomicReference<String> seriesCategory = new AtomicReference<>();
		AtomicReference<String> movieId = new AtomicReference<>();
		AtomicReference<String> vodSeries = new AtomicReference<>();
		AtomicReference<String> episodeSeries = new AtomicReference<>();
		AtomicReference<String> archiveCommand = new AtomicReference<>();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				Map<String, String> query = request.query();
				String action = query.get("action");
				String type = query.get("type");
				if ("get_ordered_list".equals(action) && "vod".equals(type)) {
					vodCategory.set(query.get("category"));
					String page = query.get("p");
					request.respond(200, "{\"js\":{\"total_items\":\"2\"," +
							"\"max_page_items\":\"1\",\"data\":[{\"id\":\"" + page +
							"\",\"name\":\"Movie " + page +
							"\",\"cmd\":\"ffmpeg http://stream.invalid/movie/" + page +
							"\"}]}}", null);
				} else if ("get_ordered_list".equals(action) && "series".equals(type) &&
						(query.get("movie_id") == null)) {
					seriesCategory.set(query.get("category"));
					request.respond(200, "{\"js\":{\"total_items\":\"1\"," +
							"\"max_page_items\":\"1\",\"data\":[{\"id\":\"77\"," +
							"\"name\":\"Series 77\"}]}}", null);
				} else if ("get_ordered_list".equals(action) && "series".equals(type)) {
					movieId.set(query.get("movie_id"));
					request.respond(200, "{\"js\":{\"total_items\":\"1\"," +
							"\"max_page_items\":\"1\",\"data\":[{\"id\":\"s1\"," +
							"\"cmd\":\"ffmpeg http://stream.invalid/series/77\"," +
							"\"series\":[1,2]}]}}", null);
				} else if ("create_link".equals(action)) {
					if ("tv_archive".equals(type)) archiveCommand.set(query.get("cmd"));
					else if ("0".equals(query.get("series"))) vodSeries.set(query.get("series"));
					else episodeSeries.set(query.get("series"));
					request.respond(200, "{\"js\":{\"cmd\":\"ffmpeg " +
							server.url("/stream/content.m3u8") + "\"}}", null);
				} else {
					defaultPortalResponse(request, action);
				}
			});
			StalkerHttpClient client = new StalkerHttpClient(account(server.url("/"), 3), null);

			assertEquals(2, await(client.getVod("*")).size());
			assertEquals(1, await(client.getSeries("drama")).size());
			List<StalkerSeason> seasons = await(client.getSeasons("77"));
			assertEquals(2, seasons.get(0).episodes().size());
			await(client.createVodLink("ffmpeg http://upstream.invalid/movie", "0"));
			await(client.createVodLink("ffmpeg http://upstream.invalid/series", "2"));
			await(client.createArchiveLink("900"));

			assertEquals("*", vodCategory.get());
			assertEquals("drama", seriesCategory.get());
			assertEquals("77", movieId.get());
			assertEquals("0", vodSeries.get());
			assertEquals("2", episodeSeries.get());
			assertEquals("auto /media/900.mpg", archiveCommand.get());
		}
	}

	@Test
	public void stopsCatalogPaginationWhenPortalRepeatsAPage() throws Exception {
		AtomicInteger pages = new AtomicInteger();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				String action = request.query().get("action");
				if ("get_ordered_list".equals(action)) {
					pages.incrementAndGet();
					request.respond(200, "{\"js\":{\"max_page_items\":\"1\"," +
							"\"data\":[{\"id\":\"same\",\"name\":\"Same\"," +
							"\"cmd\":\"ffmpeg http://stream.invalid/same\"}]}}", null);
				} else {
					defaultPortalResponse(request, action);
				}
			});
			StalkerHttpClient client = new StalkerHttpClient(account(server.url("/"), 3), null);

			assertEquals(1, await(client.getVod("*")).size());
			assertEquals(2, pages.get());
		}
	}

	@Test
	public void fallsBackToShortEpgWhenHistoricalEndpointIsUnavailable() throws Exception {
		AtomicInteger historical = new AtomicInteger();
		AtomicInteger shortEpg = new AtomicInteger();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				String action = request.query().get("action");
				if ("get_simple_data_table".equals(action)) {
					historical.incrementAndGet();
					request.respond(400, "", null);
				} else if ("get_short_epg".equals(action)) {
					shortEpg.incrementAndGet();
					request.respond(200, "{\"js\":{\"data\":[{\"id\":\"901\"," +
							"\"ch_id\":\"10\",\"start_timestamp\":\"1788200000\"," +
							"\"stop_timestamp\":\"1788203600\",\"name\":\"News\"}]}}", null);
				} else {
					defaultPortalResponse(request, action);
				}
			});
			StalkerHttpClient client = new StalkerHttpClient(account(server.url("/"), 3), null);

			List<StalkerEpgProgram> epg = await(client.getEpg("10"));
			assertEquals(1, epg.size());
			assertEquals("901", epg.get(0).id());
			assertEquals(2, historical.get());
			assertEquals(1, shortEpg.get());
		}
	}

	@Test
	public void rejectsEmptyCategoryAndChannelCatalogs() throws Exception {
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				String action = request.query().get("action");
				if ("get_genres".equals(action)) request.respond(200, "{\"js\":[]}", null);
				else defaultPortalResponse(request, action);
			});
			StalkerAccount account = account(server.url("/server/load.php"), 3);
			StalkerHttpClient http = new StalkerHttpClient(account, null);
			ExecutionException failure = assertThrows(ExecutionException.class, () ->
					await(new StalkerHealthChecker(account, http, null).check()));
			assertTrue(failure.getCause().getMessage().contains("no channel categories"));
		}

		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				String action = request.query().get("action");
				if ("get_genres".equals(action)) {
					request.respond(200,
							"{\"js\":[{\"id\":\"1\",\"title\":\"News\"}]}", null);
				} else if ("get_all_channels".equals(action)) {
					request.respond(200, "{\"js\":{\"data\":[]}}", null);
				} else {
					defaultPortalResponse(request, action);
				}
			});
			StalkerAccount account = account(server.url("/server/load.php"), 3);
			StalkerHttpClient http = new StalkerHttpClient(account, null);
			ExecutionException failure = assertThrows(ExecutionException.class, () ->
					await(new StalkerHealthChecker(account, http, null).check()));
			assertTrue(failure.getCause().getMessage().contains("no playable channels"));
		}
	}

	@Test
	public void retainsSensitiveProbeHeadersOnlyOnSameOriginRedirects() throws Exception {
		AtomicReference<String> sameCookie = new AtomicReference<>();
		AtomicReference<String> sameAuthorization = new AtomicReference<>();
		AtomicReference<String> crossCookie = new AtomicReference<>();
		AtomicReference<String> crossAuthorization = new AtomicReference<>();
		try (TestServer target = new TestServer(); TestServer source = new TestServer()) {
			target.handle(request -> {
				crossCookie.set(request.header("Cookie"));
				crossAuthorization.set(request.header("Authorization"));
				request.respond(206, "x", null);
			});
			source.handle(request -> {
				switch (request.path()) {
					case "/same-start" -> request.redirect(source.url("/same-target"));
					case "/same-target" -> {
						sameCookie.set(request.header("Cookie"));
						sameAuthorization.set(request.header("Authorization"));
						request.respond(206, "x", null);
					}
					case "/cross-start" -> request.redirect(target.url("/target"));
					default -> request.respond(404, "", null);
				}
			});
			StalkerHttpClient http = new StalkerHttpClient(account(source.url("/"), 3), null);
			Map<String, String> sensitive = Map.of("Cookie", "secret-cookie",
					"Authorization", "Bearer secret-token");

			assertEquals(206, await(http.probe(new StalkerPlaybackLink(
					URI.create(source.url("/same-start")), sensitive))).statusCode());
			assertEquals("secret-cookie", sameCookie.get());
			assertEquals("Bearer secret-token", sameAuthorization.get());

			assertEquals(206, await(http.probe(new StalkerPlaybackLink(
					URI.create(source.url("/cross-start")), sensitive))).statusCode());
			assertNull(crossCookie.get());
			assertNull(crossAuthorization.get());
		}
	}

	@Test
	public void handlesStreamStatusCodesAndSendsBoundedRange() throws Exception {
		AtomicInteger rangedRequests = new AtomicInteger();
		try (TestServer server = new TestServer()) {
			server.handle(request -> {
				if ("bytes=0-0".equals(request.header("Range"))) rangedRequests.incrementAndGet();
				int status = Integer.parseInt(request.path().substring(1));
				request.respond(status, "stream payload", null);
			});
			StalkerHttpClient http = new StalkerHttpClient(account(server.url("/"), 3), null);

			for (int status : new int[]{200, 206}) {
				assertEquals(status, await(http.probe(link(server, "/" + status))).statusCode());
			}
			for (int status : new int[]{401, 403, 404, 503}) {
				assertThrows(ExecutionException.class,
						() -> await(http.probe(link(server, "/" + status))));
				assertEquals(status, http.getLastStatusCode());
			}
			assertEquals(6, rangedRequests.get());
		}
	}

	@Test
	public void rejectsHtmlAndHonorsTimeoutAndCancellation() throws Exception {
		try (TestServer html = new TestServer()) {
			html.handle(request -> request.respond(200, "<html>not json</html>", null));
			StalkerHttpClient client = new StalkerHttpClient(account(html.url("/"), 2), null);
			assertThrows(ExecutionException.class, () -> await(client.authenticate()));
		}

		try (TestServer slow = new TestServer()) {
			slow.handle(request -> {
				try {
					Thread.sleep(1500);
					request.respond(200, "x", null);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			});
			StalkerHttpClient client = new StalkerHttpClient(account(slow.url("/"), 1), null);
			ExecutionException failure = assertThrows(ExecutionException.class,
					() -> await(client.probe(link(slow, "/slow"))));
			assertTrue(failure.getCause().getMessage().contains("did not respond"));
		}

		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (TestServer blocked = new TestServer()) {
			blocked.handle(request -> {
				request.beginChunked(200);
				started.countDown();
				try {
					release.await(3, SECONDS);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			});
			StalkerHttpClient client = new StalkerHttpClient(account(blocked.url("/"), 5), null);
			FutureSupplier<StalkerProbeResult> future = client.probe(link(blocked, "/blocked"));
			assertTrue(started.await(2, SECONDS));
			assertTrue(future.cancel(true));
			assertTrue(future.isCancelled());
			release.countDown();
		}
	}

	private static StalkerPlaybackLink link(TestServer server, String path) {
		return new StalkerPlaybackLink(URI.create(server.url(path)), Map.of());
	}

	private static String channel(String id) {
		return "{\"id\":\"" + id + "\",\"name\":\"Channel " + id +
				"\",\"tv_genre_id\":\"1\",\"cmd\":\"ffmpeg http://upstream.invalid/" +
				id + "\"}";
	}

	private static void defaultPortalResponse(Request request, String action) throws IOException {
		if ("handshake".equals(action)) {
			request.respond(200, "{\"js\":{\"token\":\"token\"}}", null);
		} else if ("get_profile".equals(action)) {
			request.respond(200, "{\"js\":{\"id\":\"1\"}}", null);
		} else {
			request.respond(400, "", null);
		}
	}

	private static StalkerAccount account(String portal, int timeout) {
		return new StalkerAccount(1, "Test", portal, MAC, "serial", "device", "Test Agent",
				timeout);
	}

	private static <T> T await(FutureSupplier<T> future) throws Exception {
		return future.get(10, SECONDS);
	}

	@FunctionalInterface
	private interface RequestHandler {
		void handle(Request request) throws IOException;
	}

	private static final class TestServer implements AutoCloseable {
		private final ServerSocket server;
		private final ExecutorService executor;
		private final Thread acceptThread;
		private volatile RequestHandler handler;

		TestServer() throws IOException {
			server = new ServerSocket();
			server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
			executor = Executors.newCachedThreadPool(task -> {
				Thread thread = new Thread(task, "stalker-mock-client");
				thread.setDaemon(true);
				return thread;
			});
			acceptThread = new Thread(this::accept, "stalker-mock-accept");
			acceptThread.setDaemon(true);
			acceptThread.start();
		}

		void handle(RequestHandler handler) {
			this.handler = handler;
		}

		String url(String path) {
			String suffix = path.startsWith("/") ? path : '/' + path;
			return "http://127.0.0.1:" + server.getLocalPort() + suffix;
		}

		private void accept() {
			while (!server.isClosed()) {
				try {
					Socket socket = server.accept();
					executor.execute(() -> serve(socket));
				} catch (SocketException closed) {
					if (!server.isClosed()) throw new AssertionError(closed);
				} catch (IOException failure) {
					throw new AssertionError(failure);
				}
			}
		}

		private void serve(Socket socket) {
			try (socket) {
				Request request = Request.read(socket);
				RequestHandler current = handler;
				if (current == null) request.respond(503, "", null);
				else current.handle(request);
			} catch (IOException ignored) {
				// Client cancellation and early probe close are expected in these tests.
			}
		}

		@Override
		public void close() throws IOException {
			server.close();
			executor.shutdownNow();
			acceptThread.interrupt();
		}
	}

	private static final class Request {
		private final URI target;
		private final Map<String, String> headers;
		private final OutputStream output;

		private Request(URI target, Map<String, String> headers, OutputStream output) {
			this.target = target;
			this.headers = headers;
			this.output = output;
		}

		static Request read(Socket socket) throws IOException {
			InputStream input = new BufferedInputStream(socket.getInputStream());
			String requestLine = line(input);
			if ((requestLine == null) || requestLine.isEmpty()) {
				throw new IOException("Missing request line");
			}
			String[] parts = requestLine.split(" ", 3);
			if (parts.length < 2) throw new IOException("Invalid request line");
			Map<String, String> headers = new LinkedHashMap<>();
			for (String line; (line = line(input)) != null && !line.isEmpty(); ) {
				int separator = line.indexOf(':');
				if (separator > 0) headers.put(line.substring(0, separator).trim()
						.toLowerCase(Locale.ROOT), line.substring(separator + 1).trim());
			}
			return new Request(URI.create(parts[1]), headers, socket.getOutputStream());
		}

		String path() {
			return target.getPath();
		}

		String header(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

		Map<String, String> query() {
			String raw = target.getRawQuery();
			if ((raw == null) || raw.isEmpty()) return Map.of();
			Map<String, String> result = new LinkedHashMap<>();
			for (String part : raw.split("&")) {
				int separator = part.indexOf('=');
				String name = (separator < 0) ? part : part.substring(0, separator);
				String value = (separator < 0) ? "" : part.substring(separator + 1);
				result.put(URLDecoder.decode(name, UTF_8), URLDecoder.decode(value, UTF_8));
			}
			return result;
		}

		void redirect(String location) throws IOException {
			write(302, new byte[0], null, Map.of("Location", location));
		}

		void respond(int status, String body, String encoding) throws IOException {
			byte[] bytes = body.getBytes(UTF_8);
			if (encoding != null) bytes = compress(bytes, encoding);
			write(status, bytes, encoding, Map.of());
		}

		void beginChunked(int status) throws IOException {
			String response = "HTTP/1.1 " + status + ' ' + reason(status) + "\r\n" +
					"Content-Type: application/octet-stream\r\n" +
					"Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n";
			output.write(response.getBytes(ISO_8859_1));
			output.flush();
		}

		private void write(int status, byte[] body, String encoding,
				Map<String, String> extraHeaders) throws IOException {
			StringBuilder response = new StringBuilder("HTTP/1.1 ").append(status).append(' ')
					.append(reason(status)).append("\r\nContent-Type: application/json\r\n")
					.append("Content-Length: ").append(body.length).append("\r\n")
					.append("Connection: close\r\n");
			if (encoding != null) response.append("Content-Encoding: ").append(encoding)
					.append("\r\n");
			for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
				response.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
			}
			response.append("\r\n");
			output.write(response.toString().getBytes(ISO_8859_1));
			output.write(body);
			output.flush();
		}

		private static String line(InputStream input) throws IOException {
			ByteArrayOutputStream line = new ByteArrayOutputStream();
			for (int value; (value = input.read()) != -1; ) {
				if (value == '\n') break;
				if (value != '\r') line.write(value);
				if (line.size() > 32_768) throw new IOException("HTTP line is too long");
			}
			return line.toString(ISO_8859_1);
		}

		private static byte[] compress(byte[] input, String encoding) throws IOException {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if ("gzip".equals(encoding)) {
				try (GZIPOutputStream compressed = new GZIPOutputStream(output)) {
					compressed.write(input);
				}
			} else {
				try (DeflaterOutputStream compressed = new DeflaterOutputStream(output)) {
					compressed.write(input);
				}
			}
			return output.toByteArray();
		}

		private static String reason(int status) {
			return switch (status) {
				case 200 -> "OK";
				case 206 -> "Partial Content";
				case 302 -> "Found";
				case 400 -> "Bad Request";
				case 401 -> "Unauthorized";
				case 403 -> "Forbidden";
				case 404 -> "Not Found";
				case 503 -> "Service Unavailable";
				default -> "Status";
			};
		}
	}
}
