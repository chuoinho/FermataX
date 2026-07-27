package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.browse.SearchResults;
import me.aap.fermata.addon.stremio.data.StremioMetaRecord;
import me.aap.fermata.addon.stremio.data.StremioMetaIdentity;
import me.aap.fermata.addon.stremio.data.StremioMetaProviderRecord;
import me.aap.fermata.addon.stremio.data.StremioProgressRecord;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.data.StremioVideoRecord;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioCanonicalIdentity;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.protocol.model.ManifestBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.model.PrefixConstraint;
import me.aap.fermata.addon.stremio.protocol.model.ResourceCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;
import me.aap.fermata.addon.stremio.session.StremioAdjacentDirection;
import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioItemAvailability;
import me.aap.fermata.addon.stremio.session.StremioProviderState;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;
import me.aap.fermata.addon.stremio.session.StremioVoiceCandidate;
import me.aap.fermata.addon.stremio.session.StremioVoiceResult;
import me.aap.fermata.media.net.RemotePlaybackProgress;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
public class StremioSessionGatewayAdapterTest {
	private static final long TIMEOUT_SECONDS = 5L;
	private File directory;
	private StremioRepository repository;
	private FakeItemGateway itemGateway;
	private final List<BrowseProvider> providers = new ArrayList<>();
	private StremioSessionGatewayAdapter gateway;

	@Before
	public void setUp() throws Exception {
		directory = Files.createTempDirectory("stremio-session").toFile();
		repository = new StremioRepository(new File(directory, "stremio.db"));
		await(repository.ready());
		itemGateway = new FakeItemGateway();
		gateway = createGateway();
	}

	@After
	public void tearDown() throws Exception {
		if (gateway != null) gateway.close();
		if (repository != null) await(repository.closeAsync());
		delete(directory);
	}

	@Test
	public void playbackPreparationProgressIsDelegatedToTheRuntimeGateway() throws Exception {
		List<RemotePlaybackProgress> progress = new ArrayList<>();

		awaitFuture(gateway.preparePlayback(null, progress::add));

		assertTrue(itemGateway.preparationProgressDelegated);
		assertEquals(List.of(RemotePlaybackProgress.findingPeers()), progress);
	}

	@Test
	public void processRestoreUsesStableDatabaseProjection() throws Exception {
		putSource("source-a", true, 0);
		String videoId = putMovie("source-a", "movie-a", "Exact Movie");
		StremioSessionCoordinator first = new StremioSessionCoordinator(gateway);

		await(first.activatePlayback(videoId, 7L, 1_000L));
		gateway.close();
		await(repository.closeAsync());
		repository = new StremioRepository(new File(directory, "stremio.db"));
		await(repository.ready());
		gateway = createGateway();
		StremioSessionCoordinator recreated = new StremioSessionCoordinator(gateway);

		var restored = await(recreated.restoreAfterProcessDeath());
		assertTrue(restored.isAvailable());
		assertEquals(videoId, restored.item().stableId());
		assertEquals("Exact Movie", restored.item().title());
		assertEquals("Exact Movie", recreated.getCurrentTarget().orElseThrow().title());
		assertFalse(restored.item().backToListId().contains("http"));
	}

	@Test
	public void managedProgressWritesOnceAndUpdatesRestorePointer() throws Exception {
		putSource("source-a", true, 0);
		StremioPlaybackIdentity identity = StremioPlaybackIdentity.scoped(
				"source-a", "movie", "movie-a", "movie-a");
		putMovie("source-a", "movie-a", "Exact Movie");

		awaitFuture(gateway.saveProgress(identity, 12_345L, false));
		assertEquals(0, itemGateway.progressWrites);
		assertEquals(12_345L, await(repository.getProgress(identity.videoKey())).positionMs());
		gateway.close();
		gateway = createGateway();

		var restored = await(new StremioSessionCoordinator(gateway)
				.restoreAfterProcessDeath());
		assertTrue(restored.isAvailable());
		assertEquals(identity.videoKey(), restored.item().stableId());
	}

	@Test
	public void continueProjectionKeepsRepositoryOrderStableIdentityAndSafeLogging()
			throws Exception {
		putSource("source-a", true, 0);
		String older = putMovie("source-a", "movie-old", "Older Movie");
		String newer = putMovie("source-a", "movie-new", "Newer Movie");
		StremioVideoRecord newerVideo = await(repository.getVideo(newer));
		await(repository.putVideo(new StremioVideoRecord(newerVideo.videoKey(),
				newerVideo.metaKey(), newerVideo.type(), newerVideo.providerVideoId(),
				newerVideo.title(), newerVideo.seasonNumber(), newerVideo.episodeNumber(),
				newerVideo.releasedMs(), newerVideo.durationMs(),
				"https://cdn.example.invalid/poster.jpg", newerVideo.updatedMs())));
		await(repository.putProgress(new StremioProgressRecord(older, 10_000L, 100_000L,
				false, 10L, 10L)));
		await(repository.putProgress(new StremioProgressRecord(newer, 20_000L, 100_000L,
				false, 20L, 20L)));

		List<StremioContinueEntry> result = await(gateway.loadContinue(10));

		assertEquals(2, result.size());
		assertEquals(newer, result.get(0).item().stableId());
		assertEquals("Newer Movie", result.get(0).item().title());
		assertEquals(20_000L, result.get(0).positionMs());
		assertEquals(100_000L, result.get(0).durationMs());
		assertEquals("https://cdn.example.invalid/poster.jpg", result.get(0).item().artwork());
		assertEquals(older, result.get(1).item().stableId());
		String logged = result.toString();
		assertFalse(logged.contains("Newer Movie"));
		assertFalse(logged.contains("provider"));
		assertFalse(logged.contains("http"));
	}

	@Test
	public void processRestoreKeepsExactSourceAfterOrderAndEnableChanges() throws Exception {
		putSource("source-a", true, 0);
		putSource("source-b", true, 1);
		String videoId = putMovie("source-b", "movie-b", "Source B Movie");
		await(gateway.sessions().activatePlayback(videoId, 3L, 100L));

		putSource("source-a", true, 1);
		putSource("source-b", false, 0);
		gateway.close();
		await(repository.closeAsync());
		repository = new StremioRepository(new File(directory, "stremio.db"));
		await(repository.ready());
		gateway = createGateway();

		var restored = await(gateway.sessions().restoreAfterProcessDeath());
		assertEquals(StremioItemAvailability.PROVIDER_DISABLED, restored.availability());
		assertEquals("source-b", restored.item().sourceUuid());
		assertEquals("Source B Movie", restored.item().title());
	}

	@Test
	public void hundredPlaybackSwitchesUseOnlyManagedWriterAndNeverCrossItems() throws Exception {
		putSource("source-a", true, 0);
		StremioPlaybackIdentity[] identities = {
				StremioPlaybackIdentity.scoped("source-a", "movie", "movie-a", "movie-a"),
				StremioPlaybackIdentity.scoped("source-a", "movie", "movie-b", "movie-b"),
				StremioPlaybackIdentity.scoped("source-a", "movie", "movie-c", "movie-c")
		};
		putMovie("source-a", "movie-a", "Movie A");
		putMovie("source-a", "movie-b", "Movie B");
		putMovie("source-a", "movie-c", "Movie C");
		Map<String, Long> expected = new HashMap<>();
		for (int i = 0; i < 100; i++) {
			StremioPlaybackIdentity identity = identities[i % identities.length];
			long position = 1_000L + i;
			awaitFuture(gateway.saveProgress(identity, position, false, i + 1L));
			expected.put(identity.videoKey(), position);
		}

		assertEquals(0, itemGateway.progressWrites);
		for (var entry : expected.entrySet()) {
			assertEquals(entry.getValue().longValue(),
					await(repository.getProgress(entry.getKey())).positionMs());
		}
	}

	@Test
	public void staleCoreGenerationCannotReclaimOwnershipAfterNewItem() throws Exception {
		putSource("source-a", true, 0);
		StremioPlaybackIdentity a = StremioPlaybackIdentity.scoped(
				"source-a", "movie", "movie-a", "movie-a");
		StremioPlaybackIdentity b = StremioPlaybackIdentity.scoped(
				"source-a", "movie", "movie-b", "movie-b");
		putMovie("source-a", "movie-a", "Movie A");
		putMovie("source-a", "movie-b", "Movie B");

		awaitFuture(gateway.saveProgress(a, 10_000L, false, 11L));
		awaitFuture(gateway.saveProgress(b, 20_000L, false, 12L));
		awaitFuture(gateway.saveProgress(a, 99_000L, false, 11L));

		assertEquals(10_000L, await(repository.getProgress(a.videoKey())).positionMs());
		assertEquals(20_000L, await(repository.getProgress(b.videoKey())).positionMs());
		assertEquals(b.videoKey(), await(repository.getSessionState()).videoKey());
	}

	@Test
	public void episodeQueueUsesOneTargetedMetaLoadAndPersistsAdjacentIdentity() throws Exception {
		putSource("source-a", true, 0);
		StremioPlaybackIdentity firstIdentity = StremioPlaybackIdentity.scoped(
				"source-a", "series", "series-a", "episode-1");
		await(repository.putMeta(new StremioMetaRecord(firstIdentity.contentKey(), "source-a",
				"series", "series-a", null, "Series", "", null, null, null, "",
				-1L, "[]", 1L)));
		await(repository.putVideo(new StremioVideoRecord(firstIdentity.videoKey(),
				firstIdentity.contentKey(), "series", "episode-1", "Episode 1", 1, 1,
				0L, 2_000L, null, 1L)));
		BrowseMedia series = media("source-a", "series-a", "Series");
		series = new BrowseMedia(series.sourceUuid(), "series", series.id(), series.title(),
				null, null, "", "", null, List.of(), "en");
		itemGateway.metaResult = new BrowseDetails(series, List.of(new BrowseSeason(1, List.of(
				new BrowseEpisode("source-a", "series", "series-a", "episode-1",
						"Episode 1", 1, 1, null, null, "",
						new StremioDuration("2s", 2_000L)),
				new BrowseEpisode("source-a", "series", "series-a", "episode-2",
						"Episode 2", 1, 2, null, null, "",
						new StremioDuration("2s", 2_000L))))));

		var adjacent = await(new StremioSessionCoordinator(gateway).adjacentEpisode(
				firstIdentity.videoKey(), StremioAdjacentDirection.NEXT));
		assertTrue(adjacent.isAvailable());
		assertEquals("Episode 2", adjacent.item().title());
		assertEquals(1, itemGateway.metaLoads);
		assertNotNull(await(repository.getVideo(adjacent.item().stableId())));
	}

	@Test
	public void disabledAndRemovedProvidersNeverResolveAsAvailable() throws Exception {
		putSource("source-a", true, 0);
		String videoId = putMovie("source-a", "movie-a", "Movie");
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		assertTrue(await(coordinator.resolveStableItem(videoId)).isAvailable());

		putSource("source-a", false, 0);
		assertEquals(StremioItemAvailability.PROVIDER_DISABLED,
				await(coordinator.resolveStableItem(videoId)).availability());
		await(repository.deleteSource("source-a"));
		assertEquals(StremioProviderState.REMOVED,
				await(gateway.getProviderState("source-a")));
		assertEquals(StremioItemAvailability.ITEM_MISSING,
				await(coordinator.resolveStableItem(videoId)).availability());
	}

	@Test
	public void canonicalFailoverUsesOnlyRecordedOwnerAndItsProviderSpecificId()
			throws Exception {
		putSource("source-a", false, 0);
		putSource("source-b", true, 1);
		putSource("source-unrelated", true, 0);
		providers.add(provider("source-unrelated", 0));
		providers.add(provider("source-b", 1));
		String stableId = putCanonicalMovie();

		StremioSessionItem restored = await(gateway.loadItem(stableId));

		assertNotNull(restored);
		assertEquals("source-b", restored.sourceUuid());
		assertEquals(StremioItemAvailability.AVAILABLE,
				await(gateway.sessions().resolveStableItem(stableId)).availability());
	}

	@Test
	public void canonicalItemWithoutEnabledOwnerIsRetainedAsProviderDisabled()
			throws Exception {
		putSource("source-a", false, 0);
		putSource("source-b", false, 1);
		String stableId = putCanonicalMovie();

		var resolution = await(gateway.sessions().resolveStableItem(stableId));

		assertEquals(StremioItemAvailability.PROVIDER_DISABLED, resolution.availability());
		assertNotNull(resolution.item());
		assertEquals("source-a", resolution.item().sourceUuid());
	}

	@Test
	public void canonicalCacheChangesOwnerOnlyAfterPreviousOwnerIsDisabled()
			throws Exception {
		putSource("source-a", true, 0);
		putSource("source-b", true, 1);
		providers.add(provider("source-a", 0));
		providers.add(provider("source-b", 1));
		StremioSessionCoordinator coordinator = gateway.sessions();

		itemGateway.searchResults = new SearchResults("matrix", List.of(
				media("source-a", "tt0133093", "The Matrix from A")));
		String stableId = await(coordinator.searchVoice("matrix", Locale.ENGLISH))
				.choice(1).stableId();
		assertEquals("source-a", await(gateway.loadItem(stableId)).sourceUuid());

		itemGateway.searchResults = new SearchResults("matrix", List.of(
				media("source-b", "imdb:tt0133093", "The Matrix from B")));
		String sameStableId = await(coordinator.searchVoice("matrix", Locale.ENGLISH))
				.choice(1).stableId();
		assertEquals(stableId, sameStableId);
		assertEquals("source-a", await(gateway.loadItem(stableId)).sourceUuid());

		putSource("source-a", false, 0);
		await(coordinator.searchVoice("matrix", Locale.ENGLISH));

		StremioSessionItem restored = await(gateway.loadItem(stableId));
		assertEquals("source-b", restored.sourceUuid());
		assertEquals("The Matrix from B", restored.title());
		assertEquals(StremioItemAvailability.AVAILABLE,
				await(coordinator.resolveStableItem(stableId)).availability());
	}

	@Test
	public void canonicalCacheChangesOwnerAfterPreviousOwnerIsRemoved()
			throws Exception {
		putSource("source-a", true, 0);
		putSource("source-b", true, 1);
		providers.add(provider("source-a", 0));
		providers.add(provider("source-b", 1));
		StremioSessionCoordinator coordinator = gateway.sessions();

		itemGateway.searchResults = new SearchResults("matrix", List.of(
				media("source-a", "tt0133093", "The Matrix from A")));
		String stableId = await(coordinator.searchVoice("matrix", Locale.ENGLISH))
				.choice(1).stableId();
		await(repository.deleteSource("source-a"));

		itemGateway.searchResults = new SearchResults("matrix", List.of(
				media("source-b", "tt0133093", "The Matrix from B")));
		await(coordinator.searchVoice("matrix", Locale.ENGLISH));

		StremioSessionItem restored = await(gateway.loadItem(stableId));
		assertEquals("source-b", restored.sourceUuid());
		assertEquals(StremioItemAvailability.AVAILABLE,
				await(coordinator.resolveStableItem(stableId)).availability());
	}

	@Test
	public void voiceSeesNewProvidersAndKeepsThreeStableChoices() throws Exception {
		putSource("source-a", true, 0);
		providers.add(provider("source-a", 0));
		itemGateway.searchResults = new SearchResults("movie", List.of(
				media("source-a", "a", "Movie")));
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);

		StremioVoiceResult first = await(coordinator.searchVoice("movie", Locale.ENGLISH));
		assertEquals(1, first.choices().size());

		putSource("source-b", true, 1);
		putSource("source-c", true, 2);
		putSource("source-new", true, 3);
		providers.add(provider("source-b", 1));
		providers.add(provider("source-c", 2));
		providers.add(provider("source-new", 3));
		itemGateway.searchResults = new SearchResults("movie", List.of(
				media("source-a", "a", "Movie"),
				media("source-b", "b", "Movie Alpha"),
				media("source-c", "c", "Movie Beta"),
				media("source-new", "new", "Movie New Provider")));

		StremioVoiceResult refreshed = await(coordinator.searchVoice("movie", Locale.ENGLISH));
		assertEquals(3, refreshed.choices().size());
		assertEquals("source-a", refreshed.choice(1).sourceUuid());
		assertEquals("source-b", refreshed.choice(2).sourceUuid());
		assertEquals("source-c", refreshed.choice(3).sourceUuid());
		assertNotNull(await(coordinator.selectVoiceResult(refreshed, 3)).item());

		itemGateway.searchResults = new SearchResults("new", List.of(
				media("source-new", "new", "New Provider Movie")));
		StremioVoiceResult newProvider = await(coordinator.searchVoice("new", Locale.ENGLISH));
		assertEquals("source-new", newProvider.choice(1).sourceUuid());
	}

	@Test
	public void providerUrlCannotBecomeVoiceOrSessionTitle() throws Exception {
		putSource("source-a", true, 0);
		providers.add(provider("source-a", 0));
		itemGateway.searchResults = new SearchResults("provider", List.of(
				media("source-a", "url-title", "https://provider.example.invalid/watch/1")));

		List<StremioVoiceCandidate> candidates = await(gateway.search(
				"provider", Locale.ENGLISH, 3));
		assertEquals(1, candidates.size());
		assertEquals("Stremio", candidates.get(0).title());
		assertFalse(candidates.get(0).title().contains("http"));
		assertEquals("Stremio", await(gateway.loadItem(
				candidates.get(0).stableId())).title());

		itemGateway.searchResults = new SearchResults("live", List.of(
				media("source-a", "normal-title", "Live: Episode 1")));
		assertEquals("Live: Episode 1", await(gateway.search(
				"live", Locale.ENGLISH, 3)).get(0).title());
	}

	private StremioSessionGatewayAdapter createGateway() {
		return new StremioSessionGatewayAdapter(repository,
				() -> CompletableFuture.completedFuture(List.copyOf(providers)),
				itemGateway, Runnable::run);
	}

	private String putMovie(String sourceUuid, String providerId, String title) throws Exception {
		StremioPlaybackIdentity identity = StremioPlaybackIdentity.scoped(
				sourceUuid, "movie", providerId, providerId);
		await(repository.putMeta(new StremioMetaRecord(identity.contentKey(), sourceUuid,
				"movie", providerId, null, title, "", null, null, null, "",
				7_200_000L, "[]", 1L)));
		await(repository.putVideo(new StremioVideoRecord(identity.videoKey(),
				identity.contentKey(), "movie", providerId, title, null, null,
				0L, 7_200_000L, null, 1L)));
		return identity.videoKey();
	}

	private String putCanonicalMovie() throws Exception {
		StremioMetaIdentity metaIdentity = StremioMetaIdentity.create(
				"source-a", "movie", "provider:a", "imdb:tt0133093");
		StremioCanonicalIdentity canonical = StremioCanonicalIdentity.from(
				"movie", "imdb:tt0133093");
		StremioPlaybackIdentity playback = canonical.playbackIdentity(
				"movie", canonical.contentId(), -1, -1);
		await(repository.putMeta(new StremioMetaRecord(metaIdentity.metaKey(),
				metaIdentity.identityScope(), "movie", metaIdentity.durableMetaId(),
				metaIdentity.canonicalIdentity(), "The Matrix", "", null, null, null,
				"", 7_200_000L, "[]", 1L)));
		await(repository.putMetaProvider(new StremioMetaProviderRecord(metaIdentity.metaKey(),
				"source-a", "provider:a", 0, 1L)));
		await(repository.putMetaProvider(new StremioMetaProviderRecord(metaIdentity.metaKey(),
				"source-b", "provider:b", 1, 1L)));
		await(repository.putVideo(new StremioVideoRecord(playback.videoKey(),
				metaIdentity.metaKey(), "movie", canonical.contentId(), "The Matrix",
				null, null, 0L, 7_200_000L, null, 1L)));
		return playback.videoKey();
	}

	private void putSource(String sourceUuid, boolean enabled, int position) throws Exception {
		await(repository.putSource(new StremioSourceRecord(sourceUuid, "fingerprint-" + sourceUuid,
				"org.fixture." + sourceUuid, sourceUuid, "1.0", "/manifest.json", null,
				enabled, position, "{}", null, null, 1L, 1L, null, 1L, 1L)));
	}

	private static BrowseMedia media(String sourceUuid, String id, String title) {
		return new BrowseMedia(sourceUuid, "movie", id, title, null, null, "", "",
				null, List.of(), "en");
	}

	private static BrowseProvider provider(String sourceUuid, int position) {
		StremioManifest manifest = new StremioManifest("org.fixture." + sourceUuid,
				sourceUuid, "Fixture", "1.0", List.of("movie"),
				PrefixConstraint.unrestricted(),
				List.of(ResourceCapability.inherited("catalog")), List.of(),
				ManifestBehaviorHints.NONE);
		return new BrowseProvider(sourceUuid, sourceUuid, manifest, true, position);
	}

	private static <T> T await(CompletionStage<T> stage) throws Exception {
		return stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static <T> T awaitFuture(FutureSupplier<T> future) throws Exception {
		CompletableFuture<T> result = new CompletableFuture<>();
		future.onCompletion((value, error) -> {
			if (error == null) result.complete(value);
			else result.completeExceptionally(error);
		});
		return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		file.delete();
	}

	private static final class FakeItemGateway implements StremioItemGateway {
		SearchResults searchResults = new SearchResults("", List.of());
		BrowseDetails metaResult;
		int progressWrites;
		int metaLoads;
		boolean preparationProgressDelegated;

		@Override
		public FutureSupplier<List<BrowseProvider>> providers() {
			return Completed.completedEmptyList();
		}

		@Override
		public FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid) {
			return Completed.completedEmptyList();
		}

		@Override
		public FutureSupplier<CatalogPage> catalog(CatalogRoute route,
				String genre, int skip) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
			metaLoads++;
			return Completed.completed((metaResult == null) ?
					new BrowseDetails(media, List.of()) : metaResult);
		}

		@Override
		public FutureSupplier<SearchResults> search(String query) {
			return Completed.completed(searchResults);
		}

		@Override
		public FutureSupplier<StreamAggregationResult> streams(StreamAggregationRequest request) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<PlaybackDescriptor> resolve(
				PlaybackDescriptor.DescriptorRefreshRequest request) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<me.aap.fermata.media.net.RemotePlaybackRequest> preparePlayback(
				PlaybackDescriptor descriptor,
				java.util.function.Consumer<RemotePlaybackProgress> progress) {
			preparationProgressDelegated = true;
			progress.accept(RemotePlaybackProgress.findingPeers());
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<Void> saveProgress(
				StremioPlaybackIdentity identity, long position, boolean completed) {
			progressWrites++;
			return Completed.completedVoid();
		}
	}
}
