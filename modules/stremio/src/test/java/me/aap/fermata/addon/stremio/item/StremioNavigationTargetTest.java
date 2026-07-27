package me.aap.fermata.addon.stremio.item;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;

public class StremioNavigationTargetTest {
	@Test
	public void seriesRootAlwaysOpensDetailsButEpisodesOpenStreams() {
		BrowseMedia series = media("series");
		BrowseEpisode episode = new BrowseEpisode("source", "series", "id", "episode-1",
				"Episode", 1, 1, null, null, "", null);

		assertEquals(StremioNavigationTarget.DETAILS,
				StremioNavigationTarget.forContent(series, null));
		assertEquals(StremioNavigationTarget.STREAMS,
				StremioNavigationTarget.forContent(series, episode));
		assertEquals(StremioNavigationTarget.STREAMS,
				StremioNavigationTarget.forContent(media("movie"), null));
	}

	private static BrowseMedia media(String type) {
		return new BrowseMedia("source", type, "id", "Title", null, null, "", "",
				null, List.of(), "en");
	}
}
