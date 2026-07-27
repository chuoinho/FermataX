package me.aap.fermata.addon.stremio.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StremioSessionCoordinatorTest {
	private static final long TIMEOUT_SECONDS = 5L;

	@Test
	public void resolvesProviderAvailabilityWithoutGuessing() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);

		assertEquals(StremioItemAvailability.AVAILABLE,
				await(coordinator.resolveStableItem("stremio:video:a")).availability());
		gateway.states.put("source-b", StremioProviderState.DISABLED);
		assertEquals(StremioItemAvailability.PROVIDER_DISABLED,
				await(coordinator.resolveStableItem("stremio:video:b")).availability());
		gateway.states.put("source-c", StremioProviderState.REMOVED);
		assertEquals(StremioItemAvailability.PROVIDER_REMOVED,
				await(coordinator.resolveStableItem("stremio:video:c")).availability());
		assertEquals(StremioItemAvailability.ITEM_MISSING,
				await(coordinator.resolveStableItem("stremio:video:missing")).availability());
	}

	@Test
	public void favoritesSynchronizeUsingCanonicalIdentity() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);

		await(coordinator.synchronizeFavorite("stremio:video:a", true));
		await(coordinator.synchronizeFavorite("stremio:video:a", false));
		await(coordinator.synchronizeFavorite("stremio:video:missing", true));

		assertEquals(2, gateway.favoriteUpdates.size());
		assertEquals("stremio:content:series", gateway.favoriteUpdates.get(0).canonicalContentKey());
		assertTrue(gateway.favoriteUpdates.get(0).favorite());
		assertFalse(gateway.favoriteUpdates.get(1).favorite());
	}

	@Test
	public void continueSnapshotIsBoundedOrderedAndDeduplicatedByStableItem() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionItem a = gateway.items.get("stremio:video:a");
		StremioSessionItem b = gateway.items.get("stremio:video:b");
		gateway.continueEntries = List.of(
				new StremioContinueEntry(a, 1_000L, 10_000L, 30L),
				new StremioContinueEntry(a, 2_000L, 10_000L, 20L),
				new StremioContinueEntry(b, 3_000L, 10_000L, 10L));

		List<StremioContinueEntry> result = await(new StremioSessionCoordinator(gateway)
				.loadContinue(Integer.MAX_VALUE));

		assertEquals(StremioSessionCoordinator.MAX_CONTINUE_ITEMS, gateway.lastContinueLimit);
		assertEquals(List.of(a.stableId(), b.stableId()), result.stream()
				.map(entry -> entry.item().stableId()).toList());
		assertEquals(1_000L, result.get(0).positionMs());
		assertTrue(await(new StremioSessionCoordinator(gateway).loadContinue(0)).isEmpty());
	}

	@Test
	public void continueEntryRejectsNonResumableProgressAndRedactsMetadataFromLogs() {
		StremioSessionItem item = movie("safe", "source-a", "Private title");
		assertThrows(IllegalArgumentException.class,
				() -> new StremioContinueEntry(item, 0L, 10L, 1L));
		assertThrows(IllegalArgumentException.class,
				() -> new StremioContinueEntry(item, 10L, 10L, 1L));
		StremioContinueEntry entry = new StremioContinueEntry(item, 1L, 10L, 1L);
		assertEquals(item.artwork(), entry.item().artwork());
		assertFalse(entry.toString().contains("Private title"));
		assertFalse(entry.toString().contains("https://"));
		assertThrows(SecurityException.class, () -> new StremioContinueEntry(
				movie("unsafe", "source-a", "https://provider.test/watch"),
				1L, 10L, 1L));
	}

	@Test
	public void smartTopAndProcessRestoreKeepExactItemAndBackDestination() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator first = new StremioSessionCoordinator(gateway);
		StremioPlaybackOwnership ownership = await(first.activatePlayback(
				"stremio:video:b", 7L, 900L));

		StremioSmartTopTarget current = first.getCurrentTarget().orElseThrow();
		assertEquals("Episode B", current.title());
		assertEquals("stremio:season:one", current.backToListId());
		assertEquals("stremio:video:b", ownership.stableId());

		StremioSessionCoordinator recreated = new StremioSessionCoordinator(gateway);
		StremioItemResolution restored = await(recreated.restoreAfterProcessDeath());
		assertTrue(restored.isAvailable());
		assertEquals("stremio:video:b", restored.item().stableId());
		assertEquals("Episode B", recreated.getCurrentTarget().orElseThrow().title());
		assertEquals("stremio:season:one",
				recreated.getCurrentTarget().orElseThrow().backToListId());

		gateway.states.put("source-b", StremioProviderState.DISABLED);
		assertEquals(StremioItemAvailability.PROVIDER_DISABLED,
				await(new StremioSessionCoordinator(gateway)
						.restoreAfterProcessDeath()).availability());
	}

	@Test
	public void deterministicEpisodeQueueIgnoresInputOrderAndDuplicates() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		gateway.queue = List.of(
				gateway.items.get("stremio:video:c"),
				gateway.items.get("stremio:video:a"),
				gateway.items.get("stremio:video:b"),
				gateway.items.get("stremio:video:b"));
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);

		assertEquals("stremio:video:a", await(coordinator.adjacentEpisode(
				"stremio:video:b", StremioAdjacentDirection.PREVIOUS)).item().stableId());
		assertEquals("stremio:video:c", await(coordinator.adjacentEpisode(
				"stremio:video:b", StremioAdjacentDirection.NEXT)).item().stableId());
		assertEquals(StremioItemAvailability.ITEM_MISSING,
				await(coordinator.adjacentEpisode("stremio:video:a",
						StremioAdjacentDirection.PREVIOUS)).availability());
	}

	@Test
	public void voiceSearchIsLocaleAwareFreshAndStableForOneToThreeSelection() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		gateway.voiceCandidates = List.of(
				voice("stremio:video:c", "source-c", "İzmir Stories", 1),
				voice("stremio:video:b", "source-b", "İzmir", 5),
				voice("stremio:video:a", "source-a", "İzmir Adventure", 0),
				voice("stremio:video:d", "source-d", "Other", 0));
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		assertEquals("stremio", coordinator.getVoiceTarget());

		StremioVoiceResult result = await(coordinator.searchVoice("  İZMİR  ",
				Locale.forLanguageTag("tr-TR")));
		assertEquals("izmir", gateway.lastVoiceQuery);
		assertEquals("tr-TR", gateway.lastVoiceLocale.toLanguageTag());
		assertEquals(3, result.choices().size());
		assertEquals("stremio:video:b", result.choice(1).stableId());
		assertEquals("stremio:video:a", result.choice(2).stableId());
		assertEquals("stremio:video:c", result.choice(3).stableId());
		assertNull(result.choice(4));
		assertEquals("stremio:video:a",
				await(coordinator.selectVoiceResult(result, 2)).item().stableId());
		assertThrows(Exception.class,
				() -> await(coordinator.selectVoiceResult(result, 2)));

		gateway.voiceCandidates = List.of(voice(
				"stremio:video:d", "source-d", "İzmir New Provider", 0));
		gateway.items.put("stremio:video:d", movie("d", "source-d", "İzmir New Provider"));
		gateway.states.put("source-d", StremioProviderState.ENABLED);
		StremioVoiceResult refreshed = await(coordinator.searchVoice("İzmir",
				Locale.forLanguageTag("tr-TR")));
		assertEquals("stremio:video:d", refreshed.choice(1).stableId());
		assertThrows(Exception.class,
				() -> await(coordinator.selectVoiceResult(result, 1)));
	}

	@Test
	public void latestVoiceGenerationRejectsLateProviderResponse() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		CompletableFuture<List<StremioVoiceCandidate>> delayed = new CompletableFuture<>();
		gateway.nextVoiceResponse = delayed;
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		CompletionStage<StremioVoiceResult> old = coordinator.searchVoice("old", Locale.ENGLISH);

		gateway.voiceCandidates = List.of(voice(
				"stremio:video:a", "source-a", "New", 0));
		StremioVoiceResult current = await(coordinator.searchVoice("new", Locale.ENGLISH));
		delayed.complete(List.of(voice("stremio:video:b", "source-b", "Old", 0)));

		assertEquals("stremio:video:a", current.choice(1).stableId());
		assertThrows(Exception.class, () -> await(old));
	}

	@Test
	public void voiceExcludesDisabledAndRemovedProvidersDefensively() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		gateway.voiceCandidates = List.of(
				voice("stremio:video:a", "source-a", "Movie", 0),
				voice("stremio:video:b", "source-b", "Movie Two", 0),
				voice("stremio:video:c", "source-c", "Movie Three", 0));
		gateway.states.put("source-b", StremioProviderState.DISABLED);
		gateway.states.put("source-c", StremioProviderState.REMOVED);

		StremioVoiceResult result = await(new StremioSessionCoordinator(gateway)
				.searchVoice("movie", Locale.ENGLISH));
		assertEquals(1, result.choices().size());
		assertEquals("stremio:video:a", result.choice(1).stableId());
	}

	@Test
	public void lateActivationCannotReplaceNewerPlaybackOwnership() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		CompletableFuture<StremioSessionItem> delayed = new CompletableFuture<>();
		gateway.nextItemResponse = delayed;
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		CompletionStage<StremioPlaybackOwnership> old = coordinator.activatePlayback(
				"stremio:video:a", 1L, 1L);

		StremioPlaybackOwnership current = await(coordinator.activatePlayback(
				"stremio:video:b", 2L, 2L));
		delayed.complete(gateway.items.get("stremio:video:a"));

		assertThrows(Exception.class, () -> await(old));
		assertEquals("stremio:video:b", current.stableId());
		assertEquals("stremio:video:b",
				coordinator.getCurrentTarget().orElseThrow().stableId());
	}

	@Test
	public void outOfOrderAbaCannotReclaimGenerationOrWriteProgress() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		StremioPlaybackOwnership firstA = await(coordinator.activatePlayback(
				"stremio:video:a", 10L, 10L));
		StremioProgressSnapshot staleA = coordinator.snapshotProgressFromCore(
				firstA, 1_000L, false, 11L).orElseThrow();

		CompletableFuture<StremioSessionItem> delayedB = new CompletableFuture<>();
		gateway.nextItemResponse = delayedB;
		CompletionStage<StremioPlaybackOwnership> pendingB = coordinator.activatePlayback(
				"stremio:video:b", 11L, 12L);
		assertThrows(Exception.class, () -> await(coordinator.activatePlayback(
				"stremio:video:a", 10L, 13L)));

		delayedB.complete(gateway.items.get("stremio:video:b"));
		StremioPlaybackOwnership currentB = await(pendingB);
		assertEquals("stremio:video:b", currentB.stableId());
		assertEquals(StremioProgressWriteResult.REJECTED_STALE,
				await(coordinator.persistCoreProgress(staleA)));
		assertTrue(gateway.progressWrites.isEmpty());
		assertEquals("stremio:video:b",
				coordinator.getCurrentTarget().orElseThrow().stableId());
	}

	@Test
	public void sameMediaSessionGenerationCannotOwnTwoDifferentItems() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		await(coordinator.activatePlayback("stremio:video:a", 4L, 1L));

		assertThrows(Exception.class, () -> await(coordinator.activatePlayback(
				"stremio:video:b", 4L, 2L)));
		assertEquals("stremio:video:a",
				coordinator.getCurrentTarget().orElseThrow().stableId());
	}

	@Test
	public void failedRestorePersistenceDoesNotGrantPlaybackOwnership() {
		FakeGateway gateway = gatewayWithDefaultItems();
		gateway.failNextRestoreSave = true;
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);

		assertThrows(Exception.class, () -> await(coordinator.activatePlayback(
				"stremio:video:a", 1L, 1L)));
		assertTrue(coordinator.getCurrentTarget().isEmpty());
	}

	@Test
	public void oneHundredAbcSwitchesProduceZeroWrongItemWrites() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		String[] ids = {"stremio:video:a", "stremio:video:b", "stremio:video:c"};
		StremioProgressSnapshot previous = null;
		int staleRejected = 0;

		for (int switchNumber = 0; switchNumber < 100; switchNumber++) {
			String id = ids[switchNumber % ids.length];
			StremioPlaybackOwnership owner = await(coordinator.activatePlayback(
					id, switchNumber, 10_000L + switchNumber));

			if (previous != null) {
				assertEquals(StremioProgressWriteResult.REJECTED_STALE,
						await(coordinator.persistCoreProgress(previous)));
				staleRejected++;
			}

			long position = 1_000L + switchNumber;
			StremioProgressSnapshot current = coordinator.snapshotProgressFromCore(
					owner, position, false, 20_000L + switchNumber).orElseThrow();
			assertEquals(StremioProgressWriteResult.WRITTEN,
					await(coordinator.persistCoreProgress(current)));
			previous = current;
		}

		assertEquals(99, staleRejected);
		assertEquals(100, gateway.progressWrites.size());
		int wrongWrites = 0;
		for (int i = 0; i < gateway.progressWrites.size(); i++) {
			StremioProgressSnapshot write = gateway.progressWrites.get(i);
			String expectedId = ids[i % ids.length];
			long expectedPosition = 1_000L + i;
			if (!expectedId.equals(write.stableId()) ||
					(expectedPosition != write.positionMs()) || (write.positionMs() < 0L)) {
				wrongWrites++;
			}
		}
		assertEquals(0, wrongWrites);
	}

	@Test
	public void randomizedCompletionOfOneHundredAbcWritesNeverChangesSnapshotOwnership()
			throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		gateway.delayProgressWrites = true;
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		String[] ids = {"stremio:video:a", "stremio:video:b", "stremio:video:c"};
		Map<Long, String> expectedIds = new HashMap<>();
		Map<Long, Long> expectedPositions = new HashMap<>();
		List<CompletionStage<StremioProgressWriteResult>> writes = new ArrayList<>();

		for (int i = 0; i < 100; i++) {
			long generation = i + 1L;
			String stableId = ids[i % ids.length];
			StremioPlaybackOwnership owner = await(coordinator.activatePlayback(
					stableId, generation, 10_000L + i));
			long position = 5_000L + i;
			StremioProgressSnapshot snapshot = coordinator.snapshotProgressFromCore(
					owner, position, false, 20_000L + i).orElseThrow();
			expectedIds.put(generation, stableId);
			expectedPositions.put(generation, position);
			writes.add(coordinator.persistCoreProgress(snapshot));
		}

		List<PendingProgressWrite> completionOrder = new ArrayList<>(
				gateway.pendingProgressWrites);
		Collections.shuffle(completionOrder, new Random(0x5A17E11L));
		completionOrder.forEach(PendingProgressWrite::complete);
		for (CompletionStage<StremioProgressWriteResult> write : writes) {
			assertEquals(StremioProgressWriteResult.WRITTEN, await(write));
		}

		assertEquals(100, gateway.progressWrites.size());
		for (StremioProgressSnapshot write : gateway.progressWrites) {
			assertEquals(expectedIds.get(write.playbackGeneration()), write.stableId());
			assertEquals(expectedPositions.get(write.playbackGeneration()).longValue(),
					write.positionMs());
		}
	}

	@Test
	public void progressUsesCoreNormalizationAndRejectsReleasedOwnership() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		StremioPlaybackOwnership owner = await(coordinator.activatePlayback(
				"stremio:video:a", 1L, 1L));

		assertThrows(IllegalArgumentException.class, () ->
				coordinator.snapshotProgressFromCore(owner, 99L, true, 2L));
		StremioProgressSnapshot completed = coordinator.snapshotProgressFromCore(
				owner, 0L, true, 2L).orElseThrow();
		assertEquals(StremioProgressWriteResult.WRITTEN,
				await(coordinator.persistCoreProgress(completed)));
		coordinator.releasePlayback(owner);
		assertTrue(coordinator.snapshotProgressFromCore(owner, 1L, false, 3L).isEmpty());
		assertTrue(coordinator.getCurrentTarget().isPresent());
	}

	@Test
	public void progressObserversReceiveOnlyCommittedOwnedWrites() throws Exception {
		FakeGateway gateway = gatewayWithDefaultItems();
		StremioSessionCoordinator coordinator = new StremioSessionCoordinator(gateway);
		AtomicInteger notifications = new AtomicInteger();
		AutoCloseable observer = coordinator.observeProgressChanges(
				notifications::incrementAndGet);

		StremioPlaybackOwnership first = await(coordinator.activatePlayback(
				"stremio:video:a", 1L, 1L));
		StremioProgressSnapshot stale = coordinator.snapshotProgressFromCore(
				first, 1_000L, false, 2L).orElseThrow();
		assertEquals(StremioProgressWriteResult.WRITTEN,
				await(coordinator.persistCoreProgress(stale)));
		assertEquals(1, notifications.get());

		StremioPlaybackOwnership current = await(coordinator.activatePlayback(
				"stremio:video:b", 2L, 3L));
		assertEquals(StremioProgressWriteResult.REJECTED_STALE,
				await(coordinator.persistCoreProgress(stale)));
		assertEquals(1, notifications.get());

		observer.close();
		StremioProgressSnapshot afterClose = coordinator.snapshotProgressFromCore(
				current, 2_000L, false, 5L).orElseThrow();
		assertEquals(StremioProgressWriteResult.WRITTEN,
				await(coordinator.persistCoreProgress(afterClose)));
		assertEquals(1, notifications.get());
	}

	@Test
	public void modelsRejectRawUrlsAndRedactUserMetadata() {
		assertThrows(IllegalArgumentException.class, () -> new StremioSessionItem(
				"https://provider.test/video", "stremio:content:x", "source-a", "Title",
				"", null, -1L, "stremio:list:x", null, -1, -1));
		StremioSessionItem item = movie("safe", "source-a", "Private title");
		String text = item.toString() + StremioSmartTopTarget.from(item) +
				voice(item.stableId(), item.sourceUuid(), item.title(), 0);
		assertFalse(text.contains("Private title"));
		assertFalse(text.contains("https://art.test"));
	}

	private static FakeGateway gatewayWithDefaultItems() {
		FakeGateway gateway = new FakeGateway();
		gateway.put(episode("a", "source-a", "Episode A", 1, 1));
		gateway.put(episode("b", "source-b", "Episode B", 1, 2));
		gateway.put(episode("c", "source-c", "Episode C", 2, 1));
		return gateway;
	}

	private static StremioSessionItem episode(String id, String source, String title,
			int season, int episode) {
		return new StremioSessionItem("stremio:video:" + id, "stremio:content:series",
				source, title, "S" + season + " E" + episode, "https://art.test/" + id,
				2_400_000L, "stremio:season:one", "stremio:queue:series", season, episode);
	}

	private static StremioSessionItem movie(String id, String source, String title) {
		return new StremioSessionItem("stremio:video:" + id, "stremio:content:" + id,
				source, title, "Movie", "https://art.test/" + id, 7_200_000L,
				"stremio:catalog:movies", null, -1, -1);
	}

	private static StremioVoiceCandidate voice(
			String id, String source, String title, int rank) {
		return new StremioVoiceCandidate(id, source, title, "", rank);
	}

	private static <T> T await(CompletionStage<T> stage) throws Exception {
		return stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static final class FakeGateway implements StremioSessionGateway {
		final Map<String, StremioSessionItem> items = new HashMap<>();
		final Map<String, StremioProviderState> states = new HashMap<>();
		final List<StremioFavoriteUpdate> favoriteUpdates = new ArrayList<>();
		final List<StremioProgressSnapshot> progressWrites = new ArrayList<>();
		final List<PendingProgressWrite> pendingProgressWrites = new ArrayList<>();
		List<StremioSessionItem> queue = List.of();
		List<StremioContinueEntry> continueEntries = List.of();
		List<StremioVoiceCandidate> voiceCandidates = List.of();
		CompletionStage<List<StremioVoiceCandidate>> nextVoiceResponse;
		CompletionStage<StremioSessionItem> nextItemResponse;
		boolean failNextRestoreSave;
		boolean delayProgressWrites;
		StremioRestorePoint restorePoint;
		String lastVoiceQuery;
		Locale lastVoiceLocale;
		int lastContinueLimit;

		void put(StremioSessionItem item) {
			items.put(item.stableId(), item);
			states.put(item.sourceUuid(), StremioProviderState.ENABLED);
		}

		@Override
		public CompletionStage<List<StremioContinueEntry>> loadContinue(int limit) {
			lastContinueLimit = limit;
			return CompletableFuture.completedFuture(continueEntries);
		}

		@Override
		public CompletionStage<StremioSessionItem> loadItem(String stableId) {
			if (nextItemResponse != null) {
				CompletionStage<StremioSessionItem> response = nextItemResponse;
				nextItemResponse = null;
				return response;
			}
			return CompletableFuture.completedFuture(items.get(stableId));
		}

		@Override
		public CompletionStage<StremioProviderState> getProviderState(String sourceUuid) {
			return CompletableFuture.completedFuture(states.get(sourceUuid));
		}

		@Override
		public CompletionStage<Void> synchronizeFavorite(StremioFavoriteUpdate update) {
			favoriteUpdates.add(update);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<Void> writeProgress(StremioProgressSnapshot snapshot) {
			if (delayProgressWrites) {
				PendingProgressWrite pending = new PendingProgressWrite(snapshot, progressWrites);
				pendingProgressWrites.add(pending);
				return pending.completion;
			}
			progressWrites.add(snapshot);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<Void> saveRestorePoint(StremioRestorePoint restorePoint) {
			if (failNextRestoreSave) {
				failNextRestoreSave = false;
				return CompletableFuture.failedFuture(new IllegalStateException("write failed"));
			}
			this.restorePoint = restorePoint;
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<StremioRestorePoint> loadRestorePoint() {
			return CompletableFuture.completedFuture(restorePoint);
		}

		@Override
		public CompletionStage<List<StremioSessionItem>> loadEpisodeQueue(String episodeQueueId) {
			return CompletableFuture.completedFuture(queue);
		}

		@Override
		public CompletionStage<List<StremioVoiceCandidate>> search(
				String normalizedQuery, Locale locale, int limit) {
			lastVoiceQuery = normalizedQuery;
			lastVoiceLocale = locale;
			if (nextVoiceResponse != null) {
				CompletionStage<List<StremioVoiceCandidate>> response = nextVoiceResponse;
				nextVoiceResponse = null;
				return response;
			}
			return CompletableFuture.completedFuture(voiceCandidates);
		}
	}

	private static final class PendingProgressWrite {
		private final StremioProgressSnapshot snapshot;
		private final List<StremioProgressSnapshot> writes;
		private final CompletableFuture<Void> completion = new CompletableFuture<>();

		private PendingProgressWrite(StremioProgressSnapshot snapshot,
				List<StremioProgressSnapshot> writes) {
			this.snapshot = snapshot;
			this.writes = writes;
		}

		private void complete() {
			writes.add(snapshot);
			completion.complete(null);
		}
	}
}
