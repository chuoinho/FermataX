package me.aap.fermata.addon.audiobook.download;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.audiobook.model.AudiobookBook;
import me.aap.fermata.addon.audiobook.model.AudiobookChapter;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = FermataApplication.class)
public class AudiobookDownloadManagerTest {
	private File directory;
	private AudiobookDownloadManager manager;
	private java.net.ServerSocket server;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("audiobook-download-test").toFile();
	}

	@After
	public void tearDown() throws Exception {
		if (manager != null) manager.close();
		if (server != null) server.close();
		try (var paths = Files.walk(directory.toPath())) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
	}

	private interface Handler {
		String respond(String request);
	}

	// Serves one response per connection; enough for HttpURLConnection with "Connection: close".
	private String startServer(Handler handler) throws Exception {
		server = new java.net.ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"));
		Thread thread = new Thread(() -> {
			try {
				while (true) {
					try (java.net.Socket socket = server.accept()) {
						java.io.BufferedReader in = new java.io.BufferedReader(
								new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
						StringBuilder request = new StringBuilder();
						for (String line; ((line = in.readLine()) != null) && !line.isEmpty(); ) {
							request.append(line).append('\n');
						}
						socket.getOutputStream().write(handler.respond(request.toString())
								.getBytes(StandardCharsets.UTF_8));
					}
				}
			} catch (java.io.IOException closed) {
				// The test finished.
			}
		});
		thread.setDaemon(true);
		thread.start();
		return "http://127.0.0.1:" + server.getLocalPort() + "/book.mp3";
	}

	private static String response(int status, String headers, String body) {
		return "HTTP/1.1 " + status + " X\r\n" + headers + "Content-Length: " + body.length() +
				"\r\nConnection: close\r\n\r\n" + body;
	}

	private static boolean hasRange(String request) {
		return request.contains("\nRange:") || request.startsWith("Range:");
	}

	private static String ifRange(String request) {
		for (String line : request.split("\n")) {
			if (line.startsWith("If-Range: ")) return line.substring(10);
		}
		return null;
	}

	private static String read(File file) throws Exception {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private static void write(File file, String text) throws Exception {
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private File download(String url, File partial, File complete) throws Exception {
		return AudiobookDownloadManager.downloadFile(url, java.util.Collections.emptyMap(),
				partial, complete, () -> { });
	}

	@Test
	public void resumeAppendsWhenTheFileIsUnchanged() throws Exception {
		File partial = new File(directory, "c.partial");
		File complete = new File(directory, "c.mp3");
		File validator = new File(partial.getPath() + ".validator");
		List<String> sent = java.util.Collections.synchronizedList(new ArrayList<>());
		String url = startServer(request -> {
			if (!hasRange(request)) return response(200, "ETag: \"v1\"\r\n", "HELLO WORLD");
			sent.add(ifRange(request));
			return response(206, "Content-Range: bytes 5-10/11\r\n", " WORLD");
		});
		download(url, partial, complete);
		assertEquals("HELLO WORLD", read(complete));
		assertFalse("the validator goes with the partial", validator.exists());

		// As if the first attempt was interrupted after five bytes.
		assertTrue(complete.delete());
		write(partial, "HELLO");
		write(validator, "\"v1\"");
		download(url, partial, complete);

		assertEquals(java.util.Collections.singletonList("\"v1\""), sent);
		assertEquals("HELLO WORLD", read(complete));
	}

	@Test
	public void freshDownloadRemembersTheValidatorForAResume() throws Exception {
		File partial = new File(directory, "c.partial");
		File complete = new File(directory, "c.mp3");
		// The connection drops after the headers, leaving only what a resume needs.
		String url = startServer(request ->
				"HTTP/1.1 200 X\r\nETag: \"v1\"\r\nContent-Length: 11\r\nConnection: close\r\n\r\nHELLO");

		try {
			download(url, partial, complete);
			fail("the truncated body should fail");
		} catch (java.io.IOException expected) {
			assertEquals("\"v1\"", read(new File(partial.getPath() + ".validator")));
		}
	}

	@Test
	public void weakEtagIsNotUsedAsAValidator() throws Exception {
		File partial = new File(directory, "c.partial");
		File complete = new File(directory, "c.mp3");
		String url = startServer(request -> "HTTP/1.1 200 X\r\nETag: W/\"v1\"\r\n" +
				"Last-Modified: Mon, 02 Nov 2026 00:00:00 GMT\r\nContent-Length: 11\r\nConnection: close\r\n\r\nHELLO");

		try {
			download(url, partial, complete);
			fail("the truncated body should fail");
		} catch (java.io.IOException expected) {
			assertEquals("Mon, 02 Nov 2026 00:00:00 GMT", read(new File(partial.getPath() + ".validator")));
		}
	}

	@Test
	public void resumeStartsOverWhenTheServerFileChanged() throws Exception {
		File partial = new File(directory, "c.partial");
		File complete = new File(directory, "c.mp3");
		write(partial, "OLD");
		write(new File(partial.getPath() + ".validator"), "\"v1\"");
		// If-Range no longer matches, so the server sends the new file as a plain 200.
		String url = startServer(request -> response(200, "ETag: \"v2\"\r\n", "NEW FILE"));

		download(url, partial, complete);

		assertEquals("NEW FILE", read(complete));
	}

	@Test
	public void wrongContentRangeStartsOverInsteadOfCorruptingTheFile() throws Exception {
		File partial = new File(directory, "c.partial");
		File complete = new File(directory, "c.mp3");
		write(partial, "HELLO");
		String url = startServer(request -> hasRange(request) ?
				response(206, "Content-Range: bytes 0-10/11\r\n", "HELLO WORLD") :
				response(200, "", "HELLO WORLD"));

		download(url, partial, complete);

		assertEquals("HELLO WORLD", read(complete));
	}

	@Test
	public void persistsDownloadingBeforeClientAndCompletes() throws Exception {
		FakeStore store = new FakeStore();
		AudiobookBook book = book();
		store.chapters = List.of(chapter(book));
		manager = new AudiobookDownloadManager(directory, store,
				(source, headers, partial, complete, cancellation) -> {
					store.clientCalls++;
					cancellation.checkCancelled();
					complete.getParentFile().mkdirs();
					Files.write(complete.toPath(), "audio".getBytes(StandardCharsets.UTF_8));
					return complete;
				});

		FutureSupplier<Void> task = manager.download(book);

		assertEquals(List.of(AudiobookDownloadState.DOWNLOADING), store.states);
		assertEquals(0, store.clientCalls);
		store.downloadingPersisted.complete(null);
		task.get(5, TimeUnit.SECONDS);

		assertEquals(1, store.clientCalls);
		assertEquals(List.of(AudiobookDownloadState.DOWNLOADING,
				AudiobookDownloadState.COMPLETE), store.states);
		assertTrue(store.completedPath != null);
	}

	@Test
	public void passesAuthenticatedHeadersToDownloadClient() throws Exception {
		FakeStore store = new FakeStore();
		AudiobookBook book = book();
		store.chapters = List.of(chapter(book));
		store.downloadingPersisted.complete(null);
		List<java.util.Map<String, String>> observed = new ArrayList<>();
		manager = new AudiobookDownloadManager(directory, store,
				(source, headers, partial, complete, cancellation) -> {
					observed.add(headers);
					complete.getParentFile().mkdirs();
					Files.write(complete.toPath(), "audio".getBytes(StandardCharsets.UTF_8));
					return complete;
				}, ignored -> completed(java.util.Map.of("Authorization", "Bearer token")));

		manager.download(book).get(5, TimeUnit.SECONDS);

		assertEquals(List.of(java.util.Map.of("Authorization", "Bearer token")), observed);
	}

	private static AudiobookBook book() {
		return new AudiobookBook("book-1", "source-1", "remote-1", "Book", "Author", "",
				"", "", "en", 10_000, null, 0, 0, false, 1, 1);
	}

	private static AudiobookChapter chapter(AudiobookBook book) {
		return new AudiobookChapter(book.getId(), "chapter-1", 0, "Chapter 1",
				"https://example.test/chapter.mp3", "audio/mpeg", 0, 0, 10_000, false,
				null, 0);
	}

	private static final class FakeStore implements AudiobookDownloadStore {
		final Promise<Void> downloadingPersisted = new Promise<>();
		final List<Integer> states = new ArrayList<>();
		List<AudiobookChapter> chapters = List.of();
		int clientCalls;
		String completedPath;

		@Override
		public FutureSupplier<List<AudiobookChapter>> listChapters(String bookId) {
			return completed(chapters);
		}

		@Override
		public FutureSupplier<Void> updateDownload(String bookId, String chapterId, int state,
				String localPath) {
			states.add(state);
			if (state == AudiobookDownloadState.COMPLETE) completedPath = localPath;
			return (state == AudiobookDownloadState.DOWNLOADING) ? downloadingPersisted : completedVoid();
		}

		@Override
		public FutureSupplier<Void> clearDownloads(String bookId) {
			return completedVoid();
		}
	}
}
