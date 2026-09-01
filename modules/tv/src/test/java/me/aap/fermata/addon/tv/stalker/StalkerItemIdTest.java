package me.aap.fermata.addon.tv.stalker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StalkerItemIdTest {
	@Test
	public void roundTripsReservedCharacters() {
		String id = StalkerItemId.channel("tvst", 7, "genre:news", "Tin tức / News",
				"channel:42");
		StalkerItemId.Channel parsed = StalkerItemId.parseChannel(id, "tvst");

		assertEquals(7, parsed.sourceId());
		assertEquals("genre:news", parsed.categoryId());
		assertEquals("Tin tức / News", parsed.categoryName());
		assertEquals("channel:42", parsed.channelId());
	}

	@Test
	public void roundTripsVodAndSeriesHierarchy() {
		String categoryId = StalkerItemId.contentCategory("tvscc", 9, "series",
				"category:drama", "Drama / Kịch");
		StalkerItemId.ContentCategory category = StalkerItemId.parseContentCategory(categoryId,
				"tvscc");
		assertEquals(9, category.sourceId());
		assertEquals("series", category.type());
		assertEquals("category:drama", category.categoryId());
		assertEquals("Drama / Kịch", category.categoryName());

		String seriesId = StalkerItemId.content("tvssr", category.sourceId(), category.type(),
				category.categoryId(), category.categoryName(), "series:42");
		StalkerItemId.Content series = StalkerItemId.parseContent(seriesId, "tvssr");
		String seasonId = StalkerItemId.season("tvssn", series, "season:1");
		StalkerItemId.Season season = StalkerItemId.parseSeason(seasonId, "tvssn");
		String episodeId = StalkerItemId.episode("tvse", season, "episode:2");
		StalkerItemId.Episode episode = StalkerItemId.parseEpisode(episodeId, "tvse");

		assertEquals("series:42", episode.contentId());
		assertEquals("season:1", episode.seasonId());
		assertEquals("episode:2", episode.episodeId());
	}
}
