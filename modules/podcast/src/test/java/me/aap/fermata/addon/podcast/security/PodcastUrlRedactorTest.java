package me.aap.fermata.addon.podcast.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PodcastUrlRedactorTest {
	@Test
	public void stripsUserInfoAndAllQueryValues() {
		String secret = "https://driver:secret@example.test/feed.xml?token=abc&member=42";
		String redacted = PodcastUrlRedactor.forStorage(secret);

		assertTrue(PodcastUrlRedactor.containsSecrets(secret));
		assertFalse(redacted.contains("driver"));
		assertFalse(redacted.contains("secret"));
		assertFalse(redacted.contains("abc"));
		assertFalse(redacted.contains("42"));
		assertTrue(redacted.contains("token="));
		assertTrue(redacted.contains("member="));
		assertNotEquals(secret, redacted);
	}

	@Test
	public void publicUrlRemainsUsable() {
		String publicUrl = "https://example.test/feed.xml";
		assertFalse(PodcastUrlRedactor.containsSecrets(publicUrl));
		assertTrue(PodcastUrlRedactor.forStorage(publicUrl).equals(publicUrl));
	}
}
