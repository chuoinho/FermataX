package me.aap.fermata.addon.podcast.net;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.podcast.download.PodcastDownloadResult;

public class PodcastDownloadClientTest {
	private File directory;

	@Before
	public void createDirectory() throws Exception {
		directory = Files.createTempDirectory("podcast-download-test").toFile();
	}

	@After
	public void removeDirectory() throws Exception {
		try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(directory.toPath())) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
	}

	@Test
	public void resumes206AndAtomicallyMovesCompletedFile() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		Files.write(partial.toPath(), "abc".getBytes(UTF_8));
		FakeConnection response = new FakeConnection(206, "def");
		response.contentRange = "bytes 3-5/6";
		FakeClient client = new FakeClient(response);

		PodcastDownloadResult result = client.download("https://example.test/episode.mp3",
				Map.of("Authorization", "Basic secret"), partial, complete, "etag-v1", null,
				null);

		assertEquals("bytes=3-", response.requestHeaders.get("Range"));
		assertEquals("etag-v1", response.requestHeaders.get("If-Range"));
		assertEquals("Basic secret", response.requestHeaders.get("Authorization"));
		assertEquals("abcdef", new String(Files.readAllBytes(complete.toPath()), UTF_8));
		assertFalse(partial.exists());
		assertEquals(6, result.bytes());
	}

	@Test
	public void mismatchedContentRangeRestartsFromZero() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		Files.write(partial.toPath(), "stale".getBytes(UTF_8));
		FakeConnection mismatched = new FakeConnection(206, "bad");
		mismatched.contentRange = "bytes 0-2/3";
		FakeConnection restarted = new FakeConnection(200, "fresh");

		new FakeClient(mismatched, restarted).download(
				"https://example.test/episode.mp3", Map.of(), partial, complete,
				"etag-v1", null, null);

		assertEquals("bytes=5-", mismatched.requestHeaders.get("Range"));
		assertEquals("etag-v1", mismatched.requestHeaders.get("If-Range"));
		assertFalse(restarted.requestHeaders.containsKey("Range"));
		assertEquals("fresh", new String(Files.readAllBytes(complete.toPath()), UTF_8));
	}

	@Test
	public void serverIgnoringRangeRestartsInsteadOfAppendingCorruptData() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		Files.write(partial.toPath(), "stale".getBytes(UTF_8));
		FakeConnection response = new FakeConnection(200, "fresh");

		new FakeClient(response).download("https://example.test/episode.mp3", Map.of(),
				partial, complete, "etag-v1", null, null);

		assertEquals("bytes=5-", response.requestHeaders.get("Range"));
		assertEquals("etag-v1", response.requestHeaders.get("If-Range"));
		assertEquals("fresh", new String(Files.readAllBytes(complete.toPath()), UTF_8));
	}

	@Test
	public void crossOriginRedirectDropsAuthorization() throws Exception {
		FakeConnection redirect = new FakeConnection(302, "");
		redirect.location = "https://cdn.test/episode.mp3";
		FakeConnection body = new FakeConnection(200, "audio");
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");

		new FakeClient(redirect, body).download("https://example.test/episode.mp3",
				Map.of("Authorization", "Basic secret"), partial, complete, null);

		assertTrue(redirect.requestHeaders.containsKey("Authorization"));
		assertFalse(body.requestHeaders.containsKey("Authorization"));
	}

	@Test
	public void rangeNotSatisfiableRestartsFromZero() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		Files.write(partial.toPath(), "stale".getBytes(UTF_8));
		FakeConnection rejected = new FakeConnection(416, "");
		FakeConnection restarted = new FakeConnection(200, "fresh");

		new FakeClient(rejected, restarted).download("https://example.test/episode.mp3",
				Map.of(), partial, complete, null);

		assertEquals("bytes=5-", rejected.requestHeaders.get("Range"));
		assertFalse(restarted.requestHeaders.containsKey("Range"));
		assertEquals("fresh", new String(Files.readAllBytes(complete.toPath()), UTF_8));
	}

	@Test
	public void rejectsDownloadWhenStorageIsExhausted() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		FakeConnection response = new FakeConnection(200, "audio");
		FakeClient client = new FakeClient(response) {
			@Override
			protected long usableSpace(File directory) {
				return 0;
			}
		};

		PodcastException error = assertThrows(PodcastException.class, () -> client.download(
				"https://example.test/episode.mp3", Map.of(), partial, complete, null));
		assertEquals(PodcastErrorCode.TOO_LARGE, error.getCode());
		assertFalse(complete.exists());
	}

	@Test
	public void cancellationAfterLastWritePreventsAtomicCommit() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		FakeConnection response = new FakeConnection(200, "audio");
		PodcastDownloadClient.ProgressListener cancellation =
				new PodcastDownloadClient.ProgressListener() {
			private boolean cancelled;

			@Override
			public void onProgress(long downloaded, long total) {
				cancelled = true;
			}

			@Override
			public boolean isCancelled() {
				return cancelled;
			}
		};

		assertThrows(CancellationException.class, () -> new FakeClient(response).download(
				"https://example.test/episode.mp3", Map.of(), partial, complete, cancellation));
		assertTrue(partial.isFile());
		assertFalse(complete.exists());
	}

	@Test
	public void truncatedResponseKeepsOnlyPartialFile() throws Exception {
		File partial = new File(directory, "episode.partial");
		File complete = new File(directory, "episode.media");
		FakeConnection response = new FakeConnection(200, "short");
		response.declaredLength = 10;

		assertThrows(IOException.class, () -> new FakeClient(response).download(
				"https://example.test/episode.mp3", Map.of(), partial, complete, null));
		assertEquals(5, partial.length());
		assertFalse(complete.exists());
	}

	private static class FakeClient extends PodcastDownloadClient {
		private final Queue<HttpURLConnection> connections = new ArrayDeque<>();

		FakeClient(HttpURLConnection... values) {
			connections.addAll(java.util.List.of(values));
		}

		@Override
		protected HttpURLConnection open(String url) {
			return connections.remove();
		}
	}

	private static final class FakeConnection extends HttpURLConnection {
		private final int status;
		private final byte[] body;
		private final Map<String, String> requestHeaders = new HashMap<>();
		String location;
		String contentRange;
		long declaredLength = -1;

		FakeConnection(int status, String body) throws Exception {
			super(new URL("https://example.test"));
			this.status = status;
			this.body = body.getBytes(UTF_8);
		}

		@Override public int getResponseCode() { return status; }
		@Override public long getContentLengthLong() {
			return (declaredLength < 0) ? body.length : declaredLength;
		}
		@Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
		@Override public String getHeaderField(String name) {
			if ("Location".equalsIgnoreCase(name)) return location;
			if ("Content-Range".equalsIgnoreCase(name)) return contentRange;
			return null;
		}
		@Override public void setRequestProperty(String key, String value) {
			requestHeaders.put(key, value);
		}
		@Override public void disconnect() {}
		@Override public boolean usingProxy() { return false; }
		@Override public void connect() throws IOException {}
	}
}
