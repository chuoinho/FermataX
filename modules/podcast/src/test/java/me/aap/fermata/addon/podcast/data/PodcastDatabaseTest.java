package me.aap.fermata.addon.podcast.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.addon.podcast.model.PodcastEpisode;
import me.aap.fermata.addon.podcast.model.PodcastSubscription;
import me.aap.fermata.addon.podcast.download.PodcastDownloadInfo;
import me.aap.fermata.addon.podcast.download.PodcastDownloadState;

@RunWith(RobolectricTestRunner.class)
public class PodcastDatabaseTest {
	private SQLiteDatabase database;

	@Before
	public void createDatabase() {
		database = SQLiteDatabase.create(null);
		PodcastDatabase.initialize(database);
	}

	@After
	public void closeDatabase() {
		database.close();
	}

	@Test
	public void createsSchemaAndUpsertPreservesPlaybackState() {
		PodcastSubscription subscription = subscription("feed-key", "Road Show");
		PodcastStoredEpisode first = episode("guid-1", "First title");
		PodcastDatabase.upsert(database, subscription, List.of(first), 100);
		database.execSQL("UPDATE podcast_episode SET progress_ms=42000, played=1");

		PodcastStoredEpisode updated = episode("guid-1", "Updated title");
		PodcastDatabase.upsert(database, subscription, List.of(updated), 200);

		assertEquals(PodcastDatabase.SCHEMA_VERSION, DatabaseUtils.longForQuery(database,
				"SELECT value FROM podcast_meta WHERE key='schema_version'", null));
		assertEquals(1, DatabaseUtils.longForQuery(database,
				"SELECT COUNT(*) FROM podcast_subscription", null));
		assertEquals(42_000, DatabaseUtils.longForQuery(database,
				"SELECT progress_ms FROM podcast_episode", null));
		assertEquals(1, DatabaseUtils.longForQuery(database,
				"SELECT played FROM podcast_episode", null));
		assertEquals("Updated title", DatabaseUtils.stringForQuery(database,
				"SELECT title FROM podcast_episode", null));
		assertNotNull(PodcastDatabase.getSubscription(database, "feed-key"));
	}

	@Test
	public void deleteCascadesAndReturnsCredentialReferences() {
		PodcastSubscription subscription = new PodcastSubscription("private-feed",
				"https://example.test/feed?token=%3Credacted%3E", "feed:private-feed", "Private",
				"", "", "https://example.test/art?key=%3Credacted%3E", "artwork:private-feed",
				"", "", false, null, null, 1, 1, 1);
		PodcastStoredEpisode episode = new PodcastStoredEpisode(episodeModel("guid-private", "One"),
				"https://example.test/audio?key=%3Credacted%3E", "media:private-feed:one",
				"", null);
		PodcastDatabase.upsert(database, subscription, List.of(episode), 1);

		assertEquals(3, PodcastDatabase.credentialRefs(database, "private-feed").size());
		assertEquals(1, PodcastDatabase.deleteSubscription(database, "private-feed"));
		assertNull(PodcastDatabase.getSubscription(database, "private-feed"));
		assertEquals(0, DatabaseUtils.longForQuery(database,
				"SELECT COUNT(*) FROM podcast_episode", null));
	}

	@Test
	public void progressMirrorUpdatesOnlyAddressedEpisode() {
		PodcastSubscription subscription = subscription("feed-progress", "Progress");
		PodcastStoredEpisode first = episode("one", "One");
		PodcastStoredEpisode second = episode("two", "Two");
		PodcastDatabase.upsert(database, subscription, List.of(first, second), 1);

		PodcastDatabase.updateProgress(database, "feed-progress", first.episode.getKey(),
				75_000, true, 1234);

		assertEquals(75_000, DatabaseUtils.longForQuery(database,
				"SELECT progress_ms FROM podcast_episode WHERE episode_key=?",
				new String[]{first.episode.getKey()}));
		assertEquals(1, DatabaseUtils.longForQuery(database,
				"SELECT played FROM podcast_episode WHERE episode_key=?",
				new String[]{first.episode.getKey()}));
		assertEquals(0, DatabaseUtils.longForQuery(database,
				"SELECT progress_ms FROM podcast_episode WHERE episode_key=?",
				new String[]{second.episode.getKey()}));
	}

	@Test
	public void refreshFailurePersistsBackoffAndSuccessClearsIt() {
		PodcastSubscription subscription = subscription("feed-refresh", "Refresh");
		PodcastDatabase.upsert(database, subscription, List.of(), 1);
		PodcastDatabase.refreshFailed(database, "feed-refresh", 100, 5_000, "TIMEOUT");

		PodcastSubscription failed = PodcastDatabase.getSubscription(database, "feed-refresh");
		assertEquals(1, failed.getFailureCount());
		assertEquals(5_000, failed.getNextRefreshMs());
		assertEquals("TIMEOUT", failed.getLastErrorCode());

		PodcastDatabase.refreshNotModified(database, "feed-refresh", 200, null, null);
		PodcastSubscription recovered = PodcastDatabase.getSubscription(database, "feed-refresh");
		assertEquals(0, recovered.getFailureCount());
		assertEquals(0, recovered.getNextRefreshMs());
		assertNull(recovered.getLastErrorCode());
	}

	@Test
	public void retryPolicyUsesSteppedBackoffJitterCapAndRetryAfter() {
		assertTrue(inRange(PodcastRepository.retryDelayMs(1, 0, 0), 54_000, 66_000));
		assertTrue(inRange(PodcastRepository.retryDelayMs(2, 0, 1), 270_000, 330_000));
		assertTrue(inRange(PodcastRepository.retryDelayMs(4, 0, 2), 3_240_000, 3_960_000));
		assertEquals(6 * 60 * 60 * 1000L,
				PodcastRepository.retryDelayMs(20, Long.MAX_VALUE, 3));
		assertEquals(120_000, PodcastRepository.retryDelayMs(1, 120_000, 4));
	}

	@Test
	public void rejectsEpisodeKeyCollisionInsteadOfOverwriting() {
		PodcastSubscription subscription = subscription("feed-collision", "Collision");
		PodcastStoredEpisode episode = episode("same-key", "Original");
		PodcastDatabase.upsert(database, subscription, List.of(episode), 1);
		database.execSQL("UPDATE podcast_episode SET identity_hash='different-fingerprint'");

		assertThrows(SQLiteException.class, () -> PodcastDatabase.upsert(database,
				subscription, List.of(episode("same-key", "Replacement")), 2));
		assertEquals("Original", DatabaseUtils.stringForQuery(database,
				"SELECT title FROM podcast_episode", null));
	}

	@Test
	public void schemaIsPodcastOwnedAndUnsupportedVersionIsNotMutated() {
		try (android.database.Cursor cursor = database.rawQuery(
				"SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
						"AND name!='android_metadata'",
				null)) {
			while (cursor.moveToNext()) assertTrue(cursor.getString(0).startsWith("podcast_"));
		}

		SQLiteDatabase old = SQLiteDatabase.create(null);
		try {
			old.execSQL("CREATE TABLE podcast_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
			old.execSQL("INSERT INTO podcast_meta VALUES ('schema_version', '0')");
			old.execSQL("CREATE TABLE podcast_legacy (value TEXT)");
			old.execSQL("INSERT INTO podcast_legacy VALUES ('keep')");
			assertThrows(SQLiteException.class, () -> PodcastDatabase.initialize(old));
			assertEquals("0", DatabaseUtils.stringForQuery(old,
					"SELECT value FROM podcast_meta WHERE key='schema_version'", null));
			assertEquals("keep", DatabaseUtils.stringForQuery(old,
					"SELECT value FROM podcast_legacy", null));
		} finally {
			old.close();
		}
	}

	@Test
	public void continueAndNewQueriesUsePlaybackStateWithoutCrossFeedLeakage() {
		PodcastSubscription firstFeed = subscription("feed-one", "One");
		PodcastSubscription secondFeed = subscription("feed-two", "Two");
		PodcastStoredEpisode first = episode("first", "First");
		PodcastStoredEpisode second = episode("second", "Second");
		PodcastDatabase.upsert(database, firstFeed, List.of(first), 1);
		PodcastDatabase.upsert(database, secondFeed, List.of(second), 2);
		PodcastDatabase.updateProgress(database, "feed-one", first.episode.getKey(),
				50_000, false, 500);
		PodcastDatabase.updateProgress(database, "feed-two", second.episode.getKey(),
				90_000, true, 900);

		assertEquals(1, PodcastDatabase.listContinue(database, 50).size());
		assertEquals("feed-one", PodcastDatabase.listContinue(database, 50).get(0).getFeedKey());
		assertEquals(1, PodcastDatabase.listNewEpisodes(database, 50).size());
		assertEquals("feed-one", PodcastDatabase.listNewEpisodes(database, 50).get(0).getFeedKey());
	}

	@Test
	public void downloadValidatorsSurviveStateReloadAndDelete() {
		PodcastSubscription subscription = subscription("feed-download", "Download");
		PodcastStoredEpisode stored = episode("download-one", "Download One");
		PodcastDatabase.upsert(database, subscription, List.of(stored), 1);

		PodcastDatabase.updateDownload(database, "feed-download", stored.episode.getKey(),
				PodcastDownloadState.DOWNLOADING, null, "episode.partial", 25, 100,
				"etag-v1", "date-v1", null);
		PodcastDownloadInfo info = PodcastDatabase.getDownloadInfo(database, "feed-download",
				stored.episode.getKey());

		assertEquals("etag-v1", info.etag());
		assertEquals("date-v1", info.lastModified());
		PodcastDatabase.deleteDownload(database, "feed-download", stored.episode.getKey());
		assertEquals(PodcastDownloadInfo.EMPTY, PodcastDatabase.getDownloadInfo(database,
				"feed-download", stored.episode.getKey()));
	}

	private static PodcastSubscription subscription(String key, String title) {
		return new PodcastSubscription(key, "https://example.test/feed.xml", null, title,
				"Host", "Description", "https://example.test/art.jpg", null,
				"https://example.test/show", "en", false, "tag", "date", 10, 10, 10);
	}

	private static boolean inRange(long value, long min, long max) {
		return (value >= min) && (value <= max);
	}

	private static PodcastStoredEpisode episode(String guid, String title) {
		PodcastEpisode episode = episodeModel(guid, title);
		return new PodcastStoredEpisode(episode, episode.getMediaUrl(), null, "", null);
	}

	private static PodcastEpisode episodeModel(String guid, String title) {
		PodcastEpisode.Builder builder = new PodcastEpisode.Builder();
		builder.guid = guid;
		builder.title = title;
		builder.mediaUrl = "https://example.test/" + guid + ".mp3";
		PodcastEpisode result = PodcastEpisode.build(builder);
		assertNotNull(result);
		return result;
	}
}
