package me.aap.fermata.addon.tv.xtream;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class XtreamJsonStreamParserTest extends Assert {

	@Test
	public void prepareJsonInputRejectsHtml() {
		IOException ex = assertThrows(IOException.class, () ->
				XtreamJsonStreamParser.prepareJsonInput(input("  \n<html>auth failed</html>")));

		assertTrue(ex.getMessage().contains("got HTML"));
	}

	@Test
	public void prepareJsonInputRejectsUnexpectedPayload() {
		IOException ex = assertThrows(IOException.class, () ->
				XtreamJsonStreamParser.prepareJsonInput(input("auth failed")));

		assertTrue(ex.getMessage().contains("expected JSON"));
	}

	@Test
	public void prepareJsonInputKeepsFirstJsonByteReadable() throws IOException {
		assertEquals('{', XtreamJsonStreamParser.prepareJsonInput(input("  {\"ok\":true}")).read());
		assertEquals('[', XtreamJsonStreamParser.prepareJsonInput(input("\n\t[]")).read());
	}

	@Test
	public void statusAcceptsCommonStringAndNumericProviderValues() throws IOException {
		XtreamStatus status = new XtreamJsonStreamParser().parseStatus(input("""
				{"user_info":{"auth":"1","status":"Active","exp_date":4102444800,
				"active_cons":"2.0","max_connections":3,"ignored":{"nested":true}}}
				"""));

		assertTrue(status.isAuthenticated());
		assertTrue(status.isActive());
		assertEquals(4102444800L, status.getExpiryTime());
		assertEquals(2, status.getActiveConnections());
		assertEquals(3, status.getMaxConnections());
	}

	@Test
	public void categoryArrayIsConsumedIncrementallyAtPanelScale() throws IOException {
		int count = 10_000;
		StringBuilder json = new StringBuilder(count * 70).append('[');
		for (int i = 0; i < count; i++) {
			if (i != 0) json.append(',');
			json.append("{\"category_id\":\"").append(i)
					.append("\",\"category_name\":\"Category ").append(i)
					.append("\",\"parent_id\":\"0\",\"ignored\":[1,2,3]}");
		}
		json.append(']');
		AtomicInteger seen = new AtomicInteger();

		new XtreamJsonStreamParser().parseLiveCategories(input(json.toString()), category -> {
			int index = seen.getAndIncrement();
			assertEquals(String.valueOf(index), category.getId());
		});

		assertEquals(count, seen.get());
	}

	@Test
	public void liveAndVodStreamsPreserveArchiveAndContainerFields() throws IOException {
		XtreamJsonStreamParser parser = new XtreamJsonStreamParser();
		List<XtreamChannel> live = new ArrayList<>();
		List<XtreamMovie> vod = new ArrayList<>();
		parser.parseLiveStreams(input("""
				[{"stream_id":"42.0","name":"News","stream_icon":"live.png",
				"epg_channel_id":"news.id","tv_archive":"yes","tv_archive_duration":"7",
				"category_id":5},{"stream_id":0,"name":"invalid"}]
				"""), live::add);
		parser.parseVodStreams(input("""
				[{"stream_id":77,"name":"Movie","stream_icon":"vod.png",
				"category_id":"9","container_extension":"mkv"}]
				"""), vod::add);

		assertEquals(1, live.size());
		assertEquals(42, live.get(0).getStreamId());
		assertTrue(live.get(0).isTvArchive());
		assertEquals(7, live.get(0).getTvArchiveDuration());
		assertEquals("5", live.get(0).getCategoryId());
		assertEquals(1, vod.size());
		assertEquals("mkv", vod.get(0).getContainerExtension());
	}

	@Test
	public void seriesInfoSupportsObjectEpisodesAndNestedInfoFallbacks() throws IOException {
		List<XtreamSeason> seasons = new XtreamJsonStreamParser().parseSeriesInfo(input("""
				{"seasons":[{"season_number":"2","name":"Second","cover":"season.jpg"}],
				"episodes":{"2":[{"id":"20","episode_num":"3","title":"Episode title",
				"container_extension":"mkv","info":{"movie_image":"episode.jpg"}}]}}
				"""));

		assertEquals(1, seasons.size());
		XtreamSeason season = seasons.get(0);
		assertEquals(2, season.getSeasonNumber());
		assertEquals("Second", season.getName());
		assertEquals(1, season.getEpisodes().size());
		XtreamEpisode episode = season.getEpisodes().get(0);
		assertEquals(20, episode.getEpisodeId());
		assertEquals(3, episode.getEpisodeNumber());
		assertEquals("episode.jpg", episode.getIcon());
		assertEquals("mkv", episode.getContainerExtension());
	}

	@Test
	public void epgAcceptsWrappedListingsAndDecodesProviderText() throws IOException {
		List<XtreamEpgProgram> epg = new XtreamJsonStreamParser().parseEpg(input("""
				{"epg_listings":[{"title":"TmV3cw==","description":"VXBkYXRl",
				"start_timestamp":"1700000000","stop_timestamp":"1700003600",
				"has_archive":"1"},{"title":"invalid","start_timestamp":"0"}]}
				"""));

		assertEquals(1, epg.size());
		assertEquals("News", epg.get(0).getTitle());
		assertEquals("Update", epg.get(0).getDescription());
		assertTrue(epg.get(0).hasArchive());
	}

	private static ByteArrayInputStream input(String value) {
		return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
	}
}
