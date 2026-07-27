package me.aap.fermata.addon.stremio.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.addon.stremio.protocol.model.CatalogCapability;
import me.aap.fermata.addon.stremio.protocol.model.CatalogExtra;
import me.aap.fermata.addon.stremio.protocol.model.ManifestBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.model.PrefixConstraint;
import me.aap.fermata.addon.stremio.protocol.model.ResourceCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StremioBrowseRepositoryTest {
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
	private final ScheduledExecutorService workers = Executors.newScheduledThreadPool(4);
	private final List<StremioBrowseRepository> repositories = new ArrayList<>();

	@After
	public void cleanup() {
		repositories.forEach(StremioBrowseRepository::close);
		scheduler.shutdownNow();
		workers.shutdownNow();
	}

	@Test
	public void aggregatesOnlyEnabledCatalogsInStableProviderOrder() {
		var late = provider("late", true, 2, catalog("movie", "popular", "Popular",
				List.of(extra("genre", false, List.of("Drama", "H\u00e0i")),
						extra("search", false, List.of()))));
		var early = provider("early", true, 0, catalog("series", "featured", "Featured",
				List.of()));
		var disabled = provider("disabled", false, 1,
				catalog("movie", "hidden", "Hidden", List.of()));
		var repository = repository((provider, request, generation) -> failed("unused"));

		var catalogs = repository.catalogs(List.of(late, disabled, early));

		assertEquals(List.of("early", "late"),
				catalogs.stream().map(item -> item.route().sourceUuid()).toList());
		assertEquals(List.of("Drama", "H\u00e0i"), catalogs.get(1).genres());
		assertTrue(catalogs.get(1).searchable());
		assertThrows(UnsupportedOperationException.class, catalogs::clear);
	}

	@Test
	public void routesCatalogExactlyAndEncodesGenreAndSkip() throws Exception {
		var calls = new CopyOnWriteArrayList<String>();
		var provider = provider("source-a", true, 0, catalog("movie", "popular", "Popular",
				List.of(extra("genre", false, List.of("Khoa h\u1ecdc")),
						extra("skip", false, List.of()))));
		var repository = repository((selected, request, generation) -> {
			calls.add(selected.sourceUuid() + '|' + request.type() + '|' + request.id() + '|' +
					request.extras().get("genre") + '|' + request.extras().get("skip"));
			return payload(catalogJson("m1", "movie", "Phim \u0111\u1eb9p"), false);
		});

		var state = repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "popular"), "Khoa h\u1ecdc", 20, null)
				.result().get(2, TimeUnit.SECONDS);

		var content = content(state);
		assertEquals("source-a|movie|popular|Khoa h\u1ecdc|20", calls.get(0));
		assertEquals("Phim \u0111\u1eb9p", content.value().items().get(0).title());
		assertEquals(21, content.value().nextSkip());
		assertTrue(content.value().hasNext());
	}

	@Test
	public void forwardsArbitraryCatalogExtrasAlongsideGenreAndSkip() throws Exception {
		var provider = provider("source-a", true, 0, catalog("movie", "search", "Search",
				List.of(extra("search", true, List.of("alpha", "beta")),
						extra("genre", false, List.of("Drama")),
						extra("skip", false, List.of()))));
		var calls = new CopyOnWriteArrayList<Map<String, ?>>();
		var repository = repository((selected, request, generation) -> {
			calls.add(request.extras());
			return payload(catalogJson("m1", "movie", "Result"), false);
		});

		var state = repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "search"), "Drama", 20,
				Map.of("search", List.of("alpha")), null)
				.result().get(2, TimeUnit.SECONDS);

		assertTrue(state instanceof BrowseLoadState.Content<?>);
		assertEquals(List.of("alpha"), calls.get(0).get("search"));
		assertEquals("Drama", calls.get(0).get("genre"));
		assertEquals("20", calls.get(0).get("skip"));
	}

	@Test
	public void rejectsWrongCatalogTypeIdAndDisabledProviderWithoutGatewayCall() throws Exception {
		var calls = new AtomicInteger();
		var provider = provider("source-a", false, 0,
				catalog("movie", "popular", "Popular", List.of()));
		var repository = repository((selected, request, generation) -> {
			calls.incrementAndGet();
			return payload("{}", false);
		});

		var state = repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "series", "popular"), null, 0, null)
				.result().get(2, TimeUnit.SECONDS);

		assertTrue(state instanceof BrowseLoadState.Failure<?>);
		assertEquals(0, calls.get());
	}

	@Test
	public void rejectsCatalogResponseWithMismatchedType() throws Exception {
		var provider = provider("source-a", true, 0,
				catalog("movie", "popular", "Popular", List.of()));
		var repository = repository((selected, request, generation) ->
				payload(catalogJson("show", "series", "Wrong type"), false));

		var state = repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "popular"), null, 0, null)
				.result().get(2, TimeUnit.SECONDS);

		assertTrue(state instanceof BrowseLoadState.Failure<?>);
		assertFalseRetryable(state);
	}

	@Test
	public void detailsFallbackToCompatibleMetadataAddonWithoutChangingOwnership() throws Exception {
		BrowseProvider catalogProvider = provider("catalog", true, 0,
				catalog("movie", "popular", "Popular", List.of()));
		BrowseProvider metadataProvider = provider("metadata", true, 1);
		List<String> calls = new CopyOnWriteArrayList<>();
		StremioBrowseRepository repository = repository((selected, request, generation) -> {
			calls.add(selected.sourceUuid());
			if (selected.sourceUuid().equals("catalog")) {
				return failed(new BrowseGatewayException("primary unavailable", true));
			}
			return payload("{\"meta\":" + meta("movie-1", "movie", "Fallback") + "}",
					false);
		});

		BrowseDetails details = content(repository.loadDetails(
				List.of(metadataProvider, catalogProvider), "catalog", "movie", "movie-1", null)
				.result().get(2, TimeUnit.SECONDS)).value();

		assertEquals(List.of("catalog", "metadata"), calls);
		assertEquals("catalog", details.media().sourceUuid());
		assertEquals("Fallback", details.media().title());
	}

	@Test
	public void paginationIsBoundedAndEmptyPageStops() throws Exception {
		var provider = provider("source-a", true, 0, catalog("movie", "popular", "Popular",
				List.of(extra("skip", false, List.of()))));
		var repository = repository((selected, request, generation) -> payload(
				!request.extras().containsKey("skip") ? "{\"metas\":[]}" :
						"{\"metas\":[" + meta("1", "movie", "One") + ',' +
								meta("2", "movie", "Two") + ',' + meta("3", "movie", "Three") + "]}",
				false), 2, 2, 0);

		var first = content(repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "popular"), null, 1, null)
				.result().get(2, TimeUnit.SECONDS)).value();
		var empty = repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "popular"), null, 0, null)
				.result().get(2, TimeUnit.SECONDS);

		assertEquals(List.of("1", "2"), first.items().stream().map(BrowseMedia::id).toList());
		assertEquals(3, first.nextSkip());
		assertTrue(first.hasNext());
		assertTrue(empty instanceof BrowseLoadState.Empty<?>);
	}

	@Test
	public void shortNonEmptyCatalogPageKeepsPaginationAvailable() throws Exception {
		var provider = provider("source-a", true, 0, catalog("movie", "popular", "Popular",
				List.of(extra("skip", false, List.of()))));
		var repository = repository((selected, request, generation) -> payload(
				"{\"metas\":[" + meta("1", "movie", "One") + "]}", false), 2, 2, 0);

		var page = content(repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "popular"), null, 0, null)
				.result().get(2, TimeUnit.SECONDS)).value();

		assertTrue(page.hasNext());
	}

	@Test
	public void catalogWithoutSkipCapabilityRemainsSinglePage() throws Exception {
		var provider = provider("source-a", true, 0,
				catalog("movie", "popular", "Popular", List.of()));
		var repository = repository((selected, request, generation) -> payload(
				"{\"metas\":[" + meta("1", "movie", "One") + "]}", false), 2, 2, 0);

		var page = content(repository.loadCatalog(List.of(provider),
				new CatalogRoute("source-a", "movie", "popular"), null, 0, null)
				.result().get(2, TimeUnit.SECONDS)).value();

		assertFalse(page.hasNext());
	}

	@Test
	public void normalizesLongUnicodeTitleAndGroupsEpisodesDeterministically() throws Exception {
		var title = "\u6771\u4eac - \u0110i\u1ec7n \u1ea3nh - " + "L".repeat(300);
		var provider = provider("series-source", true, 0,
				catalog("series", "shows", "Shows", List.of()));
		var repository = repository((selected, request, generation) -> payload("""
				{"meta":{"id":"show-1","type":"series","name":"%s","videos":[
				 {"id":"s2e1","title":"S2 E1","season":2,"episode":1},
				 {"id":"special","title":"Special"},
				 {"id":"s1e10","title":"S1 E10","season":1,"episode":10},
				 {"id":"s1e2","title":"S1 E2","season":1,"episode":2}
				]}}
				""".formatted(title), false));

		var details = content(repository.loadDetails(List.of(provider), "series-source",
				"series", "show-1", null).result().get(2, TimeUnit.SECONDS)).value();

		assertEquals(title, details.media().title());
		assertEquals(List.of(0, 1, 2), details.seasons().stream().map(BrowseSeason::number).toList());
		assertEquals(List.of("s1e2", "s1e10"), details.seasons().get(1).episodes().stream()
				.map(BrowseEpisode::videoId).toList());
		assertEquals("series-source:series:show-1:s1e2",
				details.seasons().get(1).episodes().get(0).scopedId());
	}

	@Test
	public void searchAggregatesProviderScopedResultsAndReportsPartialStaleFailure()
			throws Exception {
		var good = provider("good", true, 0, searchable("movie", "search-a"));
		var stale = provider("stale", true, 1, searchable("movie", "search-b"));
		var bad = provider("bad", true, 2, searchable("movie", "search-c"));
		var repository = repository((selected, request, generation) -> switch (selected.sourceUuid()) {
			case "good" -> payload(catalogJson("same", "movie", "C\u00e0 ph\u00ea"), false);
			case "stale" -> payload(catalogJson("same", "movie", "C\u00e0 ph\u00ea stale"), true);
			default -> failed(new BrowseGatewayException("timeout", true));
		}, 2, 20, 0);

		var state = repository.search(List.of(good, stale, bad),
				"c\u00e0 ph\u00ea \u6771\u4eac", null)
				.result().get(2, TimeUnit.SECONDS);

		var content = content(state);
		assertEquals("c\u00e0 ph\u00ea \u6771\u4eac", content.value().query());
		assertEquals(2, content.value().items().size());
		assertEquals(List.of("good:movie:same", "stale:movie:same"),
				content.value().items().stream().map(BrowseMedia::scopedId).toList());
		assertTrue(content.stale());
		assertTrue(content.partial());
		assertTrue(content.canRetry());
	}

	@Test
	public void rapidSearchReplacementCancelsDebouncedGeneration() throws Exception {
		var calls = new CopyOnWriteArrayList<String>();
		var provider = provider("source", true, 0, searchable("movie", "search"));
		var repository = repository((selected, request, generation) -> {
			calls.add((String) request.extras().get("search"));
			return payload(catalogJson("1", "movie",
					(String) request.extras().get("search")), false);
		}, 2, 20, 80);

		var old = repository.search(List.of(provider), "old", null);
		Thread.sleep(10);
		var latest = repository.search(List.of(provider), "new", null);
		var state = latest.result().get(2, TimeUnit.SECONDS);

		assertTrue(old.result().isCancelled());
		assertEquals(List.of("new"), calls);
		assertEquals("new", content(state).value().items().get(0).title());
	}

	@Test
	public void lateSearchResponseCannotReplaceNewGeneration() throws Exception {
		var provider = provider("source", true, 0, searchable("movie", "search"));
		var oldResponse = new CompletableFuture<BrowsePayload>();
		var oldStarted = new CountDownLatch(1);
		var repository = repository((selected, request, generation) -> {
			if ("old".equals(request.extras().get("search"))) {
				oldStarted.countDown();
				return oldResponse;
			}
			return payload(catalogJson("new-id", "movie", "new"), false);
		}, 2, 20, 0);

		var old = repository.search(List.of(provider), "old", null);
		assertTrue(oldStarted.await(2, TimeUnit.SECONDS));
		var latest = repository.search(List.of(provider), "new", null);
		oldResponse.complete(new BrowsePayload(
				catalogJson("old-id", "movie", "old").getBytes(StandardCharsets.UTF_8), false));

		var state = content(latest.result().get(2, TimeUnit.SECONDS));
		assertTrue(old.result().isCancelled());
		assertEquals("new-id", state.value().items().get(0).id());
	}

	@Test
	public void closingOldSearchHandleCannotCancelCurrentSearch() throws Exception {
		var provider = provider("source", true, 0, searchable("movie", "search"));
		var currentResponse = new CompletableFuture<BrowsePayload>();
		var repository = repository((selected, request, generation) -> {
			if ("current".equals(request.extras().get("search"))) return currentResponse;
			return payload(catalogJson("old-id", "movie", "old"), false);
		}, 2, 20, 0);

		var old = repository.search(List.of(provider), "old", null);
		content(old.result().get(2, TimeUnit.SECONDS));
		var current = repository.search(List.of(provider), "current", null);
		old.close();
		currentResponse.complete(new BrowsePayload(
				catalogJson("current-id", "movie", "current")
						.getBytes(StandardCharsets.UTF_8), false));

		assertEquals("current-id", content(current.result().get(2, TimeUnit.SECONDS))
				.value().items().get(0).id());
	}

	@Test
	public void searchConcurrencyAndResultCountAreBounded() throws Exception {
		var providers = new ArrayList<BrowseProvider>();
		for (int i = 0; i < 6; i++) providers.add(provider("p" + i, true, i,
				searchable("movie", "search")));
		var active = new AtomicInteger();
		var maximum = new AtomicInteger();
		var started = new CountDownLatch(2);
		var releases = new CopyOnWriteArrayList<CompletableFuture<BrowsePayload>>();
		var repository = repository((selected, request, generation) -> {
			int now = active.incrementAndGet();
			maximum.accumulateAndGet(now, Math::max);
			started.countDown();
			var result = new CompletableFuture<BrowsePayload>();
			releases.add(result);
			result.whenComplete((value, error) -> active.decrementAndGet());
			return result;
		}, 2, 20, 0);

		var search = repository.search(providers, "bounded", null);
		assertTrue(started.await(2, TimeUnit.SECONDS));
		while (!search.result().isDone()) {
			for (var pending : List.copyOf(releases)) {
				if (!pending.isDone()) pending.complete(new BrowsePayload(
						catalogJson("same", "movie", "Result").getBytes(StandardCharsets.UTF_8), false));
			}
			Thread.sleep(5);
		}

		var state = content(search.result().get(2, TimeUnit.SECONDS));
		assertEquals(2, maximum.get());
		assertEquals(6, state.value().items().size());
	}

	@Test
	public void searchResultLimitIsAppliedAfterStableDeduplication() throws Exception {
		var provider = provider("source", true, 0,
				searchable("movie", "first"), searchable("movie", "second"));
		var repository = new StremioBrowseRepository((selected, request, generation) -> payload(
				"{\"metas\":[" + meta("1", "movie", "One") + ',' +
						meta("2", "movie", "Two") + ',' + meta("3", "movie", "Three") + "]}",
				false), workers, scheduler, 2, 20, 2, 0);
		repositories.add(repository);

		var state = content(repository.search(List.of(provider), "query", null)
				.result().get(2, TimeUnit.SECONDS));

		assertEquals(List.of("1", "2"),
				state.value().items().stream().map(BrowseMedia::id).toList());
	}

	@Test
	public void allSearchProvidersFailWithRetryState() throws Exception {
		var provider = provider("bad", true, 0, searchable("movie", "search"));
		var repository = repository((selected, request, generation) ->
				failed(new BrowseGatewayException("offline", true)), 1, 20, 0);

		var state = repository.search(List.of(provider), "anything", null)
				.result().get(2, TimeUnit.SECONDS);

		assertTrue(state instanceof BrowseLoadState.Failure<?>);
		assertTrue(((BrowseLoadState.Failure<?>) state).canRetry());
	}

	@Test
	public void synchronousGatewayFailureBecomesRetryableLoadState() throws Exception {
		var provider = provider("source", true, 0,
				catalog("movie", "popular", "Popular", List.of()));
		var repository = repository((selected, request, generation) -> {
			throw new BrowseGatewayException("offline", true);
		});

		var state = repository.loadCatalog(List.of(provider),
				new CatalogRoute("source", "movie", "popular"), null, 0, null)
				.result().get(2, TimeUnit.SECONDS);

		assertTrue(state instanceof BrowseLoadState.Failure<?>);
		assertTrue(((BrowseLoadState.Failure<?>) state).canRetry());
	}

	@Test
	public void browseModelsDoNotExposePresentationUrlsOrTitlesInLogs() {
		var media = new BrowseMedia("source", "movie", "secret-id", "Secret title",
				"https://token.invalid/poster", "https://token.invalid/background",
				"Secret description", "2026", null, List.of("Drama"), "en");
		var episode = new BrowseEpisode("source", "series", "secret-series", "secret-video",
				"Secret episode", 1, 2, null, "https://token.invalid/thumb", null, null);

		for (var value : List.of(media.toString(), episode.toString())) {
			assertFalseContains(value, "Secret");
			assertFalseContains(value, "token.invalid");
			assertFalseContains(value, "secret-id");
			assertFalseContains(value, "secret-video");
		}
	}

	private StremioBrowseRepository repository(StremioBrowseGateway gateway) {
		return repository(gateway, 4, 100, 0);
	}

	private StremioBrowseRepository repository(StremioBrowseGateway gateway,
			int maxConcurrency, int maxPageItems, long debounceMillis) {
		var repository = new StremioBrowseRepository(gateway, workers, scheduler,
				maxConcurrency, maxPageItems, 20, debounceMillis);
		repositories.add(repository);
		return repository;
	}

	private static BrowseProvider provider(String id, boolean enabled, int position,
			CatalogCapability... catalogs) {
		var manifest = new StremioManifest("manifest." + id, "Provider " + id, "Description",
				"1.0.0", List.of("movie", "series"), PrefixConstraint.unrestricted(),
				List.of(ResourceCapability.inherited("catalog"),
						ResourceCapability.inherited("meta")),
				List.of(catalogs), ManifestBehaviorHints.NONE);
		return new BrowseProvider(id, "Provider " + id, manifest, enabled, position);
	}

	private static CatalogCapability searchable(String type, String id) {
		return catalog(type, id, "Search", List.of(extra("search", false, List.of())));
	}

	private static CatalogCapability catalog(String type, String id, String name,
			List<CatalogExtra> extras) {
		return new CatalogCapability(type, id, name, extras);
	}

	private static CatalogExtra extra(String name, boolean required, List<String> options) {
		return new CatalogExtra(name, required, options, 1);
	}

	private static CompletableFuture<BrowsePayload> payload(String json, boolean stale) {
		return CompletableFuture.completedFuture(
				new BrowsePayload(json.getBytes(StandardCharsets.UTF_8), stale));
	}

	private static CompletableFuture<BrowsePayload> failed(String message) {
		return failed(new AssertionError(message));
	}

	private static CompletableFuture<BrowsePayload> failed(Throwable error) {
		return CompletableFuture.failedFuture(error);
	}

	private static String catalogJson(String id, String type, String title) {
		return "{\"metas\":[" + meta(id, type, title) + "]}";
	}

	private static String meta(String id, String type, String title) {
		return "{\"id\":\"" + id + "\",\"type\":\"" + type +
				"\",\"name\":\"" + title + "\"}";
	}

	@SuppressWarnings("unchecked")
	private static <T> BrowseLoadState.Content<T> content(BrowseLoadState<T> state) {
		assertNotNull(state);
		assertTrue("Expected Content, got " + state.getClass().getSimpleName(),
				state instanceof BrowseLoadState.Content<?>);
		return (BrowseLoadState.Content<T>) state;
	}

	private static void assertFalseRetryable(BrowseLoadState<?> state) {
		assertTrue(state instanceof BrowseLoadState.Failure<?>);
		assertTrue(!((BrowseLoadState.Failure<?>) state).canRetry());
	}

	private static void assertFalseContains(String value, String text) {
		assertTrue("Unexpected text in log-safe value: " + text, !value.contains(text));
	}
}
