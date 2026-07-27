package me.aap.fermata.addon.stremio.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.stremio.StremioRootItem;
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
import me.aap.fermata.addon.stremio.protocol.response.StreamBehaviorHints;
import me.aap.fermata.addon.stremio.protocol.response.StremioStream;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregationResult;
import me.aap.fermata.addon.stremio.subtitle.SubtitleCandidate;
import me.aap.fermata.addon.stremio.subtitle.SubtitleDescriptor;
import me.aap.fermata.addon.stremio.subtitle.SubtitleFormat;
import me.aap.fermata.addon.stremio.subtitle.SubtitleLanguageNormalizer;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.sub.FileSubtitles;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.vfs.VirtualFile;

public class StremioSubtitlePlaybackResourceTest {
	private static final String SOURCE = "11111111-1111-4111-8111-111111111111";
	private static final byte[] VTT = ("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n" +
			"Engine bridge\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);

	@Test
	public void exposesOnlyEngineReadableLazySidecars() throws Exception {
		StreamAggregationRequest request = request();
		PlaybackDescriptor playback = new PlaybackDescriptorFactory(
				new PlaybackHeaderRegistry.HeaderStore()).create(request,
				new StreamProvider(SOURCE, "fixture", "Fixture", 0, true),
				new StremioStream("HD", null, null,
						new DirectStreamTarget("https://cdn.example.invalid/movie.m3u8"),
						StreamBehaviorHints.EMPTY), System.currentTimeMillis());
		FakeGateway gateway = new FakeGateway();
		ExtRoot root = new ExtRoot(StremioRootItem.ID, null);
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, playback, request, 0L);

		var folder = item.getResource().getParent().get();
		var files = folder.getChildren().get();
		assertEquals(2, files.size());
		assertTrue(files.stream().anyMatch(file -> file.getName().endsWith(".vtt")));
		assertTrue(files.stream().anyMatch(file -> file.getName().endsWith(".srt")));
		assertEquals(1, gateway.resolveCalls);
		assertEquals(0, gateway.loadCalls);
		assertFalse(item.isSeekable());

		var subtitles = FileSubtitles.load((VirtualFile) files.get(0));
		assertEquals("Engine bridge",
				subtitles.get(SubGrid.Position.BOTTOM_CENTER).get(0).getText());
		assertEquals(1, gateway.loadCalls);
		assertTrue(item.getResource().getRid().toString().startsWith("https://cdn.example.invalid"));
	}

	@Test
	public void retriesAfterProviderInitiallyReturnsNoSubtitles() throws Exception {
		StreamAggregationRequest request = request();
		PlaybackDescriptor playback = new PlaybackDescriptorFactory(
				new PlaybackHeaderRegistry.HeaderStore()).create(request,
				new StreamProvider(SOURCE, "fixture", "Fixture", 0, true),
				new StremioStream("HD", null, null,
						new DirectStreamTarget("https://cdn.example.invalid/movie.m3u8"),
						StreamBehaviorHints.EMPTY), System.currentTimeMillis());
		FakeGateway gateway = new FakeGateway();
		gateway.emptyFirst = true;
		ExtRoot root = new ExtRoot(StremioRootItem.ID, null);
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, playback, request, 0L);

		var folder = item.getResource().getParent().get();
		assertTrue(folder.getChildren().get().isEmpty());
		assertEquals(1, gateway.resolveCalls);
		assertEquals(2, folder.getChildren().get().size());
		assertEquals(2, gateway.resolveCalls);
	}

	@Test
	public void parsesWebVttShortTimestampAfterSelectingSubtitle() throws Exception {
		StreamAggregationRequest request = request();
		PlaybackDescriptor playback = new PlaybackDescriptorFactory(
				new PlaybackHeaderRegistry.HeaderStore()).create(request,
				new StreamProvider(SOURCE, "fixture", "Fixture", 0, true),
				new StremioStream("HD", null, null,
						new DirectStreamTarget("https://cdn.example.invalid/movie.m3u8"),
						StreamBehaviorHints.EMPTY), System.currentTimeMillis());
		FakeGateway gateway = new FakeGateway() {
			@Override
			public FutureSupplier<byte[]> loadSubtitle(SubtitleDescriptor descriptor) {
				return Completed.completed(("WEBVTT\n\n00:01.000 --> 00:02.500\n" +
						"Short timestamp\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
			}
		};
		ExtRoot root = new ExtRoot(StremioRootItem.ID, null);
		StremioDirectPlayableItem item = new StremioDirectPlayableItem(
				root, gateway, playback, request, 0L);

		var files = item.getResource().getParent().get().getChildren().get();
		var subtitles = FileSubtitles.load((VirtualFile) files.get(0));
		assertEquals("Short timestamp",
				subtitles.get(SubGrid.Position.BOTTOM_CENTER).get(0).getText());
	}

	private static StreamAggregationRequest request() {
		return new StreamAggregationRequest(
				StremioPlaybackIdentity.scoped(SOURCE, "movie", "tt1", "tt1"),
				"movie", "tt1", "tt1",
				new StremioPlaybackMetadata("Movie", null, 0L));
	}

	private static SubtitleDescriptor subtitle(String id, SubtitleFormat format) {
		return new SubtitleDescriptor(id, id, URI.create("https://sub.example.invalid/" + id +
				((format == SubtitleFormat.WEBVTT) ? ".vtt" : ".ass")), "eng",
				SubtitleLanguageNormalizer.normalize("eng"), SOURCE, "Fixture",
				SubtitleCandidate.Source.PROVIDER, format, SubtitleDescriptor.Status.READY,
				VTT.length, null, Instant.now().plusSeconds(600));
	}

	private static class FakeGateway implements StremioItemGateway {
		private int resolveCalls;
		private int loadCalls;
		private boolean emptyFirst;

		@Override
		public FutureSupplier<List<BrowseProvider>> providers() {
			return Completed.completed(List.of());
		}

		@Override
		public FutureSupplier<List<CatalogDescriptor>> catalogs(String sourceUuid) {
			return Completed.completed(List.of());
		}

		@Override
		public FutureSupplier<CatalogPage> catalog(CatalogRoute route, String genre, int skip) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<BrowseDetails> meta(BrowseMedia media) {
			return Completed.completedNull();
		}

		@Override
		public FutureSupplier<SearchResults> search(String query) {
			return Completed.completed(new SearchResults(query, List.of()));
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
		public FutureSupplier<SubtitleAggregationResult> subtitles(String type, String videoId) {
			resolveCalls++;
			if (emptyFirst && (resolveCalls == 1)) {
				return Completed.completed(new SubtitleAggregationResult(List.of(), List.of(), false));
			}
			return Completed.completed(new SubtitleAggregationResult(List.of(
					subtitle("vtt", SubtitleFormat.WEBVTT),
					subtitle("ass", SubtitleFormat.ASS)), List.of(), false));
		}

		@Override
		public FutureSupplier<byte[]> loadSubtitle(SubtitleDescriptor descriptor) {
			loadCalls++;
			return Completed.completed(VTT.clone());
		}

		@Override
		public FutureSupplier<Void> saveProgress(StremioPlaybackIdentity identity,
				long position, boolean completed) {
			return Completed.completedVoid();
		}
	}
}
