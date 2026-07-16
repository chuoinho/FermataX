package me.aap.fermata.addon.podcast.download;

import static me.aap.utils.async.Completed.completedVoid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.net.PodcastDownloadClient;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

@RunWith(RobolectricTestRunner.class)
@Config(application = FermataApplication.class)
public class PodcastDownloadCoordinatorTest {
	private File directory;
	private PodcastDownloadCoordinator coordinator;

	@Before
	public void createDirectory() throws Exception {
		directory = Files.createTempDirectory("podcast-download-coordinator-test").toFile();
	}

	@After
	public void cleanup() throws Exception {
		if (coordinator != null) coordinator.close();
		try (var paths = Files.walk(directory.toPath())) {
			paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
	}

	@Test
	public void startsNetworkOnlyAfterDownloadingStateIsPersisted() throws Exception {
		FakeStore store = new FakeStore();
		AtomicInteger requests = new AtomicInteger();
		PodcastDownloadClient client = new PodcastDownloadClient() {
			@Override
			public PodcastDownloadResult download(String sourceUrl,
					Map<String, String> requestHeaders, File partial, File complete, String etag,
					String lastModified, ProgressListener listener) throws java.io.IOException {
				requests.incrementAndGet();
				assertEquals("etag-v1", etag);
				assertEquals("date-v1", lastModified);
				File parent = complete.getParentFile();
				if ((parent != null) && !parent.isDirectory() && !parent.mkdirs()) {
					throw new java.io.IOException("Cannot create test download directory");
				}
				Files.write(complete.toPath(), "audio".getBytes(UTF_8));
				return new PodcastDownloadResult(complete, 5, 5, null, null);
			}
		};
		coordinator = new PodcastDownloadCoordinator(directory, store, client);

		FutureSupplier<File> download = coordinator.download(episode());

		assertEquals(List.of(PodcastDownloadState.DOWNLOADING), store.states);
		assertEquals(0, requests.get());
		assertFalse(download.isDone());

		store.downloadingPersisted.complete(null);
		File result = download.get(5, TimeUnit.SECONDS);

		assertTrue(result.isFile());
		assertEquals(1, requests.get());
		assertEquals(List.of(PodcastDownloadState.DOWNLOADING,
				PodcastDownloadState.COMPLETE), store.states);
	}

	@Test
	public void deleteCancelsPendingDownloadBeforeNetworkStarts() throws Exception {
		FakeStore store = new FakeStore();
		AtomicInteger requests = new AtomicInteger();
		PodcastDownloadClient client = new PodcastDownloadClient() {
			@Override
			public PodcastDownloadResult download(String sourceUrl,
					Map<String, String> requestHeaders, File partial, File complete, String etag,
					String lastModified, ProgressListener listener) {
				requests.incrementAndGet();
				throw new AssertionError("Cancelled download reached the network");
			}
		};
		coordinator = new PodcastDownloadCoordinator(directory, store, client);

		FutureSupplier<File> pending = coordinator.download(episode());
		coordinator.delete(episode()).get(5, TimeUnit.SECONDS);
		store.downloadingPersisted.complete(null);
		Thread.sleep(100);

		assertTrue(pending.isCancelled());
		assertEquals(0, requests.get());
		assertEquals(1, store.deleteCalls.get());
		assertFalse(PodcastDownloadFiles.complete(directory, episode()).exists());
		assertFalse(PodcastDownloadFiles.partial(directory, episode()).exists());
	}

	private static PodcastEpisodeRecord episode() {
		return new PodcastEpisodeRecord("feed-key", "episode-key", "Road Show", "Episode",
				"", "Host", "https://example.test/episode.mp3", null, "audio/mpeg", "",
				null, 0, 60_000, 5, false, 0, 0, PodcastDownloadState.NONE, null);
	}

	private static final class FakeStore implements PodcastDownloadStore {
		final Promise<Void> downloadingPersisted = new Promise<>();
		final List<Integer> states = new ArrayList<>();
		final PodcastDownloadInfo info = new PodcastDownloadInfo("etag-v1", "date-v1");
		final AtomicInteger deleteCalls = new AtomicInteger();

		@Override
		public PodcastPlaybackSource resolveNetworkPlayback(PodcastEpisodeRecord episode) {
			return new PodcastPlaybackSource(episode.getMediaUrl(), null);
		}

		@Override
		public FutureSupplier<PodcastDownloadInfo> getDownloadInfo(String feedKey,
				String episodeKey) {
			return me.aap.utils.async.Completed.completed(info);
		}

		@Override
		public FutureSupplier<Void> updateDownload(String feedKey, String episodeKey, int state,
				String localPath, String tempPath, long downloaded, long total, String etag,
				String lastModified, String errorCode) {
			states.add(state);
			return (state == PodcastDownloadState.DOWNLOADING) ? downloadingPersisted :
					completedVoid();
		}

		@Override
		public FutureSupplier<Void> deleteDownloadState(String feedKey, String episodeKey) {
			deleteCalls.incrementAndGet();
			return completedVoid();
		}
	}
}
