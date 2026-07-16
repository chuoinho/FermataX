package me.aap.fermata.addon.podcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import me.aap.fermata.FermataApplication;

import me.aap.fermata.addon.podcast.data.PodcastPlaybackSource;
import me.aap.fermata.addon.podcast.model.PodcastEpisodeRecord;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.media.lib.DefaultMediaLib;

@RunWith(RobolectricTestRunner.class)
@Config(application = FermataApplication.class)
public class PodcastEpisodeItemTest {
	@Test
	public void stableEpisodeIsInternalSeekableAndCarriesPrivateHeaders() {
		PodcastRootItem root = new PodcastRootItem(new DefaultMediaLib(
				RuntimeEnvironment.getApplication()));
		PodcastSubscription subscription = new PodcastSubscription("feed-key",
				"https://example.test/feed", null, "Road Show", "", "", "", null,
				"", "", false, null, null, 0, 0, 0);
		PodcastSubscriptionItem parent = new PodcastSubscriptionItem(root, subscription);
		PodcastEpisodeRecord record = new PodcastEpisodeRecord("feed-key", "episode-key",
				"Road Show", "Episode One", "", "Host", "https://example.test/one.mp3",
				null, "audio/mpeg", "", null, 0, 120_000, 100, false, 42_000, 0, 0, null);
		AtomicLong savedPosition = new AtomicLong(-1);
		boolean[] savedPlayed = new boolean[1];
		PodcastEpisodeItem item = new PodcastEpisodeItem(parent, record,
				new PodcastPlaybackSource("https://example.test/one.mp3", "Basic secret"), "",
				(feedKey, episodeKey, position, played, lastPlayedMs) -> {
					savedPosition.set(position);
					savedPlayed[0] = played;
					return me.aap.utils.async.Completed.completedVoid();
				});

		assertEquals("podcast:episode:feed-key:episode-key", item.getId());
		assertEquals("Episode One", item.getName());
		assertFalse(item.isExternal());
		assertFalse(item.isStream());
		assertTrue(item.isLocationSensitive());
		assertTrue(item.isSeekable());
		assertFalse(item.isVideo());
		assertEquals(42_000, item.getResumePosition());
		assertEquals(42_000, root.getLib().getLastPlayedPosition(item));
		assertEquals("Basic secret", item.getRequestHeaders().get("Authorization"));
		assertTrue(root.isChildItemId(item.getId()));

		item.savePlaybackProgress(55_000, false).getOrThrow();
		assertEquals(55_000, item.getResumePosition());
		assertEquals(55_000, savedPosition.get());
		assertFalse(savedPlayed[0]);

		item.savePlaybackProgress(0, true).getOrThrow();
		assertEquals(0, item.getResumePosition());
		assertEquals(0, savedPosition.get());
		assertTrue(savedPlayed[0]);
	}

	@Test
	public void playbackLocationTracksDownloadedFileLifecycle() throws Exception {
		PodcastRootItem root = new PodcastRootItem(new DefaultMediaLib(
				RuntimeEnvironment.getApplication()));
		PodcastSubscription subscription = new PodcastSubscription("feed-key",
				"https://example.test/feed", null, "Road Show", "", "", "", null,
				"", "", false, null, null, 0, 0, 0);
		PodcastSubscriptionItem parent = new PodcastSubscriptionItem(root, subscription);
		PodcastEpisodeRecord record = new PodcastEpisodeRecord("feed-key", "episode-key",
				"Road Show", "Private Episode", "", "Host",
				"https://example.test/private.mp3", "media-credential", "audio/mpeg", "",
				null, 0, 120_000, 100, false, 0, 0, 0, null);
		File directory = Files.createTempDirectory("podcast-episode-item-test").toFile();
		File downloaded = new File(directory, "episode.media");
		PodcastEpisodeItem item = new PodcastEpisodeItem(parent, record,
				new PodcastPlaybackSource(record.getMediaUrl(), "Basic secret"),
				() -> me.aap.utils.async.Completed.completed(""), null, downloaded);

		try {
			assertFalse(item.isDownloaded());
			assertEquals(record.getMediaUrl(), item.getLocation().toString());
			assertEquals("Basic secret", item.getRequestHeaders().get("Authorization"));

			Files.write(downloaded.toPath(), new byte[] {1, 2, 3});
			assertTrue(item.isDownloaded());
			assertEquals(Uri.fromFile(downloaded), item.getLocation());
			assertTrue(item.getRequestHeaders().isEmpty());

			assertTrue(downloaded.delete());
			assertFalse(item.isDownloaded());
			assertEquals(record.getMediaUrl(), item.getLocation().toString());
			assertEquals("Basic secret", item.getRequestHeaders().get("Authorization"));
		} finally {
			downloaded.delete();
			directory.delete();
		}
	}
}
