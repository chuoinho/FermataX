package me.aap.fermata.addon.podcast.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

import me.aap.fermata.addon.podcast.util.PodcastIds;
import me.aap.fermata.addon.podcast.util.PodcastUrls;

public class PodcastSearchModelTest {
	@Test
	public void requestNormalizesLocaleQueryAndLimit() {
		PodcastSearchRequest request = new PodcastSearchRequest("  Test  ",
				Locale.forLanguageTag("vi-VN"), 500);

		assertEquals("Test", request.getQuery());
		assertEquals("vi", request.getLanguage());
		assertEquals("VN", request.getCountry());
		assertEquals(50, request.getLimit());
		assertFalse(request.cacheKey().contains("null"));
	}

	@Test
	public void resultRequiresTitleAndHttpFeedButNotOptionalMetadata() {
		assertNull(result("", "https://example.test/feed"));
		assertNull(result("Show", "ftp://example.test/feed"));

		PodcastSearchResult result = result("Show", "HTTPS://EXAMPLE.TEST:443/feed#part");
		assertNotNull(result);
		assertEquals("https://example.test/feed", result.getFeedUrl());
		assertTrue(result.getArtworkUrl() == null);
		assertFalse(result.toString().contains(result.getFeedUrl()));
	}

	@Test
	public void stableHashAndUrlIdentityAreDeterministic() {
		assertEquals(PodcastIds.hash("value"), PodcastIds.hash("value"));
		assertNotEquals(PodcastIds.hash("value"), PodcastIds.hash("other"));
		assertEquals("http://example.test/path?q=1",
				PodcastUrls.normalizeHttpUrl("HTTP://EXAMPLE.TEST:80/path?q=1#fragment"));
	}

	private static PodcastSearchResult result(String title, String feed) {
		return PodcastSearchResult.create("apple", "1", title, "", "", feed,
				"", "", "", 0, false);
	}
}
