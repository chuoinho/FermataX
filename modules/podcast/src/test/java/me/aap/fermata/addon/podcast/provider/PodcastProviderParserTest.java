package me.aap.fermata.addon.podcast.provider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import me.aap.fermata.addon.podcast.model.PodcastSearchResult;

@RunWith(RobolectricTestRunner.class)
public class PodcastProviderParserTest {
	@Test
	public void appleMapsNormalizedPodcastFieldsAndSkipsInvalidKinds() throws Exception {
		List<PodcastSearchResult> results = AppleSearchProvider.parse(input("""
				{"resultCount":3,"results":[
				 {"kind":"podcast","collectionId":42,"collectionName":"Road Show",
				  "artistName":"Driver","feedUrl":"https://EXAMPLE.test:443/feed.xml",
				  "artworkUrl600":"https://img.test/600.jpg","trackCount":"12",
				  "collectionExplicitness":"explicit","ignored":{"nested":true}},
				 {"kind":"song","collectionId":2,"collectionName":"Skip",
				  "feedUrl":"https://example.test/song"},
				 {"kind":"podcast","collectionId":3,"collectionName":"No feed"}
				]}
				"""), 10);

		assertEquals(1, results.size());
		PodcastSearchResult result = results.get(0);
		assertEquals("Road Show", result.getTitle());
		assertEquals("Driver", result.getAuthor());
		assertEquals("https://example.test/feed.xml", result.getFeedUrl());
		assertEquals(12, result.getEpisodeCount());
		assertTrue(result.isExplicit());
	}

	@Test
	public void fyydMapsEnvelopeAndRejectsStatusFailure() throws Exception {
		List<PodcastSearchResult> results = FyydSearchProvider.parse(input("""
				{"status":1,"msg":"ok","meta":{"paging":{"count":1}},"data":[{
				 "id":"fyyd-1","title":"Open Road","author":"Host",
				 "xmlURL":"https://feeds.test/open.xml","layoutImageURL":"https://img.test/open.jpg",
				 "description":"Description","episode_count":"8","language":"en"}]}
				"""), 10);

		assertEquals(1, results.size());
		assertEquals("fyyd", results.get(0).getProvider());
		assertEquals("Description", results.get(0).getDescription());
		assertEquals(8, results.get(0).getEpisodeCount());
		assertFalse(results.get(0).isExplicit());

		IOException error = assertThrows(IOException.class, () ->
				FyydSearchProvider.parse(input("{\"status\":0,\"msg\":\"unavailable\",\"data\":[]}"), 10));
		assertTrue(error.getMessage().contains("unavailable"));
	}

	@Test
	public void providerParsersRespectClientResultLimit() throws Exception {
		StringBuilder json = new StringBuilder("{\"results\":[");
		for (int i = 0; i < 100; i++) {
			if (i != 0) json.append(',');
			json.append("{\"kind\":\"podcast\",\"collectionId\":").append(i)
					.append(",\"collectionName\":\"Show ").append(i)
					.append("\",\"feedUrl\":\"https://example.test/").append(i).append("\"}");
		}
		json.append("]}");

		assertEquals(7, AppleSearchProvider.parse(input(json.toString()), 7).size());
	}

	@Test
	public void appleDoesNotTreatNotExplicitAsExplicit() throws Exception {
		List<PodcastSearchResult> results = AppleSearchProvider.parse(input("""
				{"results":[{"kind":"podcast","collectionName":"Clean show",
				"feedUrl":"https://example.test/clean","collectionExplicitness":"notExplicit"}]}
				"""), 1);

		assertEquals(1, results.size());
		assertFalse(results.get(0).isExplicit());
	}

	private static ByteArrayInputStream input(String value) {
		return new ByteArrayInputStream(value.getBytes(UTF_8));
	}
}
