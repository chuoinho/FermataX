package me.aap.fermata.addon.podcast.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PodcastSourceTest {
	@Test
	public void extractsBasicAuthAndNeverExposesItInRequestUrl() {
		PodcastSource source = PodcastSource.create(
				"https://driver:p%40ss@example.test/feed.xml", null, null);

		assertNotNull(source);
		assertTrue(source.isPrivate());
		assertFalse(source.getRequestUrl().contains("driver"));
		assertFalse(source.getRequestUrl().contains("p%40ss"));
		assertTrue(source.toRequest(null, null).getAuthorization().startsWith("Basic "));
	}

	@Test
	public void preservesPercentEncodingAndLiteralPlusInCredentials() {
		PodcastSource source = PodcastSource.create(
				"https://driver:p+ss@example.test/my%20feed.xml?token=a%2Fb", null, null);

		assertEquals("https://example.test/my%20feed.xml?token=a%2Fb", source.getRequestUrl());
		assertFalse(source.getRequestUrl().contains("%2520"));
		assertFalse(source.getRequestUrl().contains("%252F"));
	}

	@Test
	public void tokenUrlUsesStableOpaqueKeyAndRedactedStorageUrl() {
		PodcastSource first = PodcastSource.create(
				"https://example.test/feed?token=secret", null, null);
		PodcastSource second = PodcastSource.create(
				"https://example.test/feed?token=secret", null, null);

		assertTrue(first.isPrivate());
		assertEquals(first.getFeedKey(), second.getFeedKey());
		assertFalse(first.getStorageUrl().contains("secret"));
		assertTrue(first.getCredentialRef().startsWith("feed:"));
	}

	@Test
	public void plainPublicFeedDoesNotRequireSecureStorage() {
		PodcastSource source = PodcastSource.create("https://example.test/feed.xml", null, null);
		assertFalse(source.isPrivate());
		assertEquals("https://example.test/feed.xml", source.getStorageUrl());
	}
}
