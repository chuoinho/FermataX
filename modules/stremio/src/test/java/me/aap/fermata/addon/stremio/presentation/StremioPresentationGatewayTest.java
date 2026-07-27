package me.aap.fermata.addon.stremio.presentation;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.junit.Test;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.browse.SearchResults;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.item.StremioStreamRequestFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptorFactory;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.model.CatalogExtra;
import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioFavoriteUpdate;
import me.aap.fermata.addon.stremio.session.StremioProgressSnapshot;
import me.aap.fermata.addon.stremio.session.StremioProviderState;
import me.aap.fermata.addon.stremio.session.StremioRestorePoint;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioSessionGateway;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;
import me.aap.fermata.addon.stremio.session.StremioVoiceCandidate;
import me.aap.utils.async.FutureSupplier;

public class StremioPresentationGatewayTest {
	@Test
	public void streamQualityOnlyUsesExplicitResolutionTokens() {
		assertEquals("1080p", StremioPresentationGateway.streamQuality(
				"Movie 1080P WEB-DL"));
		assertEquals("2160p", StremioPresentationGateway.streamQuality("Movie 4K"));
		assertEquals("", StremioPresentationGateway.streamQuality("Movie HD", "Provider"));
	}

	@Test
	public void homeIsBoundedFilmFirstAndProviderFree() {
		FakeItems items = new FakeItems();
		for (int i = 0; i < 8; i++) {
			CatalogDescriptor catalog = catalog("source-" + i, "movie", "catalog-" + i,
					"Shelf " + i, "Provider " + i, i);
			items.catalogs.add(catalog);
			List<BrowseMedia> media = new ArrayList<>();
			for (int j = 0; j < 14; j++) {
				media.add(media(catalog.route().sourceUuid(), "movie", "id-" + i + '-' + j,
						"Movie " + i + '-' + j));
			}
			items.pages.put(catalog.route(), new CatalogPage(catalog, null, 0, 14,
					false, media));
		}
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		StremioPresentationPage page = page(gateway.load(new StremioRoute.Home()));
		List<StremioUiModel.Section> sections = page.models().stream()
				.filter(StremioUiModel.Section.class::isInstance)
				.map(StremioUiModel.Section.class::cast).toList();
		assertEquals(6, sections.size());
		for (StremioUiModel.Section section : sections) {
			assertEquals(12, section.posters().size());
			for (StremioUiModel.Poster poster : section.posters()) {
				assertFalse(poster.title().contains("Provider"));
				assertFalse(poster.subtitle().contains("Provider"));
				assertTrue(page.selections().get(poster.stableKey())
						instanceof StremioSelection.Navigate);
			}
		}
	}

	@Test
	public void homeProviderFailureIsVisibleAndRetryable() {
		FakeItems items = new FakeItems();
		items.catalogs.add(catalog("source-a", "movie", "broken",
				"Broken", "Provider", 0));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		StremioPresentationPage page = page(gateway.load(new StremioRoute.Home()));

		assertTrue(page.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StateRow row) &&
						(row.kind() == StremioUiModel.StateKind.ERROR)));
		assertTrue(page.selections().values().stream().anyMatch(selection ->
				(selection instanceof StremioSelection.Command command) &&
						(command.action() == StremioUiModel.ActionKind.RETRY)));
	}

	@Test
	public void homeSeparatesMovieAndSeriesShelvesWithTheSameSemanticName() {
		FakeItems items = new FakeItems();
		CatalogDescriptor popularMovies = catalog("source-a", "movie", "popular-movie",
				"Popular Movies", "Provider A", 0);
		CatalogDescriptor popularSeries = catalog("source-a", "series", "popular-series",
				"Popular Series", "Provider A", 0);
		CatalogDescriptor featuredMovies = catalog("source-b", "movie", "featured-movie",
				"Featured", "Provider B", 1);
		CatalogDescriptor featuredSeries = catalog("source-b", "series", "featured-series",
				"Featured", "Provider B", 1);
		items.catalogs.addAll(List.of(popularMovies, popularSeries, featuredMovies, featuredSeries));
		items.pages.put(popularMovies.route(), page(popularMovies,
				media("source-a", "movie", "popular-movie", "Popular Movie")));
		items.pages.put(popularSeries.route(), page(popularSeries,
				media("source-a", "series", "popular-series", "Popular Series")));
		items.pages.put(featuredMovies.route(), page(featuredMovies,
				media("source-b", "movie", "featured-movie", "Featured Movie")));
		items.pages.put(featuredSeries.route(), page(featuredSeries,
				media("source-b", "series", "featured-series", "Featured Series")));

		StremioPresentationPage page = page(
				new StremioPresentationGateway(items, TEXT).load(new StremioRoute.Home()));
		List<StremioUiModel.Section> sections = page.models().stream()
				.filter(StremioUiModel.Section.class::isInstance)
				.map(StremioUiModel.Section.class::cast).toList();

		assertEquals(List.of("POPULAR_MOVIES", "POPULAR_SERIES",
				"FEATURED_MOVIES", "FEATURED_SERIES"), sections.stream()
				.map(StremioUiModel.Section::title).toList());
		assertEquals(List.of(1, 1, 1, 1), sections.stream()
				.map(section -> section.posters().size()).toList());
		assertTrue(sections.stream().allMatch(section -> section.seeAll() != null));
	}

	@Test
	public void homeLoadsNewMovieAndSeriesCatalogsWithRequiredYear() {
		FakeItems items = new FakeItems();
		CatalogExtra year = new CatalogExtra(
				"genre", true, List.of("2026", "2025"), 1);
		CatalogDescriptor newMovies = new CatalogDescriptor(
				new CatalogRoute("source-a", "movie", "year"), "Provider", "New",
				List.of("2026", "2025"), false, 0, 0, List.of(year));
		CatalogDescriptor newSeries = new CatalogDescriptor(
				new CatalogRoute("source-a", "series", "year"), "Provider", "New",
				List.of("2026", "2025"), false, 0, 1, List.of(year));
		items.catalogs.addAll(List.of(newMovies, newSeries));
		items.pages.put(newMovies.route(), page(newMovies,
				media("source-a", "movie", "new-movie", "New Movie")));
		items.pages.put(newSeries.route(), page(newSeries,
				media("source-a", "series", "new-series", "New Series")));

		StremioPresentationPage page = page(
				new StremioPresentationGateway(items, TEXT).load(new StremioRoute.Home()));
		List<StremioUiModel.Section> sections = page.models().stream()
				.filter(StremioUiModel.Section.class::isInstance)
				.map(StremioUiModel.Section.class::cast).toList();

		assertEquals(List.of("NEW_MOVIES", "NEW_SERIES"), sections.stream()
				.map(StremioUiModel.Section::title).toList());
		assertEquals(List.of("2026", "2026"), items.catalogRequests.stream()
				.map(CatalogRequest::genre).toList());
	}

	@Test
	public void homeKeepsNonYoutubeCustomCatalogTypes() {
		FakeItems items = new FakeItems();
		CatalogDescriptor channel = catalog("source-a", "channel", "live",
				"Live TV", "Provider", 0);
		CatalogDescriptor youtube = catalog("source-b", "youtube", "clips",
				"YouTube", "Provider", 1);
		items.catalogs.addAll(List.of(channel, youtube));
		items.pages.put(channel.route(), page(channel,
				media("source-a", "channel", "news", "News")));
		items.pages.put(youtube.route(), page(youtube,
				media("source-b", "youtube", "clip", "Clip")));

		StremioPresentationPage page = page(
				new StremioPresentationGateway(items, TEXT).load(new StremioRoute.Home()));
		List<StremioUiModel.Section> sections = page.models().stream()
				.filter(StremioUiModel.Section.class::isInstance)
				.map(StremioUiModel.Section.class::cast).toList();

		assertEquals(1, sections.size());
		assertEquals("News", sections.get(0).posters().get(0).title());
	}

	@Test
	public void homeOmitsCatalogsWhoseRequiredExtrasCannotBeSupplied() {
		FakeItems items = new FakeItems();
		CatalogDescriptor calendar = new CatalogDescriptor(
				new CatalogRoute("source", "series", "calendar"), "Provider", "Calendar",
				List.of(), false, 0, 0,
				List.of(new CatalogExtra("calendarVideosIds", true, List.of(), 1)));
		items.catalogs.add(calendar);

		StremioPresentationPage page = page(
				new StremioPresentationGateway(items, TEXT).load(new StremioRoute.Home()));

		assertTrue(page.models().stream().noneMatch(StremioUiModel.Section.class::isInstance));
		assertTrue(page.models().stream().anyMatch(model ->
				model instanceof StremioUiModel.StateRow));
	}

	@Test
	public void discoverUsesFullCatalogIdentityAcrossProviders() {
		FakeItems items = new FakeItems();
		CatalogDescriptor first = catalog("source-a", "movie", "popular",
				"Popular", "Provider A", 0);
		CatalogDescriptor second = catalog("source-b", "movie", "popular",
				"Popular", "Provider B", 1);
		items.catalogs.addAll(List.of(first, second));
		items.pages.put(first.route(), page(first, media("source-a", "movie", "a", "A movie")));
		items.pages.put(second.route(), page(second, media("source-b", "movie", "b", "B movie")));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		String secondKey = StremioItemIds.catalog(second);
		StremioPresentationPage page = page(gateway.load(
				new StremioRoute.Discover(secondKey, "", 0)));
		StremioUiModel.Poster poster = page.models().stream()
				.filter(StremioUiModel.Poster.class::isInstance)
				.map(StremioUiModel.Poster.class::cast).findFirst().orElseThrow();
		assertEquals("B movie", poster.title());
		assertFalse(StremioItemIds.catalog(first).equals(secondKey));
		StremioUiModel.Filter catalogFilter = page.models().stream()
				.filter(StremioUiModel.Filter.class::isInstance)
				.map(StremioUiModel.Filter.class::cast)
				.filter(filter -> filter.stableKey().equals("filter:catalog"))
				.findFirst().orElseThrow();
		assertEquals(List.of("Popular - Provider A", "Popular - Provider B"),
				catalogFilter.options().stream().map(StremioUiModel.Option::label).toList());
	}

	@Test
	public void discoverKeepsCatalogsThatHomeCannotInvoke() {
		FakeItems items = new FakeItems();
		CatalogDescriptor calendar = new CatalogDescriptor(
				new CatalogRoute("source-a", "series", "calendar"), "Calendar addon",
				"Calendar", List.of(), false, 0, 0,
				List.of(new CatalogExtra("calendarVideosIds", true, List.of(), 1)));
		items.catalogs.add(calendar);
		items.pages.put(calendar.route(), page(calendar,
				media("source-a", "series", "calendar-item", "Calendar item")));

		StremioPresentationPage page = page(new StremioPresentationGateway(items, TEXT).load(
				new StremioRoute.Discover(StremioItemIds.catalog(calendar), "", 0)));

		assertTrue(posters(page).isEmpty());
		assertTrue(page.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StateRow state) &&
						state.kind() == StremioUiModel.StateKind.WARNING));
		assertTrue(page.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.Filter filter) &&
						filter.stableKey().equals("filter:catalog")));
	}

	@Test
	public void discoverForwardsFiniteRequiredCatalogExtrasAndShowsFilter() {
		FakeItems items = new FakeItems();
		CatalogDescriptor catalog = new CatalogDescriptor(
				new CatalogRoute("source-a", "movie", "search"), "Search addon", "Search",
				List.of(), false, 0, 0,
				List.of(new CatalogExtra("search", true, List.of("alpha", "beta"), 1),
						new CatalogExtra("skip", false, List.of(), 1)));
		items.catalogs.add(catalog);
		items.pages.put(catalog.route(), page(catalog,
				media("source-a", "movie", "search-item", "Search item")));

		StremioPresentationPage page = page(new StremioPresentationGateway(items, TEXT).load(
				new StremioRoute.Discover(StremioItemIds.catalog(catalog), "", 0)));

		assertEquals(Map.of("search", List.of("alpha")),
				items.catalogRequests.get(0).extras());
		assertTrue(page.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.Filter filter) &&
						filter.label().equals("Search")));
	}

	@Test
	public void discoverNextPageAppendsAndRefreshDoesNotDiscardEarlierItems() {
		FakeItems items = new FakeItems();
		CatalogDescriptor catalog = catalog("source-a", "movie", "popular",
				"Popular", "Provider A", 0);
		items.catalogs.add(catalog);
		items.paged.put(new PageKey(catalog.route(), 0), new CatalogPage(catalog, null,
				0, 2, true, List.of(media("source-a", "movie", "a", "Movie A"))));
		items.paged.put(new PageKey(catalog.route(), 2), new CatalogPage(catalog, null,
				2, 4, false, List.of(media("source-a", "movie", "b", "Movie B"))));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);
		String key = StremioItemIds.catalog(catalog);

		StremioPresentationPage first = page(gateway.load(
				new StremioRoute.Discover(key, "", 0)));
		StremioPresentationPage second = page(gateway.load(
				new StremioRoute.Discover(key, "", 2)));
		StremioPresentationPage refreshed = page(gateway.load(
				new StremioRoute.Discover(key, "", 2)));

		assertEquals(1, posters(first).size());
		assertEquals(List.of("Movie A", "Movie B"), posters(second).stream()
				.map(StremioUiModel.Poster::title).toList());
		assertEquals(posters(first).get(0).stableKey(), posters(second).get(0).stableKey());
		assertEquals(List.of("Movie A", "Movie B"), posters(refreshed).stream()
				.map(StremioUiModel.Poster::title).toList());
	}

	@Test
	public void discoverStopsPaginationWhenProviderRepeatsTheSamePage() {
		FakeItems items = new FakeItems();
		CatalogDescriptor catalog = catalog("source-a", "movie", "popular",
				"Popular", "Provider A", 0);
		BrowseMedia repeated = media("source-a", "movie", "a", "Movie A");
		items.catalogs.add(catalog);
		items.paged.put(new PageKey(catalog.route(), 0), new CatalogPage(catalog, null,
				0, 1, true, List.of(repeated)));
		items.paged.put(new PageKey(catalog.route(), 1), new CatalogPage(catalog, null,
				1, 2, true, List.of(repeated)));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);
		String key = StremioItemIds.catalog(catalog);

		StremioPresentationPage first = page(gateway.load(
				new StremioRoute.Discover(key, "", 0)));
		StremioPresentationPage repeatedPage = page(gateway.load(
				new StremioRoute.Discover(key, "", 1)));

		assertTrue(first.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.Action action) &&
						action.kind() == StremioUiModel.ActionKind.NEXT_PAGE));
		assertEquals(List.of("Movie A"), posters(repeatedPage).stream()
				.map(StremioUiModel.Poster::title).toList());
		assertTrue(repeatedPage.models().stream().noneMatch(model ->
				(model instanceof StremioUiModel.Action action) &&
						action.kind() == StremioUiModel.ActionKind.NEXT_PAGE));
	}

	@Test
	public void posterKeepsBackdropAsArtworkFallback() {
		FakeItems items = new FakeItems();
		BrowseMedia movie = new BrowseMedia("source-a", "movie", "fallback", "Movie",
				"https://images.invalid/poster.jpg",
				"https://images.invalid/background.jpg", "Overview", "2026",
				null, List.of("Drama"), "en");
		items.search = new SearchResults("movie", List.of(movie));

		StremioPresentationPage page = page(new StremioPresentationGateway(items, TEXT)
				.load(new StremioRoute.Search("movie")));
		StremioUiModel.Poster poster = posters(page).get(0);

		assertEquals("https://images.invalid/poster.jpg", poster.artwork());
		assertEquals("https://images.invalid/background.jpg", poster.fallbackArtwork());
	}

	@Test
	public void movieDetailsExposeProvidersAndStreamsWithoutOpeningStreamsRoute() {
		FakeItems items = new FakeItems();
		BrowseMedia movie = media("source-a", "movie", "inline-streams", "Movie");
		items.search = new SearchResults("movie", List.of(movie));
		items.details.put(movie.scopedId(), new BrowseDetails(movie, List.of()));
		StreamProvider provider = new StreamProvider(
				"source-a", "org.test", "Provider A", 0, true);
		StreamAggregationRequest streamRequest = StremioStreamRequestFactory.create(movie, null);
		PlaybackDescriptor descriptor = new PlaybackDescriptorFactory((a, b, c, d) -> null)
				.create(streamRequest, provider, new StremioStream("1080p", "Channel A", null,
						new DirectStreamTarget("https://example.invalid/video.m3u8"),
						StreamBehaviorHints.EMPTY), 1_000L);
		items.streams = new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(provider,
						StreamAggregationResult.ProviderStatus.SUCCESS, List.of(descriptor))));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("movie")));
		StremioRoute.Details route = (StremioRoute.Details) ((StremioSelection.Navigate)
				search.selections().get(search.models().get(0).stableKey())).route();
		StremioPresentationPage details = page(gateway.load(route));
		StremioUiModel.DetailsHeader header = details.models().stream()
				.filter(StremioUiModel.DetailsHeader.class::isInstance)
				.map(StremioUiModel.DetailsHeader.class::cast).findFirst().orElseThrow();
		StremioUiModel.StreamChoice stream = details.models().stream()
				.filter(StremioUiModel.StreamChoice.class::isInstance)
				.map(StremioUiModel.StreamChoice.class::cast).findFirst().orElseThrow();
		StremioUiModel.Action subtitles = details.models().stream()
				.filter(StremioUiModel.Action.class::isInstance)
				.map(StremioUiModel.Action.class::cast)
				.filter(action -> action.kind() == StremioUiModel.ActionKind.SUBTITLES)
				.findFirst().orElseThrow();

		assertFalse(header.watchable());
		assertFalse(header.subtitlesSupported());
		assertTrue(details.models().indexOf(subtitles) < details.models().indexOf(stream));
		assertTrue(details.selections().get(subtitles.stableKey()) instanceof
				StremioSelection.Subtitles);
		assertTrue(gateway.subtitleTarget(subtitles.stableKey()) != null);
		assertTrue(details.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StreamGroup group) &&
						group.providerName().equals("Provider A")));
		assertTrue(details.selections().get(stream.stableKey()) instanceof StremioSelection.Play);
		assertTrue(gateway.playbackTarget(stream.stableKey()) != null);
	}

	@Test
	public void detailsOwnsSeasonAndEpisodeNavigationExplicitly() {
		FakeItems items = new FakeItems();
		BrowseMedia series = media("source-a", "series", "series-a", "Series A");
		items.search = new SearchResults("series", List.of(series));
		BrowseEpisode first = episode(series, 1, 1, "Pilot");
		BrowseEpisode second = episode(series, 2, 1, "Return");
		items.details.put(series.scopedId(), new BrowseDetails(series,
				List.of(new BrowseSeason(1, List.of(first)),
						new BrowseSeason(2, List.of(second)))));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("series")));
		StremioUiModel.Poster poster = (StremioUiModel.Poster) search.models().get(0);
		StremioRoute.Details detailsRoute = (StremioRoute.Details)
				((StremioSelection.Navigate) search.selections()
						.get(poster.stableKey())).route();
		StremioPresentationPage details = page(gateway.load(
				new StremioRoute.Details(detailsRoute.stableId(), 2)));

		StremioUiModel.Filter seasons = details.models().stream()
				.filter(StremioUiModel.Filter.class::isInstance)
				.map(StremioUiModel.Filter.class::cast).findFirst().orElseThrow();
		assertTrue(seasons.selectedKey().endsWith(":2"));
		StremioUiModel.Episode episode = details.models().stream()
				.filter(StremioUiModel.Episode.class::isInstance)
				.map(StremioUiModel.Episode.class::cast).findFirst().orElseThrow();
		assertEquals("Return", episode.title());
		assertTrue(((StremioSelection.Navigate) details.selections()
				.get(episode.stableKey())).route() instanceof StremioRoute.Streams);
	}

	@Test
	public void seriesWithoutEpisodesShowsEmptyState() {
		FakeItems items = new FakeItems();
		BrowseMedia series = media("source-a", "series", "empty-series", "Empty series");
		items.search = new SearchResults("empty", List.of(series));
		items.details.put(series.scopedId(), new BrowseDetails(series, List.of()));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("empty")));
		StremioRoute.Details route = (StremioRoute.Details) ((StremioSelection.Navigate)
				search.selections().get(search.models().get(0).stableKey())).route();
		StremioPresentationPage details = page(gateway.load(route));

		assertTrue(details.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StateRow row) &&
						(row.kind() == StremioUiModel.StateKind.EMPTY)));
	}

	@Test
	public void seriesDetailsUsesUnifiedFavoriteIdentity() {
		FakeItems items = new FakeItems();
		BrowseMedia series = media("source-a", "series", "series-favorite", "Series Favorite");
		BrowseEpisode episode = episode(series, 1, 1, "Pilot");
		items.search = new SearchResults("series", List.of(series));
		items.details.put(series.scopedId(), new BrowseDetails(series,
				List.of(new BrowseSeason(1, List.of(episode)))));
		String persistentId = StremioStreamRequestFactory.create(series, null)
				.identity().videoKey();
		StremioSessionItem sessionItem = new StremioSessionItem(persistentId,
				"stremio:content:series-favorite", "source-a", series.title(), "", null,
				-1L, "stremio:root", null, -1, -1);
		StremioPresentationGateway gateway = new StremioPresentationGateway(items,
				new StremioSessionCoordinator(new FakeSessions(sessionItem)), TEXT);

		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("series")));
		StremioRoute.Details route = (StremioRoute.Details) ((StremioSelection.Navigate)
				search.selections().get(search.models().get(0).stableKey())).route();
		StremioPresentationPage details = page(gateway.load(route));
		StremioUiModel.DetailsHeader header = details.models().stream()
				.filter(StremioUiModel.DetailsHeader.class::isInstance)
				.map(StremioUiModel.DetailsHeader.class::cast).findFirst().orElseThrow();

		assertTrue(header.favoriteSupported());
		assertEquals(persistentId, gateway.favoriteTarget(header.stableKey()).stableId());
	}

	@Test
	public void favoritePreparationFailureRemainsVisibleAndRetryable() {
		FakeItems items = new FakeItems();
		items.failPersistentPreparation = true;
		BrowseMedia movie = media("source-a", "movie", "favorite-failure", "Movie");
		items.search = new SearchResults("movie", List.of(movie));
		items.details.put(movie.scopedId(), new BrowseDetails(movie, List.of()));
		String persistentId = StremioStreamRequestFactory.create(movie, null)
				.identity().videoKey();
		StremioSessionItem sessionItem = new StremioSessionItem(persistentId,
				"stremio:content:favorite-failure", "source-a", movie.title(), "", null,
				-1L, "stremio:root", null, -1, -1);
		StremioPresentationGateway gateway = new StremioPresentationGateway(items,
				new StremioSessionCoordinator(new FakeSessions(sessionItem)), TEXT);

		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("movie")));
		StremioRoute.Details route = (StremioRoute.Details) ((StremioSelection.Navigate)
				search.selections().get(search.models().get(0).stableKey())).route();
		StremioPresentationPage details = page(gateway.load(route));

		assertTrue(details.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StateRow row) &&
						(row.kind() == StremioUiModel.StateKind.WARNING)));
		assertTrue(details.selections().values().stream().anyMatch(selection ->
				(selection instanceof StremioSelection.Command command) &&
						(command.action() == StremioUiModel.ActionKind.RETRY)));
	}

	@Test
	public void pendingEmptyStreamsWaitForTerminalProviderResult() {
		FakeItems items = new FakeItems();
		BrowseMedia movie = media("source-a", "movie", "movie-a", "Movie A");
		items.search = new SearchResults("movie", List.of(movie));
		StreamProvider provider = new StreamProvider(
				"source-a", "org.test", "Provider", 0, true);
		items.streams = new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(provider,
						StreamAggregationResult.ProviderStatus.PENDING, List.of())));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);

		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("movie")));
		StremioRoute.Details details = (StremioRoute.Details) ((StremioSelection.Navigate)
				search.selections().get(search.models().get(0).stableKey())).route();
		StremioPresenter.Request request = gateway.load(new StremioRoute.Streams(details.stableId()));
		List<StremioPresentationPage> updates = new ArrayList<>();
		request.onUpdate(updates::add);
		assertFalse(request.result().toCompletableFuture().isDone());
		assertEquals(1, updates.size());
		assertTrue(updates.get(0).models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StreamGroup group) &&
						(group.state() == StremioUiModel.ProviderState.LOADING)));

		items.lateStreams.accept(new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(provider,
						StreamAggregationResult.ProviderStatus.FAILED, List.of()))), null);
		StremioPresentationPage streams = page(request);
		assertTrue(streams.models().stream().anyMatch(model ->
				(model instanceof StremioUiModel.StateRow row) &&
						(row.kind() == StremioUiModel.StateKind.EMPTY)));
	}

	@Test
	public void terminalStreamFailureCompletesInsteadOfSpinningForever() {
		FakeItems items = new FakeItems();
		BrowseMedia movie = media("source-a", "movie", "movie-failure", "Movie");
		items.search = new SearchResults("movie", List.of(movie));
		StreamProvider provider = new StreamProvider(
				"source-a", "org.test", "Provider", 0, true);
		items.streams = new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(provider,
						StreamAggregationResult.ProviderStatus.PENDING, List.of())));
		StremioPresentationGateway gateway = new StremioPresentationGateway(items, TEXT);
		StremioPresentationPage search = page(gateway.load(new StremioRoute.Search("movie")));
		StremioRoute.Details details = (StremioRoute.Details) ((StremioSelection.Navigate)
				search.selections().get(search.models().get(0).stableKey())).route();
		StremioPresenter.Request request = gateway.load(new StremioRoute.Streams(details.stableId()));
		assertFalse(request.result().toCompletableFuture().isDone());

		items.lateStreams.accept(null, new IllegalStateException("late persistence failed"));

		assertTrue(request.result().toCompletableFuture().isCompletedExceptionally());
		assertThrows(CompletionException.class,
				() -> request.result().toCompletableFuture().join());
	}

	private static StremioPresentationPage page(StremioPresenter.Request request) {
		return request.result().toCompletableFuture().join();
	}

	private static List<StremioUiModel.Poster> posters(StremioPresentationPage page) {
		return page.models().stream().filter(StremioUiModel.Poster.class::isInstance)
				.map(StremioUiModel.Poster.class::cast).toList();
	}

	private static CatalogPage page(CatalogDescriptor catalog, BrowseMedia media) {
		return new CatalogPage(catalog, null, 0, 1, false, List.of(media));
	}

	private static CatalogDescriptor catalog(String source, String type, String id,
			String name, String provider, int position) {
		return new CatalogDescriptor(new CatalogRoute(source, type, id), provider, name,
				List.of("Action", "Drama"), true, position, 0);
	}

	private static BrowseMedia media(String source, String type, String id, String title) {
		return new BrowseMedia(source, type, id, title,
				"https://art.invalid/" + id + ".jpg", null, "Overview", "2026",
				null, List.of("Drama"), "en");
	}

	private static BrowseEpisode episode(
			BrowseMedia series, int season, int episode, String title) {
		return new BrowseEpisode(series.sourceUuid(), series.type(), series.id(),
				"video-" + season + '-' + episode, title, season, episode,
				"2026", null, "Overview", null);
	}

	private static final StremioPresentationText TEXT = new StremioPresentationText() {
		@Override
		public String action(StremioUiModel.ActionKind kind) {
			return kind.name();
		}

		@Override
		public String label(Label kind) {
			return kind.name();
		}
	};

	private static final class FakeItems implements StremioItemGateway {
		private boolean failPersistentPreparation;
		private final List<CatalogDescriptor> catalogs = new ArrayList<>();
		private final Map<CatalogRoute, CatalogPage> pages = new LinkedHashMap<>();
		private final List<CatalogRequest> catalogRequests = new ArrayList<>();
		private final Map<PageKey, CatalogPage> paged = new LinkedHashMap<>();
		private final Map<String, BrowseDetails> details = new LinkedHashMap<>();
		private SearchResults search = new SearchResults("", List.of());
		private StreamAggregationResult streams = new StreamAggregationResult(List.of());
		private BiConsumer<StreamAggregationResult, Throwable> lateStreams;

		@Override
		public FutureSupplier<List<BrowseProvider>> providers() {
			return completed(List.of());
		}

		@Override
		public FutureSupplier<List<CatalogDescriptor>> catalogs() {
			return completed(List.copyOf(catalogs));
		}

		@Override
		public FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid) {
			return completed(catalogs.stream().filter(catalog ->
					catalog.route().sourceUuid().equals(sourceUuid)).toList());
		}

		@Override
		public FutureSupplier<CatalogPage> catalog(
				CatalogRoute route, String genre, int skip) {
			return catalog(route, genre, skip, Map.of());
		}

		@Override
		public FutureSupplier<CatalogPage> catalog(CatalogRoute route, String genre, int skip,
				Map<String, List<String>> extras) {
			catalogRequests.add(new CatalogRequest(route, genre, skip, extras));
			CatalogPage page = paged.getOrDefault(new PageKey(route, skip), pages.get(route));
			return (page == null) ? failed(new IllegalStateException("missing page")) :
					completed(page);
		}

		@Override
		public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
			BrowseDetails value = details.get(media.scopedId());
			return (value == null) ? failed(new IllegalStateException("missing details")) :
					completed(value);
		}

		@Override
		public FutureSupplier<SearchResults> search(String query) {
			return completed(search);
		}

		@Override
		public FutureSupplier<String> preparePersistentItem(
				BrowseMedia media, BrowseEpisode episode) {
			if (failPersistentPreparation) {
				return failed(new IllegalStateException("persistent preparation failed"));
			}
			return completed(StremioStreamRequestFactory.create(media, episode)
					.identity().videoKey());
		}

		@Override
		public FutureSupplier<StreamAggregationResult> streams(
				StreamAggregationRequest request) {
			return completed(streams);
		}

		@Override
		public FutureSupplier<StreamAggregationResult> streams(String sourceUuid,
				StreamAggregationRequest request,
				BiConsumer<StreamAggregationResult, Throwable> lateResults) {
			lateStreams = lateResults;
			return completed(streams);
		}

		@Override
		public FutureSupplier<PlaybackDescriptor> resolve(
				PlaybackDescriptor.DescriptorRefreshRequest request) {
			return failed(new UnsupportedOperationException());
		}

		@Override
		public FutureSupplier<Void> saveProgress(
				StremioPlaybackIdentity identity, long position, boolean completed) {
			return completedVoid();
		}
	}

	private record PageKey(CatalogRoute route, int skip) {
	}

	private record CatalogRequest(CatalogRoute route, String genre, int skip,
			Map<String, List<String>> extras) {
	}

	private static final class FakeSessions implements StremioSessionGateway {
		private final StremioSessionItem item;

		private FakeSessions(StremioSessionItem item) {
			this.item = item;
		}

		@Override
		public CompletionStage<List<StremioContinueEntry>> loadContinue(int limit) {
			return CompletableFuture.completedFuture(List.of());
		}

		@Override
		public CompletionStage<Map<String, StremioSessionItem>> loadItemsBatch(
				Collection<String> stableIds) {
			return CompletableFuture.completedFuture(stableIds.contains(item.stableId()) ?
					Map.of(item.stableId(), item) : Map.of());
		}

		@Override
		public CompletionStage<Map<String, me.aap.fermata.addon.stremio.session.StremioProgressState>>
				loadProgressBatch(Collection<String> stableIds) {
			return CompletableFuture.completedFuture(Map.of());
		}

		@Override
		public CompletionStage<Map<String, Boolean>> loadFavoriteStates(
				Collection<String> stableIds) {
			return CompletableFuture.completedFuture(Map.of(item.stableId(), false));
		}

		@Override
		public CompletionStage<StremioSessionItem> loadItem(String stableId) {
			return CompletableFuture.completedFuture(
					item.stableId().equals(stableId) ? item : null);
		}

		@Override
		public CompletionStage<StremioProviderState> getProviderState(String sourceUuid) {
			return CompletableFuture.completedFuture(StremioProviderState.ENABLED);
		}

		@Override
		public CompletionStage<Void> synchronizeFavorite(StremioFavoriteUpdate update) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<Void> writeProgress(StremioProgressSnapshot snapshot) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<Void> saveRestorePoint(StremioRestorePoint restorePoint) {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<StremioRestorePoint> loadRestorePoint() {
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<List<StremioSessionItem>> loadEpisodeQueue(String episodeQueueId) {
			return CompletableFuture.completedFuture(List.of());
		}

		@Override
		public CompletionStage<List<StremioVoiceCandidate>> search(
				String normalizedQuery, Locale locale, int limit) {
			return CompletableFuture.completedFuture(List.of());
		}
	}
}
