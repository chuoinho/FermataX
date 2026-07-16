package me.aap.fermata.addon.audiobook.download;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
@Config(application = FermataApplication.class)
public class AudiobookDownloadManagerTest {
	private File directory;
	private AudiobookDownloadManager manager;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("audiobook-download-test").toFile();
	}

	@After
	public void tearDown() throws Exception {
		if (manager != null) manager.close();
		try (var paths = Files.walk(directory.toPath())) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
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
