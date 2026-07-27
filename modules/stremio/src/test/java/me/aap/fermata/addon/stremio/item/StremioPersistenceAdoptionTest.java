package me.aap.fermata.addon.stremio.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.net.InetAddress;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.CatalogDescriptor;
import me.aap.fermata.addon.stremio.browse.CatalogPage;
import me.aap.fermata.addon.stremio.browse.CatalogRoute;
import me.aap.fermata.addon.stremio.browse.SearchResults;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptorFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackHeaderRegistry;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackMetadata;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.playback.StreamProvider;
import me.aap.fermata.addon.stremio.protocol.response.DirectStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.ExternalStreamTarget;
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.protocol.response.YoutubeStreamTarget;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.PersistentMediaItem;
import me.aap.fermata.media.net.RemotePlaybackItem;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;

@RunWith(RobolectricTestRunner.class)
@Config(application = FermataApplication.class)
public class StremioPersistenceAdoptionTest {
	private static final String SOURCE = "11111111-1111-4111-8111-111111111111";

	@Test
	public void imdbAndTmdbNavigationIdsAreCanonicalAcrossProviders() {
		BrowseMedia imdbA = media("source-a", "movie", "tt0133093", "The Matrix");
		BrowseMedia imdbB = media("source-b", "movie", "imdb:TT0133093", "The Matrix");
		BrowseMedia tmdbA = media("source-a", "movie", "tmdb:603", "The Matrix");
		BrowseMedia tmdbB = media("source-b", "movie", "tmdb:movie:603", "The Matrix");
		BrowseMedia customA = media("source-a", "movie", "provider:603", "Private");
		BrowseMedia customB = media("source-b", "movie", "provider:603", "Private");

		assertEquals(StremioItemIds.meta(imdbA), StremioItemIds.meta(imdbB));
		assertEquals(StremioItemIds.meta(tmdbA), StremioItemIds.meta(tmdbB));
		assertFalse(StremioItemIds.meta(customA).equals(StremioItemIds.meta(customB)));
		assertEquals("imdb:tt0133093",
				StremioCanonicalIdentity.from("movie", imdbA.id()).contentId());
		assertEquals("tmdb:movie:603",
				StremioCanonicalIdentity.from("movie", tmdbA.id()).contentId());
	}

	@Test
	public void stableIdSurvivesTwoCollectionExports() {
		ExtRoot stremioRoot = new ExtRoot("Stremio", null);
		FakeGateway gateway = new FakeGateway();
		StreamAggregationRequest request = request("private:movie");
		PlaybackDescriptor descriptor = direct(request);
		StremioDirectPlayableItem direct = new StremioDirectPlayableItem(
				stremioRoot, gateway, descriptor, 0L);

		PlayableItem recent = direct.export("recent:item", new ExtRoot("recent", null));
		PlayableItem favorite = recent.export("favorite:item", new ExtRoot("favorite", null));

		assertFalse(direct.getId().equals(direct.getPersistentId()));
		assertEquals(direct.getPersistentId(), PersistentMediaItem.idOf(recent));
		assertEquals(direct.getPersistentId(), recent.getOrigId());
		assertEquals(direct.getPersistentId(), PersistentMediaItem.idOf(favorite));
		assertEquals(direct.getPersistentId(), favorite.getOrigId());
		assertSame(direct.getParent(), recent.getParent());
		assertSame(direct.getRoot(), favorite.getRoot());
		assertEquals(R.drawable.stremio, direct.getIcon());
		assertEquals(R.drawable.stremio, recent.getIcon());
		assertEquals(R.drawable.stremio, favorite.getIcon());
		assertTrue(recent instanceof RemotePlaybackItem);
		assertTrue(favorite instanceof RemotePlaybackItem);
	}

	@Test
	public void youtubeTargetsCannotCreateStremioPlaybackItems() {
		assertThrows(IllegalArgumentException.class,
				() -> youtube(request("private:movie")));
	}

	@Test
	public void externalHttpRemainsVisibleButUnavailableAndEmptyAggregationIsVisible()
			throws Exception {
		DefaultMediaLib lib = new DefaultMediaLib(RuntimeEnvironment.getApplication());
		ExtRoot stremioRoot = new ExtRoot("Stremio", lib);
		BrowseMedia media = media(SOURCE, "movie", "private:web", "Exact web movie");
		FakeGateway gateway = new FakeGateway();
		StreamAggregationRequest request = request(media.id());
		PlaybackDescriptor descriptor = external(request);
		gateway.result = aggregation(descriptor);
		var picker = new StremioStreamPickerItem(
				stremioRoot, stremioRoot, gateway, media, null);
		Item unavailable = picker.getUnsortedChildren().get().get(0);
		assertTrue(unavailable instanceof StremioUnavailableStreamItem);
		assertTrue(unavailable.getMediaDescription().get().getSubtitle().toString()
				.contains("security"));

		gateway.result = new StreamAggregationResult(List.of());
		var empty = new StremioStreamPickerItem(
				stremioRoot, stremioRoot, gateway, media, null);
		List<Item> children = empty.getUnsortedChildren().get();
		assertEquals(1, children.size());
		assertTrue(children.get(0) instanceof StremioNoStreamsItem);
		assertEquals("No playable streams", children.get(0).getName());
	}

	private static BrowseMedia media(String source, String type, String id, String title) {
		return new BrowseMedia(source, type, id, title, null, null, "", "", null,
				List.of(), null);
	}

	private static StreamAggregationRequest request(String contentId) {
		StremioPlaybackIdentity identity = StremioPlaybackIdentity.scoped(
				SOURCE, "movie", contentId, contentId);
		return new StreamAggregationRequest(identity, "movie", contentId, contentId,
				new StremioPlaybackMetadata("Exact movie", "", 120_000L));
	}

	private static PlaybackDescriptor direct(StreamAggregationRequest request) {
		return factory().create(request, provider(), new StremioStream("Direct", "Fixture",
				null, new DirectStreamTarget("https://cdn.example.invalid/video.mp4"),
				StreamBehaviorHints.EMPTY), 1L);
	}

	private static PlaybackDescriptor youtube(StreamAggregationRequest request) {
		return factory().create(request, provider(), new StremioStream("YouTube", "Fixture",
				null, new YoutubeStreamTarget("abc_DEF-123"), StreamBehaviorHints.EMPTY), 1L);
	}

	private static PlaybackDescriptor external(StreamAggregationRequest request) throws Exception {
		var factory = new PlaybackDescriptorFactory(new PlaybackHeaderRegistry.HeaderStore(),
				host -> List.of(InetAddress.getByAddress(new byte[]{8, 8, 8, 8})));
		return factory.create(request, provider(), new StremioStream("Web", "Fixture",
				null, new ExternalStreamTarget("https://player.example.invalid/watch"),
				StreamBehaviorHints.EMPTY), 1L);
	}

	private static PlaybackDescriptorFactory factory() {
		return new PlaybackDescriptorFactory(new PlaybackHeaderRegistry.HeaderStore());
	}

	private static StreamProvider provider() {
		return new StreamProvider(SOURCE, "fixture", "Fixture", 0, true);
	}

	private static StreamAggregationResult aggregation(PlaybackDescriptor descriptor) {
		return new StreamAggregationResult(List.of(new StreamAggregationResult.ProviderGroup(
				provider(), StreamAggregationResult.ProviderStatus.SUCCESS, List.of(descriptor))));
	}

	private static final class FakeGateway implements StremioItemGateway {
		StreamAggregationResult result;

		@Override
		public FutureSupplier<List<BrowseProvider>> providers() {
			return Completed.completedEmptyList();
		}

		@Override
		public FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid) {
			return Completed.completedEmptyList();
		}

		@Override
		public FutureSupplier<CatalogPage> catalog(CatalogRoute route, String genre, int skip) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
			return Completed.completed(new BrowseDetails(media, List.of()));
		}

		@Override
		public FutureSupplier<SearchResults> search(String query) {
			return Completed.completed(new SearchResults(query, List.of()));
		}

		@Override
		public FutureSupplier<StreamAggregationResult> streams(StreamAggregationRequest request) {
			return Completed.completed(result);
		}

		@Override
		public FutureSupplier<PlaybackDescriptor> resolve(
				PlaybackDescriptor.DescriptorRefreshRequest request) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<Void> saveProgress(
				StremioPlaybackIdentity identity, long position, boolean completed) {
			return Completed.completedVoid();
		}
	}
}
