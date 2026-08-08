package me.aap.fermata.addon.stremio.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static me.aap.fermata.media.net.PlaybackRequestProfile.EngineCapability.P2P_STREAMING;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.browse.SearchResults;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptorFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackHeaderRegistry.HeaderStore;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.model.ManifestBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.model.PrefixConstraint;
import me.aap.fermata.addon.stremio.protocol.model.ResourceCapability;
import me.aap.fermata.addon.stremio.protocol.model.StremioManifest;
import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.InfoHashStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ProxyHeaders;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PlaybackProgressItem;
import me.aap.fermata.media.lib.PlayableItemResolver;
import me.aap.fermata.media.net.PlaybackHeaderResolver;
import me.aap.fermata.media.net.PlaybackRequestProfile;
import me.aap.fermata.media.net.PlaybackRequestProfile.HeaderReference;
import me.aap.fermata.media.net.PlaybackRequestValidationException;
import me.aap.fermata.media.net.RemotePlaybackRequest;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StremioItemHierarchyTest {
	private static final String SOURCE = "source-uuid-private";
	private static final String SERIES_ID = "series-private";
	private static final CatalogDescriptor CATALOG = new CatalogDescriptor(
			new CatalogRoute(SOURCE, "series", "featured-private"),
			"Fixture Provider", "Featured", List.of("Drama"), true, 0, 0);
	private static final BrowseMedia SERIES = new BrowseMedia(SOURCE, "series",
			"series-private", "Exact Series Title", "https://images.invalid/poster.jpg",
			"https://images.invalid/background.jpg", "Description", "2026",
			new StremioDuration("45m", 2_700_000L), List.of("Drama"), "en");
	private static final BrowseEpisode EPISODE_10 = episode("video-private-10", "Episode Ten", 1, 10);
	private static final BrowseEpisode EPISODE_2 = episode("video-private-2", "Episode Two", 1, 2);
	private static final BrowseEpisode EPISODE_S2 = episode("video-private-s2", "Season Two", 2, 1);

	@Test
	public void buildsProviderToPlayableHierarchyWithExactParentChain() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		gateway.catalogs = List.of(CATALOG);
		gateway.page = new CatalogPage(CATALOG, "Drama", 0, 1, false, List.of(SERIES));
		gateway.details = new BrowseDetails(SERIES, List.of(
				new BrowseSeason(2, List.of(EPISODE_S2)),
				new BrowseSeason(1, List.of(EPISODE_10, EPISODE_2))));
		BrowseProvider provider = provider();

		StremioProviderItem providerItem = new StremioProviderItem(root, root, gateway, provider);
		StremioCatalogItem catalog = only(providerItem, StremioCatalogItem.class);
		assertEquals("Series - Fixture Provider",
				catalog.getMediaDescription().get().getSubtitle().toString());
		StremioGenreItem genre = only(catalog, StremioGenreItem.class);
		StremioPageItem page = only(genre, StremioPageItem.class);
		StremioMetaItem meta = only(page, StremioMetaItem.class);
		List<Item> seasons = meta.getUnsortedChildren().get();
		assertEquals(List.of(1, 2), seasons.stream()
				.map(item -> ((StremioSeasonItem) item).seasonNumber()).toList());
		StremioSeasonItem season = (StremioSeasonItem) seasons.get(0);
		List<Item> episodes = season.getUnsortedChildren().get();
		assertEquals(List.of(2, 10), episodes.stream()
				.map(item -> ((StremioEpisodeItem) item).episode().episode()).toList());
		StremioEpisodeItem episode = (StremioEpisodeItem) episodes.get(0);
		StremioStreamPickerItem picker = only(episode, StremioStreamPickerItem.class);
		StremioDirectPlayableItem playable = only(picker, StremioDirectPlayableItem.class);

		assertSame(root, providerItem.getParent());
		assertSame(providerItem, catalog.getParent());
		assertSame(catalog, genre.getParent());
		assertSame(genre, page.getParent());
		assertSame(page, meta.getParent());
		assertSame(meta, season.getParent());
		assertSame(season, episode.getParent());
		assertSame(episode, picker.getParent());
		assertSame(picker, playable.getParent());
		assertSame(root, playable.getRoot());
		assertEquals("Stremio", playable.getRoot().getId());
		assertEquals("Episode Two", playable.getName());
	}

	@Test
	public void idsAreStableProviderScopedShortAndContainNoRawIdentity() {
		BrowseMedia otherProvider = new BrowseMedia("different-source", SERIES.type(), SERIES.id(),
				SERIES.title(), SERIES.poster(), SERIES.background(), SERIES.description(),
				SERIES.releaseInfo(), SERIES.duration(), SERIES.genres(), SERIES.language());
		String first = StremioItemIds.meta(SERIES);
		String repeated = StremioItemIds.meta(SERIES);
		String scoped = StremioItemIds.meta(otherProvider);

		assertEquals(first, repeated);
		assertFalse(first.equals(scoped));
		assertTrue(StremioItemIds.isStremioId(first));
		assertTrue(first.length() <= 40);
		for (String secret : List.of(SOURCE, SERIES.id(), "different-source",
				"featured-private", "video-private")) {
			assertFalse(first.contains(secret));
			assertFalse(StremioItemIds.catalog(CATALOG).contains(secret));
			assertFalse(StremioItemIds.episode(EPISODE_2).contains(secret));
		}
	}

	@Test
	public void preservesExactTitleArtworkAndDuration() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		StreamAggregationRequest request = request(EPISODE_2);
		PlaybackDescriptor descriptor = descriptor(request,
				"https://cdn.invalid/exact.m3u8?private=token", 1_000L, 60_000L);
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, descriptor, 1234L, () -> 2_000L);

		MediaMetadataCompat metadata = item.getMediaData().get();
		assertEquals("Episode Two", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
		assertEquals("https://images.invalid/video-private-2.jpg",
				metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI));
		assertEquals(1_800_000L,
				metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
		assertEquals("Episode Two", item.getMediaDescription().get().getTitle());
		assertEquals("https://images.invalid/video-private-2.jpg",
				item.getMediaDescription().get().getIconUri().toString());
		assertEquals(1_800_000L, item.getDuration().get().longValue());
		assertTrue(item.isSeekable());
		assertEquals(1234L, item.getResumePosition());
		assertEquals(PlaybackProgressItem.ProgressMode.MANAGED,
				item.getPlaybackProgressMode());
		assertFalse(item.supportsCombinedSubtitles());
	}

	@Test
	public void torrentItemsAdvertiseP2pBeforeEngineSelection() {
		ExtRoot root = new ExtRoot("Stremio", null);
		StreamAggregationRequest request = request(EPISODE_2);
		PlaybackDescriptor descriptor = new PlaybackDescriptorFactory((a, b, c, d) -> null)
				.create(request, new StreamProvider(
						SOURCE, "torrent.fixture", "Torrent fixture", 0, true),
						new StremioStream("P2P", "1080p", null,
								new InfoHashStreamTarget(
										"0123456789abcdef0123456789abcdef01234567", 0, List.of()),
								new StreamBehaviorHints(false, null, null, null,
										ProxyHeaders.EMPTY)), 1_000L);

		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, new FakeGateway(), descriptor, 0);

		assertTrue(item.getPlaybackRequestProfile().getRequiredEngineCapabilities()
				.contains(P2P_STREAMING));
	}

	@Test
	public void signedArtworkIsExcludedFromMediaSessionMetadata() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		StreamAggregationRequest request = new StreamAggregationRequest(
				StremioPlaybackIdentity.scoped(SOURCE, "series", SERIES_ID, "signed-video"),
				"series", SERIES_ID, "signed-video",
				new me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata(
						"Signed artwork", "https://images.invalid/poster.jpg?token=private",
						60_000));
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(root, gateway,
				descriptor(request, "https://cdn.invalid/video.mp4", 1_000, 60_000), 0);

		MediaMetadataCompat metadata = item.getMediaData().get();
		assertEquals(null, metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI));
		assertEquals(null, item.getMediaDescription().get().getIconUri());
	}

	@Test
	public void searchItemUsesGatewayAndBuildsNativeMetaResults() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		gateway.searchResults = new SearchResults("exact query", List.of(SERIES));
		StremioSearchItem search = new StremioSearchItem(root, root, gateway, "exact query");

		List<Item> children = search.getUnsortedChildren().get();

		assertEquals(1, gateway.searchCalls.get());
		assertEquals(1, children.size());
		assertTrue(children.get(0) instanceof StremioMetaItem);
		assertEquals("Exact Series Title", children.get(0).getName());
	}

	@Test
	public void expiredChoiceCannotPlayAndMustResolveFromDurableIdentity() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		AtomicLong clock = new AtomicLong(2_001L);
		StreamAggregationRequest request = request(EPISODE_2);
		PlaybackDescriptor expired = descriptor(request,
				"https://cdn.invalid/old.m3u8?secret=old", 1_000L, 1_000L);
		PlaybackDescriptor fresh = descriptor(request,
				"https://cdn.invalid/new.m3u8?secret=new", 2_001L, 10_000L);
		gateway.resolved = fresh;
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, expired, 0, clock::get);

		assertThrows(IllegalStateException.class, item::getLocation);
		assertSame(fresh, item.resolveForPlayback().get());
		assertEquals(1, gateway.resolveCalls.get());
		assertEquals(1, gateway.validateCalls.get());
		assertEquals("https://cdn.invalid/new.m3u8?secret=new",
				item.prepareRemotePlayback().get().getLocation().toString());
		assertEquals(1, gateway.resolveCalls.get());
		assertEquals(2, gateway.validateCalls.get());
		assertEquals("https://cdn.invalid/new.m3u8?secret=new", item.getLocation().toString());
		assertEquals(expired.identity(), gateway.lastRefresh.identity());
		assertFalse(item.getId().contains("cdn.invalid"));
		assertFalse(item.toString().contains("secret"));
	}

	@Test
	public void proxyHeadersResolveOnlyAtPlaybackWithoutLeakingIntoIdentity() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		AtomicLong headerClock = new AtomicLong(2_000L);
		HeaderStore store = new HeaderStore(headerClock::get);
		gateway.headerResolver = store;
		String secret = "Bearer stremio-private-header-value";
		StreamAggregationRequest request = request(EPISODE_2);
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(60_000L, store);
		PlaybackDescriptor descriptor = factory.create(request,
				new StreamProvider(SOURCE, "fixture.provider", "Fixture Provider", 0, true),
				new StremioStream("HD", "Fixture stream", null,
						new DirectStreamTarget("https://cdn.invalid/protected.m3u8"),
						new StreamBehaviorHints(false, null, null, null,
								new ProxyHeaders(Map.of(
										"Authorization", secret,
										"User-Agent", "FermataX-Stremio-Test"), Map.of()))),
				1_000L);
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, descriptor, 0, () -> 2_000L);

		RemotePlaybackRequest remote = item.prepareRemotePlayback().get();
		Map<String, String> headers = remote.resolveHeaders(2_000L,
				EnumSet.allOf(PlaybackRequestProfile.EngineCapability.class));

		assertEquals(secret, headers.get("Authorization"));
		assertEquals("FermataX-Stremio-Test", headers.get("User-Agent"));
		assertThrows(UnsupportedOperationException.class,
				() -> headers.put("Cookie", "must-not-mutate"));
		for (String rendered : List.of(descriptor.descriptorId(), item.getId(),
				item.toString(), descriptor.toString(), remote.toString(),
				descriptor.requestProfile().toString(),
				descriptor.requestProfile().getHeaderReference().toString())) {
			assertFalse(rendered.contains(secret));
			assertFalse(rendered.contains("FermataX-Stremio-Test"));
		}

		HeaderReference expired = store.register(SOURCE, "expired",
				Map.of("Authorization", secret), 3_000L);
		headerClock.set(3_000L);
		PlaybackRequestValidationException expiredError = assertThrows(
				PlaybackRequestValidationException.class, () -> store.resolve(expired));
		assertFalse(expiredError.getMessage().contains(secret));
		assertThrows(PlaybackRequestValidationException.class,
				() -> store.resolve(HeaderReference.of("missing-reference")));
		store.close();
		assertThrows(PlaybackRequestValidationException.class,
				() -> store.resolve(descriptor.requestProfile().getHeaderReference()));
		assertThrows(IllegalStateException.class, () -> store.register(SOURCE, "closed",
				Map.of("Authorization", secret), Long.MAX_VALUE));
	}

	@Test
	public void managedProgressIsNormalizedAndDelegatedByIdentity() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		PlaybackDescriptor descriptor = descriptor(request(EPISODE_2),
				"https://cdn.invalid/video.mp4", 1_000L, 60_000L);
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, descriptor, 0, () -> 2_000L);

		item.savePlaybackProgress(-20, false).get();
		assertEquals(0L, gateway.savedPosition);
		item.savePlaybackProgress(42_000, false).get();
		assertEquals(42_000L, gateway.savedPosition);
		item.savePlaybackProgress(99_000, true).get();
		assertEquals(0L, gateway.savedPosition);
		assertTrue(gateway.savedCompleted);
		assertEquals(descriptor.identity(), gateway.savedIdentity);
	}

	@Test
	public void nativeItemUsesStremioAdjacencyButCollectionExportUsesCollectionOrder()
			throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		CollectionRoot collection = new CollectionRoot();
		FakeGateway gateway = new FakeGateway();
		StremioDirectPlayableItem current = new StremioDirectPlayableItem(root, gateway,
				descriptor(request(EPISODE_2), "https://cdn.invalid/current.mp4",
						1_000L, 60_000L), 0L);
		StremioDirectPlayableItem adjacent = new StremioDirectPlayableItem(root, gateway,
				descriptor(request(EPISODE_10), "https://cdn.invalid/next.mp4",
						1_000L, 60_000L), 0L);
		gateway.adjacent = adjacent;
		PlayableItem exported = current.export("collection:stremio", collection);
		PlayableItem other = new ExtPlayable("collection:other", collection,
				current.getResource());
		collection.children = List.of(exported, other);

		assertSame(adjacent, current.getNextPlayable().get());
		assertSame(adjacent, current.getPrevPlayable().get());
		assertSame(collection, exported.getParent());
		assertSame(other, exported.getNextPlayable().get());
		assertSame(current, PlayableItemResolver.unwrap(exported));
		assertEquals(2, gateway.adjacentCalls.get());
	}

	@Test
	public void streamPickerRefreshesLateBatchOnceWithoutSecondNetworkRequest()
			throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		StremioStreamPickerItem picker = new StremioStreamPickerItem(
				root, root, gateway, SERIES, EPISODE_2);
		StreamAggregationRequest request = picker.request();
		PlaybackDescriptor visible = descriptor(request,
				"https://cdn.invalid/visible.mp4", 1_000L, 60_000L);
		PlaybackDescriptor late = new PlaybackDescriptorFactory(60_000L,
				(a, b, c, d) -> null).create(request,
				new StreamProvider("late-source", "fixture.late", "Late Provider", 1, true),
				new StremioStream("Late", "Late stream", null,
						new DirectStreamTarget("https://cdn.invalid/late.mp4"),
						StreamBehaviorHints.EMPTY), 1_000L);
		gateway.initialStreams = aggregation(visible);
		gateway.finalStreams = new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(
						visible.providerSnapshot(), StreamAggregationResult.ProviderStatus.SUCCESS,
						List.of(visible)),
				new StreamAggregationResult.ProviderGroup(
						late.providerSnapshot(), StreamAggregationResult.ProviderStatus.SUCCESS,
						List.of(late))), List.of(visible, late));
		AtomicInteger changes = new AtomicInteger();
		picker.addChangeListener(item -> changes.incrementAndGet());

		List<Item> initial = picker.getUnsortedChildren().get();
		assertEquals(1, initial.size());
		assertEquals(1, gateway.streamCalls.get());
		gateway.publishLateStreams();
		assertEquals(1, changes.get());

		List<Item> completed = picker.getUnsortedChildren().get();
		assertEquals(2, completed.size());
		assertEquals(initial.get(0).getId(), completed.get(0).getId());
		assertEquals(1, gateway.streamCalls.get());
		gateway.publishLateStreams();
		assertEquals(1, changes.get());
		assertEquals(1, gateway.streamCalls.get());
	}

	@Test
	public void pendingEmptyPickerDoesNotClaimTerminalNoStreams() throws Exception {
		ExtRoot root = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		StremioStreamPickerItem picker = new StremioStreamPickerItem(
				root, root, gateway, SERIES, EPISODE_2);
		StreamProvider provider = new StreamProvider(
				SOURCE, "fixture.provider", "Fixture Provider", 0, true);
		gateway.initialStreams = new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(provider,
						StreamAggregationResult.ProviderStatus.PENDING, List.of())));
		gateway.finalStreams = new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(provider,
						StreamAggregationResult.ProviderStatus.SUCCESS, List.of())));
		AtomicInteger changes = new AtomicInteger();
		picker.addChangeListener(item -> changes.incrementAndGet());

		assertTrue(picker.getUnsortedChildren().get().get(0) instanceof
				StremioLoadingStreamsItem);
		gateway.publishLateStreams();
		assertEquals(1, changes.get());
		assertTrue(picker.getUnsortedChildren().get().get(0) instanceof StremioNoStreamsItem);
		assertEquals(1, gateway.streamCalls.get());
	}

	private static StreamAggregationResult aggregation(PlaybackDescriptor descriptor) {
		return new StreamAggregationResult(List.of(
				new StreamAggregationResult.ProviderGroup(descriptor.providerSnapshot(),
						StreamAggregationResult.ProviderStatus.SUCCESS, List.of(descriptor))));
	}

	private static BrowseEpisode episode(String id, String title, int season, int episode) {
		return new BrowseEpisode(SOURCE, "series", SERIES_ID, id, title, season, episode,
				"2026", "https://images.invalid/" + id + ".jpg", "Overview",
				new StremioDuration("30m", 1_800_000L));
	}

	private static BrowseProvider provider() {
		StremioManifest manifest = new StremioManifest("fixture.provider", "Fixture Provider",
				"Fixture", "1.0.0", List.of("series"), PrefixConstraint.unrestricted(),
				List.of(ResourceCapability.inherited("catalog"),
						ResourceCapability.inherited("meta"),
						ResourceCapability.inherited("stream")),
				List.of(), ManifestBehaviorHints.NONE);
		return new BrowseProvider(SOURCE, "Fixture Provider", manifest, true, 0);
	}

	private static StreamAggregationRequest request(BrowseEpisode episode) {
		return new StreamAggregationRequest(StremioPlaybackIdentity.scoped(SOURCE, "series",
				SERIES_ID, episode.videoId()), "series", SERIES_ID, episode.videoId(),
				new me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata(
						episode.title(), episode.thumbnail(), episode.duration().milliseconds()));
	}

	private static PlaybackDescriptor descriptor(StreamAggregationRequest request, String target,
			long createdAt, long ttl) {
		PlaybackDescriptorFactory factory = new PlaybackDescriptorFactory(ttl,
				(a, b, c, d) -> null);
		return factory.create(request,
				new StreamProvider(SOURCE, "fixture.provider", "Fixture Provider", 0, true),
				new StremioStream("HD", "Fixture stream", null,
						new DirectStreamTarget(target), StreamBehaviorHints.EMPTY), createdAt);
	}

	private static <T extends Item> T only(BrowsableItem parent, Class<T> type) throws Exception {
		List<Item> children = parent.getUnsortedChildren().get();
		assertEquals(1, children.size());
		assertTrue(type.isInstance(children.get(0)));
		return type.cast(children.get(0));
	}

	private static final class CollectionRoot extends ExtRoot {
		private List<Item> children = List.of();

		private CollectionRoot() {
			super("Collection", null);
		}

		@NonNull
		@Override
		public FutureSupplier<List<Item>> getChildren() {
			return me.aap.utils.async.Completed.completed(children);
		}

		@Override
		public boolean getShufflePref() {
			return false;
		}

		@Override
		public boolean getRepeatPref() {
			return false;
		}

		@Override
		public String getRepeatItemPref() {
			return null;
		}
	}

	private static final class FakeGateway implements StremioItemGateway, PlaybackHeaderResolver {
		private List<CatalogDescriptor> catalogs = List.of();
		private CatalogPage page;
		private BrowseDetails details;
		private PlaybackDescriptor resolved;
		private SearchResults searchResults;
		private final AtomicInteger searchCalls = new AtomicInteger();
		private final AtomicInteger resolveCalls = new AtomicInteger();
		private final AtomicInteger validateCalls = new AtomicInteger();
		private final AtomicInteger streamCalls = new AtomicInteger();
		private final AtomicInteger adjacentCalls = new AtomicInteger();
		private PlayableItem adjacent;
		private StreamAggregationResult initialStreams;
		private StreamAggregationResult finalStreams;
		private BiConsumer<StreamAggregationResult, Throwable> lateStreams;
		private PlaybackDescriptor.DescriptorRefreshRequest lastRefresh;
		private StremioPlaybackIdentity savedIdentity;
		private long savedPosition;
		private boolean savedCompleted;
		private PlaybackHeaderResolver headerResolver;

		@Override
		public Map<String, String> resolve(HeaderReference reference)
				throws PlaybackRequestValidationException {
			if (headerResolver != null) return headerResolver.resolve(reference);
			throw new PlaybackRequestValidationException(
					"Playback header reference is unavailable");
		}

		@Override
		public FutureSupplier<List<BrowseProvider>> providers() {
			return me.aap.utils.async.Completed.completed(List.of());
		}

		@Override
		public FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid) {
			return me.aap.utils.async.Completed.completed(catalogs);
		}

		@Override
		public FutureSupplier<CatalogPage> catalog(
				CatalogRoute route, String genre, int skip) {
			return me.aap.utils.async.Completed.completed(page);
		}

		@Override
		public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
			return me.aap.utils.async.Completed.completed(details);
		}

		@Override
		public FutureSupplier<SearchResults> search(String query) {
			searchCalls.incrementAndGet();
			return me.aap.utils.async.Completed.completed((searchResults == null) ?
					new SearchResults(query, List.of()) : searchResults);
		}

		@Override
		public FutureSupplier<StreamAggregationResult> streams(StreamAggregationRequest request) {
			streamCalls.incrementAndGet();
			if (initialStreams != null) {
				return me.aap.utils.async.Completed.completed(initialStreams);
			}
			PlaybackDescriptor stream = descriptor(request,
					"https://cdn.invalid/stream.m3u8?secret=private", 1_000L, 60_000L);
			StreamProvider provider = new StreamProvider(
					SOURCE, "fixture.provider", "Fixture Provider", 0, true);
			return me.aap.utils.async.Completed.completed(new StreamAggregationResult(List.of(
					new StreamAggregationResult.ProviderGroup(provider,
							StreamAggregationResult.ProviderStatus.SUCCESS, List.of(stream)))));
		}

		@Override
		public FutureSupplier<StreamAggregationResult> streams(String sourceUuid,
				StreamAggregationRequest request,
				BiConsumer<StreamAggregationResult, Throwable> lateResults) {
			lateStreams = lateResults;
			return streams(request);
		}

		private void publishLateStreams() {
			if ((lateStreams != null) && (finalStreams != null)) {
				lateStreams.accept(finalStreams, null);
			}
		}

		@Override
		public FutureSupplier<PlaybackDescriptor> resolve(
				PlaybackDescriptor.DescriptorRefreshRequest request) {
			resolveCalls.incrementAndGet();
			lastRefresh = request;
			return me.aap.utils.async.Completed.completed(resolved);
		}

		@Override
		public FutureSupplier<PlaybackDescriptor> validatePlayback(PlaybackDescriptor descriptor) {
			validateCalls.incrementAndGet();
			return me.aap.utils.async.Completed.completed(descriptor);
		}

		@Override
		public FutureSupplier<Void> saveProgress(StremioPlaybackIdentity identity,
				long position, boolean completed) {
			savedIdentity = identity;
			savedPosition = position;
			savedCompleted = completed;
			return me.aap.utils.async.Completed.completedVoid();
		}

		@Override
		public FutureSupplier<PlayableItem> adjacentPlayback(
				StremioDirectPlayableItem current, boolean next) {
			adjacentCalls.incrementAndGet();
			return me.aap.utils.async.Completed.completed(adjacent);
		}
	}
}
