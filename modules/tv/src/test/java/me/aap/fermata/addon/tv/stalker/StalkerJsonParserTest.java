package me.aap.fermata.addon.tv.stalker;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class StalkerJsonParserTest {
	private final StalkerJsonParser parser = new StalkerJsonParser();

	@Test
	public void parsesHandshakeCategoriesAndChannels() throws Exception {
		assertEquals("secret-token", parser.parseToken(json(
				"{\"js\":{\"token\":\"secret-token\",\"random\":\"x\"}}")));

		List<StalkerCategory> categories = parser.parseCategories(json(
				"{\"js\":[{\"id\":\"1\",\"title\":\"News\"}]}"));
		assertEquals(List.of(new StalkerCategory("1", "News")), categories);

		List<StalkerChannel> channels = parser.parseChannels(json(
				"{\"js\":{\"data\":[{\"id\":\"10\",\"name\":\"World\",\"logo\":\"https://img.invalid/10.png\",\"tv_genre_id\":\"1\",\"cmd\":\"ffmpeg http://stream.invalid/live/10\"}]}}"));
		assertEquals(1, channels.size());
		assertEquals("10", channels.get(0).id());
		assertEquals("1", channels.get(0).categoryId());
	}

	@Test
	public void parsesCreateLinkAndExtendedHeaders() throws Exception {
		StalkerPlaybackLink link = parser.parseLink(json(
				"{\"js\":{\"cmd\":\"ffmpeg https://cdn.invalid/live.m3u8|User-Agent=Portal%20Agent&Referer=https%3A%2F%2Fportal.invalid%2Fc%2F\"}}"),
				Map.of("Cookie", "mac=redacted"));

		assertEquals("https://cdn.invalid/live.m3u8", link.uri().toString());
		assertEquals("Portal Agent", link.headers().get("User-Agent"));
		assertEquals("https://portal.invalid/c/", link.headers().get("Referer"));
		assertEquals("mac=redacted", link.headers().get("Cookie"));
		assertFalse(link.toString().contains("live.m3u8"));
		assertFalse(link.toString().contains("mac=redacted"));
	}

	@Test
	public void parsesVodSeriesSeasonsAndEpisodes() throws Exception {
		StalkerPage<StalkerVod> vod = parser.parseVodPage(json(
				"{\"js\":{\"total_items\":\"2\",\"max_page_items\":\"1\",\"data\":[" +
						"{\"id\":\"movie:7\",\"name\":\"Movie\",\"cmd\":\"ffmpeg http://stream.invalid/movie\",\"screenshot_uri\":\"http://img.invalid/movie.jpg\"}," +
						"{\"id\":\"series:8\",\"is_series\":\"1\",\"cmd\":\"ffmpeg http://stream.invalid/series\"}]}}"));
		assertEquals(2, vod.totalItems());
		assertEquals(1, vod.maxPageItems());
		assertEquals(1, vod.items().size());
		assertEquals("movie:7", vod.items().get(0).id());

		StalkerPage<StalkerSeries> series = parser.parseSeriesPage(json(
				"{\"js\":{\"data\":[{\"id\":\"77\",\"name\":\"A Series\",\"description\":\"Plot\"}]}}"));
		assertEquals("A Series", series.items().get(0).name());
		assertEquals(0, series.totalItems());

		StalkerPage<StalkerSeason> seasons = parser.parseSeasonPage(json(
				"{\"js\":{\"data\":[{\"id\":\"season:1\",\"name\":\"Season 1\"," +
						"\"cmd\":\"ffmpeg http://stream.invalid/series/77\",\"series\":[1,2]}]}}"));
		assertEquals(1, seasons.items().size());
		StalkerSeason season = seasons.items().get(0);
		assertEquals(1, season.number());
		assertEquals(2, season.episodes().size());
		assertEquals("1", season.episodes().get(0).seriesNumber());
		assertEquals("season:1:2", season.episodes().get(1).id());
	}

	@Test
	public void parsesCatchupDurationAndEpgArchiveTimestamps() throws Exception {
		List<StalkerChannel> channels = parser.parseChannels(json(
				"{\"js\":{\"data\":[{\"id\":\"10\",\"name\":\"World\",\"cmd\":\"ffmpeg http://stream.invalid/live\",\"tv_archive_duration\":\"7\"}]}}"));
		assertEquals(7, channels.get(0).catchupDays());

		StalkerPage<StalkerEpgProgram> epg = parser.parseEpgPage(json(
				"{\"js\":{\"data\":[{\"id\":\"900\",\"ch_id\":\"10\"," +
						"\"start_timestamp\":\"1788200000\",\"stop_timestamp\":\"1788203600\"," +
						"\"name\":\"News\",\"mark_archive\":\"1\"}]}}"), "fallback");
		assertEquals(1, epg.items().size());
		assertEquals(1_788_200_000_000L, epg.items().get(0).startTime());
		assertTrue(epg.items().get(0).archive());
	}

	@Test
	public void rejectsHtmlAndNonHttpPlayback() {
		assertThrows(IOException.class, () -> parser.parseToken(json("<html>error</html>")));
		assertThrows(IOException.class, () -> StalkerJsonParser.parseCommand(
				"ffmpeg udp://239.0.0.1:1234", Map.of()));
	}

	private static ByteArrayInputStream json(String value) {
		return new ByteArrayInputStream(value.getBytes(UTF_8));
	}
}
