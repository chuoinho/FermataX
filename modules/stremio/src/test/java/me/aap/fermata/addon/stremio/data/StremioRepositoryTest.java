package me.aap.fermata.addon.stremio.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public class StremioRepositoryTest {
	private File directory;
	private File databaseFile;
	private StremioRepository repository;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("stremio-database-test").toFile();
		databaseFile = new File(directory, "stremio.db");
		repository = new StremioRepository(databaseFile);
		repository.ready().get(5, TimeUnit.SECONDS);
	}

	@After
	public void tearDown() throws Exception {
		if (repository != null) repository.closeAsync().get(5, TimeUnit.SECONDS);
		delete(directory);
	}

	@Test
	public void schemaAndForeignKeysAreEnabled() throws Exception {
		assertEquals(Integer.valueOf(4), repository.schemaVersion().get(5, TimeUnit.SECONDS));
		assertTrue(repository.foreignKeysEnabled().get(5, TimeUnit.SECONDS));

		StremioCacheRecord orphan = cache("orphan", "missing-source");
		Throwable failure = failureOf(repository.putCache(orphan));
		assertTrue(rootCause(failure) instanceof SQLiteConstraintException);
	}

	@Test
	public void sessionPointerSurvivesReopenAndFollowsVideoLifetime() throws Exception {
		StremioMetaIdentity identity = StremioMetaIdentity.create(
				"source-a", "movie", "movie-a", null);
		StremioVideoRecord video = video(identity.metaKey(), "video-a");
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		repository.putMeta(meta(identity, "Movie A")).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(identity.metaKey(),
				"source-a", "movie-a", 0, 10)).get(5, TimeUnit.SECONDS);
		repository.putVideo(video).get(5, TimeUnit.SECONDS);
		repository.putSessionState(new StremioSessionRecord(video.videoKey(),
				"stremio:season:movie-a", 12, 34)).get(5, TimeUnit.SECONDS);

		repository.closeAsync().get(5, TimeUnit.SECONDS);
		repository = new StremioRepository(databaseFile);
		repository.ready().get(5, TimeUnit.SECONDS);
		StremioSessionRecord restored = repository.getSessionState().get(5, TimeUnit.SECONDS);
		assertEquals(video.videoKey(), restored.videoKey());
		assertEquals(12, restored.playbackGeneration());

		repository.deleteSource("source-a").get(5, TimeUnit.SECONDS);
		assertNull(repository.getSessionState().get(5, TimeUnit.SECONDS));
	}

	@Test
	public void sourceTransportEditPreservesUuidAndProgress() throws Exception {
		StremioSourceRecord source = source("source-a", "fingerprint-old", "secret:old");
		repository.putSource(source).get(5, TimeUnit.SECONDS);
		StremioMetaIdentity identity = StremioMetaIdentity.create(source.sourceUuid(), "movie",
				"movie:1", null);
		StremioMetaRecord meta = meta(identity, "The Example");
		StremioVideoRecord video = video(meta.metaKey(), "video:1");
		repository.putMeta(meta).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(meta.metaKey(),
				source.sourceUuid(), "movie:1", 0, 10)).get(5, TimeUnit.SECONDS);
		repository.putVideo(video).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(video.videoKey(), 42_000, 100_000,
				false, 100, 100)).get(5, TimeUnit.SECONDS);

		StremioSourceRecord edited = source.withTransport("fingerprint-new",
				"https://provider.example.invalid/manifest.json", "secret:new", 200);
		repository.putSource(edited).get(5, TimeUnit.SECONDS);

		StremioSourceRecord stored = repository.getSource("source-a").get(5, TimeUnit.SECONDS);
		assertNotNull(stored);
		assertEquals("source-a", stored.sourceUuid());
		assertEquals("fingerprint-new", stored.transportFingerprint());
		assertEquals("secret:new", stored.secretRef());
		assertEquals(42_000, repository.getProgress(video.videoKey())
				.get(5, TimeUnit.SECONDS).positionMs());
	}

	@Test
	public void continueReadIsNewestFirstFiniteIncompleteAndHardBounded() throws Exception {
		StremioSourceRecord source = source("source-continue", "fingerprint-continue", null);
		repository.putSource(source).get(5, TimeUnit.SECONDS);
		StremioMetaIdentity identity = StremioMetaIdentity.create(
				source.sourceUuid(), "movie", "continue-meta", null);
		repository.putMeta(meta(identity, "Continue movies")).get(5, TimeUnit.SECONDS);

		List<String> validKeys = new ArrayList<>();
		for (int i = 0; i < StremioRepository.MAX_CONTINUE_ROWS + 5; i++) {
			StremioVideoRecord video = video(identity.metaKey(), "continue-" + i);
			repository.putVideo(video).get(5, TimeUnit.SECONDS);
			repository.putProgress(new StremioProgressRecord(video.videoKey(), 10L, 100L,
					false, i, i)).get(5, TimeUnit.SECONDS);
			validKeys.add(video.videoKey());
		}

		putProgressFixture(identity.metaKey(), "zero-position", 0L, 100L, false, 10_000L);
		putProgressFixture(identity.metaKey(), "unknown-duration", 10L, -1L, false, 10_001L);
		putProgressFixture(identity.metaKey(), "zero-duration", 10L, 0L, false, 10_002L);
		putProgressFixture(identity.metaKey(), "at-end", 100L, 100L, false, 10_003L);
		putProgressFixture(identity.metaKey(), "past-end", 101L, 100L, false, 10_004L);
		putProgressFixture(identity.metaKey(), "completed", 0L, 100L, true, 10_005L);
		putProgressFixture(identity.metaKey(), "negative-last-played", 10L, 100L, false, -1L);

		assertTrue(repository.listContinue(0).get(5, TimeUnit.SECONDS).isEmpty());
		assertTrue(repository.listContinue(-1).get(5, TimeUnit.SECONDS).isEmpty());
		List<StremioProgressRecord> newest = repository.listContinue(3)
				.get(5, TimeUnit.SECONDS);
		int newestIndex = validKeys.size() - 1;
		assertEquals(List.of(validKeys.get(newestIndex), validKeys.get(newestIndex - 1),
				validKeys.get(newestIndex - 2)),
				newest.stream().map(StremioProgressRecord::videoKey).toList());

		List<StremioProgressRecord> bounded = repository.listContinue(Integer.MAX_VALUE)
				.get(5, TimeUnit.SECONDS);
		assertEquals(StremioRepository.MAX_CONTINUE_ROWS, bounded.size());
		assertEquals(validKeys.get(newestIndex), bounded.get(0).videoKey());
		assertEquals(validKeys.get(5), bounded.get(bounded.size() - 1).videoKey());
		assertTrue(bounded.stream().allMatch(row -> !row.completed() &&
				(row.positionMs() > 0L) && (row.positionMs() < row.durationMs())));
	}

	@Test
	public void providerConsentSurvivesRepositoryRestart() throws Exception {
		StremioSourceRecord source = new StremioSourceRecord("source-consent", "fingerprint-consent",
				"addon.example", "Example", "1.0",
				"http://192.168.1.20/manifest.json", "secret:consent", true, 0,
				"{\"id\":\"addon.example\"}", null, null, 0, 0, null, 1, 1,
				true, true);
		repository.putSource(source).get(5, TimeUnit.SECONDS);
		repository.closeAsync().get(5, TimeUnit.SECONDS);
		repository = new StremioRepository(databaseFile);
		repository.ready().get(5, TimeUnit.SECONDS);

		StremioSourceRecord restored = repository.getSource("source-consent")
				.get(5, TimeUnit.SECONDS);
		assertTrue(restored.allowCleartext());
		assertTrue(restored.allowLan());
		assertEquals(source.networkConsent(), restored.networkConsent());
	}

	@Test
	public void providerScopedAndCanonicalMetaRowsRemainUnambiguous() throws Exception {
		StremioSourceRecord firstSource = source("source-a", "fingerprint-a", null);
		StremioSourceRecord secondSource = source("source-b", "fingerprint-b", null);
		repository.putSource(firstSource).get(5, TimeUnit.SECONDS);
		repository.putSource(secondSource).get(5, TimeUnit.SECONDS);

		StremioMetaIdentity customA = StremioMetaIdentity.create("source-a", "movie",
				"movie:1", null);
		StremioMetaIdentity customB = StremioMetaIdentity.create("source-b", "movie",
				"movie:1", null);
		repository.putMeta(meta(customA, "Provider A title")).get(5, TimeUnit.SECONDS);
		repository.putMeta(meta(customB, "Provider B title")).get(5, TimeUnit.SECONDS);
		assertEquals("Provider A title", repository.getMeta(customA.metaKey())
				.get(5, TimeUnit.SECONDS).name());
		assertEquals("Provider B title", repository.getMeta(customB.metaKey())
				.get(5, TimeUnit.SECONDS).name());

		StremioMetaIdentity canonicalA = StremioMetaIdentity.create("source-a", "movie",
				"provider:a", "imdb:tt0133093");
		StremioMetaIdentity canonicalB = StremioMetaIdentity.create("source-b", "movie",
				"provider:b", "TT0133093");
		assertEquals(canonicalA.metaKey(), canonicalB.metaKey());
		repository.putMeta(meta(canonicalA, "The Matrix")).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(canonicalA.metaKey(), "source-a",
				"provider:a", 0, 10)).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(canonicalB.metaKey(), "source-b",
				"provider:b", 1, 11)).get(5, TimeUnit.SECONDS);
		assertEquals(2, repository.executeForTest(database -> count(database,
				"stremio_meta_provider", "meta_key=?", canonicalA.metaKey()))
				.get(5, TimeUnit.SECONDS).intValue());
	}

	@Test
	public void canonicalMetadataChangesOnlyWithPreferredEnabledOwner() throws Exception {
		StremioSourceRecord first = source("source-a", "fingerprint-a", null, 0);
		StremioSourceRecord second = source("source-b", "fingerprint-b", null, 1);
		repository.putSource(first).get(5, TimeUnit.SECONDS);
		repository.putSource(second).get(5, TimeUnit.SECONDS);
		StremioMetaIdentity firstIdentity = StremioMetaIdentity.create(
				"source-a", "movie", "provider:a", "imdb:tt0133093");
		StremioMetaIdentity secondIdentity = StremioMetaIdentity.create(
				"source-b", "movie", "provider:b", "imdb:tt0133093");
		StremioMetaProviderRecord firstOwner = new StremioMetaProviderRecord(
				firstIdentity.metaKey(), "source-a", "provider:a", 0, 10);
		StremioMetaProviderRecord secondOwner = new StremioMetaProviderRecord(
				secondIdentity.metaKey(), "source-b", "provider:b", 1, 20);

		repository.putOwnedMeta(meta(firstIdentity, "Provider A"), firstOwner)
				.get(5, TimeUnit.SECONDS);
		repository.putOwnedMeta(meta(secondIdentity, "Provider B"), secondOwner)
				.get(5, TimeUnit.SECONDS);
		assertEquals("Provider A", repository.getMeta(firstIdentity.metaKey())
				.get(5, TimeUnit.SECONDS).name());

		StremioSourceRecord disabledFirst = new StremioSourceRecord(first.sourceUuid(),
				first.transportFingerprint(), first.addonId(), first.name(), first.version(),
				first.redactedTransportUrl(), first.secretRef(), false, first.position(),
				first.manifestJson(), first.manifestEtag(), first.manifestLastModified(),
				first.lastCheckedMs(), first.lastSuccessMs(), first.lastErrorCode(),
				first.installedMs(), 30, first.allowCleartext(), first.allowLan());
		repository.putSource(disabledFirst).get(5, TimeUnit.SECONDS);
		repository.putOwnedMeta(meta(secondIdentity, "Provider B"), secondOwner)
				.get(5, TimeUnit.SECONDS);
		assertEquals("Provider B", repository.getMeta(firstIdentity.metaKey())
				.get(5, TimeUnit.SECONDS).name());
	}

	@Test
	public void deletingSourceCascadesItsCache() throws Exception {
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		StremioCacheRecord cache = cache("catalog-key", "source-a");
		repository.putCache(cache).get(5, TimeUnit.SECONDS);
		assertArrayEquals(cache.payload(), repository.getCache(cache.cacheKey())
				.get(5, TimeUnit.SECONDS).payload());

		assertTrue(repository.deleteSource("source-a").get(5, TimeUnit.SECONDS));
		assertNull(repository.getCache(cache.cacheKey()).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void durableCacheIsBoundedAndRejectsOversizedRows() throws Exception {
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		for (int i = 0; i < StremioRepository.MAX_CACHE_ROWS + 3; i++) {
			long stored = i + 1L;
			repository.putCache(new StremioCacheRecord("bounded-" + i, "source-a", "item",
					("payload-" + i).getBytes(StandardCharsets.UTF_8), null, null,
					stored, Long.MAX_VALUE, Long.MAX_VALUE)).get(5, TimeUnit.SECONDS);
		}

		assertEquals(StremioRepository.MAX_CACHE_ROWS, repository.executeForTest(database ->
				countAll(database, "stremio_response_cache"))
				.get(5, TimeUnit.SECONDS).intValue());
		assertNull(repository.getCache("bounded-0").get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getCache("bounded-" + (StremioRepository.MAX_CACHE_ROWS + 2))
				.get(5, TimeUnit.SECONDS));

		ExecutionException oversized = org.junit.Assert.assertThrows(ExecutionException.class,
				() -> repository.putCache(new StremioCacheRecord("oversized", "source-a", "item",
						new byte[StremioRepository.MAX_CACHE_ENTRY_BYTES + 1], null, null,
						1, Long.MAX_VALUE, Long.MAX_VALUE)).get(5, TimeUnit.SECONDS));
		assertTrue(oversized.getCause() instanceof IllegalArgumentException);
	}

	@Test
	public void sourceSnapshotCommitIsAtomicWhenARowWriteFails() throws Exception {
		StremioRepository.SourceState empty = repository.getSourceState()
				.get(5, TimeUnit.SECONDS);
		StremioRepository.SourceState attempted = new StremioRepository.SourceState(1,
				List.of(source("source-a", "fingerprint-a", null, 0),
						source("source-b", "fingerprint-b", null, 1)), true);
		repository.executeForTest(database -> {
			database.execSQL("CREATE TRIGGER fail_second_source BEFORE INSERT ON stremio_addon " +
					"WHEN NEW.source_uuid='source-b' BEGIN " +
					"SELECT RAISE(ABORT,'injected source failure'); END");
			return null;
		}).get(5, TimeUnit.SECONDS);

		Throwable failure = failureOf(repository.compareAndSetSourceState(empty, attempted));

		assertTrue(rootCause(failure) instanceof android.database.SQLException);
		assertEquals(empty, repository.getSourceState().get(5, TimeUnit.SECONDS));
		assertNull(repository.getSource("source-a").get(5, TimeUnit.SECONDS));
		assertNull(repository.getSource("source-b").get(5, TimeUnit.SECONDS));
	}

	@Test
	public void sourceRemovalRetainsSharedProgressAndDeletesOrphanProgress() throws Exception {
		StremioSourceRecord sourceA = source("source-a", "fingerprint-a", null, 0);
		StremioSourceRecord sourceB = source("source-b", "fingerprint-b", null, 1);
		StremioRepository.SourceState empty = repository.getSourceState()
				.get(5, TimeUnit.SECONDS);
		StremioRepository.SourceState both = new StremioRepository.SourceState(1,
				List.of(sourceA, sourceB), false);
		assertTrue(repository.compareAndSetSourceState(empty, both).get(5, TimeUnit.SECONDS));

		StremioMetaIdentity privateIdentity = StremioMetaIdentity.create("source-a", "movie",
				"private:1", null);
		StremioMetaRecord privateMeta = meta(privateIdentity, "Private");
		StremioVideoRecord privateVideo = video(privateMeta.metaKey(), "private-video");
		repository.putMeta(privateMeta).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(privateMeta.metaKey(),
				"source-a", "private:1", 0, 10)).get(5, TimeUnit.SECONDS);
		repository.putVideo(privateVideo).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(privateVideo.videoKey(),
				10, 100, false, 10, 10)).get(5, TimeUnit.SECONDS);

		StremioMetaIdentity sharedIdentity = StremioMetaIdentity.create("source-a", "movie",
				"provider:a", "imdb:tt0133093");
		StremioMetaRecord sharedMeta = meta(sharedIdentity, "Shared");
		StremioVideoRecord sharedVideo = video(sharedMeta.metaKey(), "shared-video");
		repository.putMeta(sharedMeta).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(sharedMeta.metaKey(),
				"source-a", "provider:a", 0, 10)).get(5, TimeUnit.SECONDS);
		repository.putMetaProvider(new StremioMetaProviderRecord(sharedMeta.metaKey(),
				"source-b", "provider:b", 1, 10)).get(5, TimeUnit.SECONDS);
		repository.putVideo(sharedVideo).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(sharedVideo.videoKey(),
				20, 100, false, 20, 20)).get(5, TimeUnit.SECONDS);

		StremioSourceRecord sourceBAtZero = source("source-b", "fingerprint-b", null, 0);
		StremioRepository.SourceState onlyB = new StremioRepository.SourceState(2,
				List.of(sourceBAtZero), false);
		assertTrue(repository.compareAndSetSourceState(both, onlyB).get(5, TimeUnit.SECONDS));
		assertNull(repository.getProgress(privateVideo.videoKey()).get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getProgress(sharedVideo.videoKey()).get(5, TimeUnit.SECONDS));

		StremioRepository.SourceState none = new StremioRepository.SourceState(3,
				List.of(), false);
		assertTrue(repository.compareAndSetSourceState(onlyB, none).get(5, TimeUnit.SECONDS));
		assertNull(repository.getProgress(sharedVideo.videoKey()).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void persistenceBoundaryRejectsTaintedManifestAndCache() throws Exception {
		StremioSourceRecord safe = source("source-a", "fingerprint-a", null);
		StremioSourceRecord tainted = new StremioSourceRecord(safe.sourceUuid(),
				safe.transportFingerprint(), safe.addonId(), safe.name(), safe.version(),
				safe.redactedTransportUrl(), safe.secretRef(), safe.enabled(), safe.position(),
				"{\"id\":\"addon.example\",\"access_token\":\"must-not-persist\"}",
				null, null, 0, 0, null, 1, 1);

		assertTrue(rootCause(failureOf(repository.putSource(tainted)))
				instanceof SecurityException);
		assertNull(repository.getSource("source-a").get(5, TimeUnit.SECONDS));

		repository.putSource(safe).get(5, TimeUnit.SECONDS);
		StremioCacheRecord taintedCache = new StremioCacheRecord("tainted", "source-a",
				"catalog", "{\"authorization\":\"Bearer secret\"}"
						.getBytes(StandardCharsets.UTF_8), null, null, 10, 20, 30);
		assertTrue(rootCause(failureOf(repository.putCache(taintedCache)))
				instanceof SecurityException);
		assertNull(repository.getCache("tainted").get(5, TimeUnit.SECONDS));
	}

	@Test
	public void persistenceBoundaryRejectsTaintedMetaAndVideoAtomically() throws Exception {
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		StremioMetaIdentity identity = StremioMetaIdentity.create(
				"source-a", "movie", "movie:taint", null);
		StremioMetaRecord taintedMeta = new StremioMetaRecord(identity.metaKey(),
				identity.identityScope(), "movie", identity.durableMetaId(), null, "Movie",
				"authorization=Bearer durable-secret", null, null, null, null,
				100_000L, "[]", 10L);

		assertTrue(rootCause(failureOf(repository.putMeta(taintedMeta)))
				instanceof SecurityException);
		assertNull(repository.getMeta(identity.metaKey()).get(5, TimeUnit.SECONDS));

		StremioMetaRecord safeMeta = meta(identity, "Movie");
		repository.putMeta(safeMeta).get(5, TimeUnit.SECONDS);
		StremioVideoRecord taintedVideo = new StremioVideoRecord(
				StremioMetaIdentity.videoKey(identity.metaKey(), "video:taint"),
				identity.metaKey(), "movie", "video:taint", "token=durable-secret",
				null, null, 0L, 100_000L, null, 11L);

		assertTrue(rootCause(failureOf(repository.putVideo(taintedVideo)))
				instanceof SecurityException);
		assertNull(repository.getVideo(taintedVideo.videoKey()).get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getMeta(identity.metaKey()).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void durableRetentionPrunesOrphansAndPreservesOwnedRows() throws Exception {
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		StremioVideoRecord[] videos = new StremioVideoRecord[6];
		for (int i = 0; i < videos.length; i++) {
			StremioMetaIdentity identity = StremioMetaIdentity.create(
					"source-a", "movie", "movie:" + i, null);
			repository.putMeta(new StremioMetaRecord(identity.metaKey(),
					identity.identityScope(), "movie", identity.durableMetaId(), null,
					"Movie " + i, "", null, null, null, null,
					100_000L, "[]", i + 1L)).get(5, TimeUnit.SECONDS);
			videos[i] = new StremioVideoRecord(
					StremioMetaIdentity.videoKey(identity.metaKey(), "video:" + i),
					identity.metaKey(), "movie", "video:" + i, "Movie " + i,
					null, null, 0L, 100_000L, null, i + 1L);
			repository.putVideo(videos[i]).get(5, TimeUnit.SECONDS);
		}
		repository.putProgress(new StremioProgressRecord(videos[0].videoKey(),
				0L, 100_000L, true, 1L, 1L)).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(videos[1].videoKey(),
				40_000L, 100_000L, false, 100L, 100L)).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(videos[2].videoKey(),
				0L, 100_000L, true, 200L, 200L)).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(videos[4].videoKey(),
				0L, 100_000L, true, 50L, 50L)).get(5, TimeUnit.SECONDS);
		repository.putSessionState(new StremioSessionRecord(videos[0].videoKey(),
				"stremio:root", 1L, 1L)).get(5, TimeUnit.SECONDS);
		repository.setFavoriteRetention(videos[3].videoKey(), true, 300L)
				.get(5, TimeUnit.SECONDS);

		repository.pruneForTest(4, 4, 3).get(5, TimeUnit.SECONDS);

		assertEquals(4, repository.executeForTest(database ->
				countAll(database, "stremio_meta")).get(5, TimeUnit.SECONDS).intValue());
		assertEquals(4, repository.executeForTest(database ->
				countAll(database, "stremio_video")).get(5, TimeUnit.SECONDS).intValue());
		assertEquals(3, repository.executeForTest(database ->
				countAll(database, "stremio_progress")).get(5, TimeUnit.SECONDS).intValue());
		assertNotNull(repository.getVideo(videos[0].videoKey()).get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getVideo(videos[1].videoKey()).get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getVideo(videos[2].videoKey()).get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getVideo(videos[3].videoKey()).get(5, TimeUnit.SECONDS));
		assertNull(repository.getVideo(videos[4].videoKey()).get(5, TimeUnit.SECONDS));
		assertNull(repository.getVideo(videos[5].videoKey()).get(5, TimeUnit.SECONDS));

		repository.setFavoriteRetention(videos[3].videoKey(), false, 400L)
				.get(5, TimeUnit.SECONDS);
		repository.pruneForTest(3, 3, 3).get(5, TimeUnit.SECONDS);
		assertNull(repository.getVideo(videos[3].videoKey()).get(5, TimeUnit.SECONDS));
		assertEquals(3, repository.executeForTest(database ->
				countAll(database, "stremio_video")).get(5, TimeUnit.SECONDS).intValue());
	}

	@Test
	public void signedAndSecretBearingArtworkNeverReachesSqlite() throws Exception {
		StremioMetaIdentity identity = StremioMetaIdentity.create(
				"source-a", "movie", "movie:artwork", null);
		StremioMetaRecord meta = new StremioMetaRecord(identity.metaKey(),
				identity.identityScope(), "movie", identity.durableMetaId(), null, "Artwork", "",
				"https://images.invalid/poster.jpg?signature=private",
				"https://images.invalid/background.jpg",
				"https://images.invalid/token/private/logo.png", null, 100_000, "[]", 10);
		StremioVideoRecord video = new StremioVideoRecord(
				StremioMetaIdentity.videoKey(meta.metaKey(), "video:artwork"), meta.metaKey(),
				"movie", "video:artwork", "Artwork", null, null, 0, 100_000,
				"https://images.invalid/thumb.jpg?X-Amz-Signature=private", 10);

		repository.putMeta(meta).get(5, TimeUnit.SECONDS);
		repository.putVideo(video).get(5, TimeUnit.SECONDS);
		StremioMetaRecord storedMeta = repository.getMeta(meta.metaKey()).get(5, TimeUnit.SECONDS);
		StremioVideoRecord storedVideo = repository.getVideo(video.videoKey())
				.get(5, TimeUnit.SECONDS);

		assertNull(storedMeta.posterUrl());
		assertEquals("https://images.invalid/background.jpg", storedMeta.backgroundUrl());
		assertNull(storedMeta.logoUrl());
		assertNull(storedVideo.thumbnailUrl());
	}

	@Test
	public void durableKeyCollisionsAreRejectedInsteadOfOverwritten() throws Exception {
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		repository.putSource(source("source-b", "fingerprint-b", null))
				.get(5, TimeUnit.SECONDS);
		StremioMetaIdentity identity = StremioMetaIdentity.create("source-a", "movie",
				"movie:1", null);
		StremioMetaRecord valid = meta(identity, "Original");
		repository.putMeta(valid).get(5, TimeUnit.SECONDS);
		StremioMetaRecord collision = new StremioMetaRecord(valid.metaKey(), "source-b", "movie",
				"movie:1", null, "Collision", "", null, null, null, null, -1, "[]", 20);

		assertTrue(rootCause(failureOf(repository.putMeta(collision)))
				instanceof IllegalStateException);
		assertEquals("Original", repository.getMeta(valid.metaKey())
				.get(5, TimeUnit.SECONDS).name());

		repository.putCache(cache("shared-key", "source-a")).get(5, TimeUnit.SECONDS);
		assertTrue(rootCause(failureOf(repository.putCache(cache("shared-key", "source-b"))))
				instanceof IllegalStateException);
	}

	@Test
	public void versionFourMigrationRemovesLegacyTaintedMetadata() throws Exception {
		repository.closeAsync().get(5, TimeUnit.SECONDS);
		File legacyDatabase = new File(directory, "stremio-v3.db");
		repository = new StremioRepository(legacyDatabase, 3, StremioSchema.migrations());
		repository.ready().get(5, TimeUnit.SECONDS);
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		repository.executeForTest(database -> {
			database.execSQL("INSERT INTO stremio_meta(meta_key,identity_scope,type," +
					"provider_meta_id,name,description,runtime_ms,genres_json,updated_ms) " +
					"VALUES(?,?,?,?,?,?,?,?,?)", new Object[]{"legacy-meta", "source-a", "movie",
					"movie:legacy", "Legacy", "token=legacy-secret", 1L, "[]", 1L});
			return null;
		}).get(5, TimeUnit.SECONDS);
		repository.closeAsync().get(5, TimeUnit.SECONDS);

		repository = new StremioRepository(legacyDatabase);
		repository.ready().get(5, TimeUnit.SECONDS);

		assertEquals(Integer.valueOf(4), repository.schemaVersion().get(5, TimeUnit.SECONDS));
		assertNull(repository.getMeta("legacy-meta").get(5, TimeUnit.SECONDS));
		assertTrue(repository.executeForTest(database -> tableExists(database,
				"stremio_retention_pin")).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void failedMigrationRollsBackAndPreservesCurrentVersion() throws Exception {
		repository.putSource(source("source-a", "fingerprint-a", null))
				.get(5, TimeUnit.SECONDS);
		repository.closeAsync().get(5, TimeUnit.SECONDS);
		repository = null;

		List<StremioSchema.Migration> migrations = new ArrayList<>(StremioSchema.migrations());
		migrations.add(new StremioSchema.Migration() {
			@Override
			public int version() {
				return 5;
			}

			@Override
			public void apply(SQLiteDatabase database) {
				database.execSQL("CREATE TABLE migration_must_rollback(value TEXT)");
				throw new IllegalStateException("deliberate migration failure");
			}
		});
		StremioRepository failed = new StremioRepository(databaseFile, 5, migrations);
		Throwable failure = failureOf(failed.ready());
		assertTrue(rootCause(failure) instanceof IllegalStateException);
		failed.closeAsync().get(5, TimeUnit.SECONDS);

		repository = new StremioRepository(databaseFile);
		repository.ready().get(5, TimeUnit.SECONDS);
		assertEquals(Integer.valueOf(4), repository.schemaVersion().get(5, TimeUnit.SECONDS));
		assertNotNull(repository.getSource("source-a").get(5, TimeUnit.SECONDS));
		assertFalse(repository.executeForTest(database -> tableExists(database,
				"migration_must_rollback")).get(5, TimeUnit.SECONDS));
	}

	@Test
	public void closeDrainsAcceptedWorkAndRejectsNewWork() throws Exception {
		CountDownLatch operationStarted = new CountDownLatch(1);
		CountDownLatch releaseOperation = new CountDownLatch(1);
		CompletableFuture<String> running = repository.executeForTest(database -> {
			operationStarted.countDown();
			if (!releaseOperation.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("test operation timed out");
			}
			return Thread.currentThread().getName();
		});
		assertTrue(operationStarted.await(5, TimeUnit.SECONDS));
		CompletableFuture<Void> accepted = repository.putSource(
				source("source-a", "fingerprint-a", null));
		CompletableFuture<Void> closing = repository.closeAsync();
		Throwable rejected = failureOf(repository.getSource("source-a"));
		assertTrue(rootCause(rejected) instanceof RejectedExecutionException);

		releaseOperation.countDown();
		assertTrue(running.get(5, TimeUnit.SECONDS).startsWith("FermataX-Stremio-DB"));
		accepted.get(5, TimeUnit.SECONDS);
		closing.get(5, TimeUnit.SECONDS);
		repository = null;

		repository = new StremioRepository(databaseFile);
		repository.ready().get(5, TimeUnit.SECONDS);
		assertNotNull(repository.getSource("source-a").get(5, TimeUnit.SECONDS));
	}

	@Test
	public void schemaMigrationRunsOnOwnedWorker() throws Exception {
		repository.closeAsync().get(5, TimeUnit.SECONDS);
		repository = null;
		AtomicReference<String> migrationThread = new AtomicReference<>();
		List<StremioSchema.Migration> migrations = new ArrayList<>(StremioSchema.migrations());
		migrations.add(new StremioSchema.Migration() {
			@Override
			public int version() {
				return 5;
			}

			@Override
			public void apply(SQLiteDatabase database) {
				migrationThread.set(Thread.currentThread().getName());
				database.execSQL("CREATE TABLE worker_marker(value TEXT)");
			}
		});
		repository = new StremioRepository(databaseFile, 5, migrations);
		repository.ready().get(5, TimeUnit.SECONDS);
		assertTrue(migrationThread.get().startsWith("FermataX-Stremio-DB"));
	}

	private static StremioSourceRecord source(String uuid, String fingerprint, String secretRef) {
		return source(uuid, fingerprint, secretRef, 0);
	}

	private static StremioSourceRecord source(
			String uuid, String fingerprint, String secretRef, int position) {
		return new StremioSourceRecord(uuid, fingerprint, "addon.example", "Example", "1.0",
				"https://provider.example.invalid/manifest.json", secretRef, true, position,
				"{\"id\":\"addon.example\"}", null, null, 0, 0, null, 1, 1);
	}

	private static StremioMetaRecord meta(StremioMetaIdentity identity, String name) {
		return new StremioMetaRecord(identity.metaKey(), identity.identityScope(), "movie",
				identity.durableMetaId(), identity.canonicalIdentity(), name, "", null, null,
				null, null, 120_000, "[]", 10);
	}

	private static StremioVideoRecord video(String metaKey, String id) {
		return new StremioVideoRecord(StremioMetaIdentity.videoKey(metaKey, id), metaKey,
				"movie", id, "Example video", null, null, 0, 100_000, null, 10);
	}

	private void putProgressFixture(String metaKey, String id, long position, long duration,
			boolean completed, long lastPlayed) throws Exception {
		StremioVideoRecord video = video(metaKey, id);
		repository.putVideo(video).get(5, TimeUnit.SECONDS);
		repository.putProgress(new StremioProgressRecord(video.videoKey(), position, duration,
				completed, lastPlayed, lastPlayed)).get(5, TimeUnit.SECONDS);
	}

	private static StremioCacheRecord cache(String key, String sourceUuid) {
		return new StremioCacheRecord(key, sourceUuid, "catalog",
				"payload".getBytes(StandardCharsets.UTF_8), null, null, 10, 20, 30);
	}

	private static int count(SQLiteDatabase database, String table, String where, String argument) {
		try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where,
				new String[]{argument})) {
			assertTrue(cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

	private static int countAll(SQLiteDatabase database, String table) {
		try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
			assertTrue(cursor.moveToFirst());
			return cursor.getInt(0);
		}
	}

	private static boolean tableExists(SQLiteDatabase database, String table) {
		try (Cursor cursor = database.rawQuery(
				"SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new String[]{table})) {
			return cursor.moveToFirst();
		}
	}

	private static Throwable failureOf(CompletableFuture<?> future) throws Exception {
		try {
			future.get(5, TimeUnit.SECONDS);
			fail("Expected future to fail");
			return new AssertionError("unreachable");
		} catch (ExecutionException expected) {
			return expected;
		}
	}

	private static Throwable rootCause(Throwable error) {
		Throwable cause = error;
		while ((cause.getCause() != null) && (cause.getCause() != cause)) cause = cause.getCause();
		return cause;
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		if (!file.delete()) file.deleteOnExit();
	}
}
