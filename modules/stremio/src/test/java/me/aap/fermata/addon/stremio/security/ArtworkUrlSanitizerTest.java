package me.aap.fermata.addon.stremio.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArtworkUrlSanitizerTest {
	@Test
	public void acceptsOnlyCredentialFreeHttpsArtwork() {
		assertEquals("https://IMAGES.EXAMPLE/poster.jpg",
				ArtworkUrlSanitizer.sanitize("https://IMAGES.EXAMPLE/poster.jpg"));
		assertNull(ArtworkUrlSanitizer.sanitize(
				"https://images.example.invalid/poster.jpg?X-Amz-Signature=private"));
		assertNull(ArtworkUrlSanitizer.sanitize(
				"https://user:pass@images.example/poster.jpg"));
		assertNull(ArtworkUrlSanitizer.sanitize(
				"https://images.example.invalid/token/private/poster.jpg"));
		assertNull(ArtworkUrlSanitizer.sanitize(
				"https://images.example.invalid/%74%6f%6b%65%6e/private/poster.jpg"));
		assertEquals("https://images.example.invalid/poster.jpg?width=640&quality=80",
				ArtworkUrlSanitizer.sanitize(
						"https://images.example.invalid/poster.jpg?width=640&quality=80"));
		assertEquals("https://images.metahub.space/poster/medium/tt0133093/img",
				ArtworkUrlSanitizer.sanitize(
						"https://images.metahub.space/poster/medium/tt0133093/img"));
		assertEquals("https://images.metahub.space/background/medium/tt0133093/img",
				ArtworkUrlSanitizer.sanitize(
						"https://images.metahub.space/background/medium/tt0133093/img"));
		assertNull(ArtworkUrlSanitizer.sanitize(
				"https://images.example.invalid/aB12cd/poster.jpg"));
		assertNull(ArtworkUrlSanitizer.sanitize("http://images.example.invalid/poster.jpg"));
		assertNull(ArtworkUrlSanitizer.sanitize("content://private/poster.jpg"));
	}

	@Test
	public void detectsShortAndOpaquePathTokens() {
		assertTrue(ArtworkUrlSanitizer.containsOpaquePathToken("/aB12cd/poster"));
		assertTrue(ArtworkUrlSanitizer.containsOpaquePathToken("/deadbeef/poster"));
		assertFalse(ArtworkUrlSanitizer.containsOpaquePathToken("/images/poster.jpg"));
		assertFalse(ArtworkUrlSanitizer.containsOpaquePathToken("/poster/tt0133093/img"));
	}

	@Test
	public void buildsSafeCanonicalPosterFallbackForImdbItems() {
		assertEquals("https://images.metahub.space/poster/medium/tt0133093/img",
				ArtworkUrlSanitizer.canonicalPoster("movie", "tt0133093"));
		assertEquals("https://images.metahub.space/poster/medium/tt0133093/img",
				ArtworkUrlSanitizer.canonicalPoster("series", "imdb:TT0133093"));
		assertNull(ArtworkUrlSanitizer.canonicalPoster("movie", "provider-123"));
	}
}
