package me.aap.fermata.addon.stremio.browse;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class BrowseMediaArtworkTest {
	@Test
	public void missingOrUnsafePosterUsesCanonicalImdbFallback() {
		BrowseMedia missing = new BrowseMedia("source", "movie", "tt0133093", "The Matrix",
				null, null, "", "", null, List.of(), null);
		assertEquals("https://images.metahub.space/poster/medium/tt0133093/img",
				missing.poster());

		BrowseMedia unsafe = new BrowseMedia("source", "movie", "tt0133093", "The Matrix",
				"http://images.example/poster.jpg", null, "", "", null, List.of(), null);
		assertEquals("https://images.metahub.space/poster/medium/tt0133093/img",
				unsafe.poster());
	}
}
