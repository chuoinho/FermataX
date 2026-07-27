package me.aap.fermata.addon.stremio.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import me.aap.fermata.addon.stremio.protocol.ManifestValidator;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ContentIdentitySetTest {
	@Test
	public void sourceLocalIdIsNeverBroadcastToAnotherProvider() {
		var identities = ContentIdentitySet.from("source-a", "series", "private-series-id",
				"private-episode-id", 2, 3);

		assertEquals(java.util.List.of("private-episode-id", "private-series-id"),
				identities.candidateIds("source-a"));
		assertTrue(identities.candidateIds("source-b").isEmpty());
		assertFalse(identities.toString().contains("private"));
	}

	@Test
	public void canonicalImdbEpisodeCanRouteAcrossProviders() {
		var identities = ContentIdentitySet.from("source-a", "series", "imdb:tt0133093",
				"provider-episode", 2, 3);
		var manifest = manifest("[\"tt\"]", "series");

		assertEquals("provider-episode",
				identities.candidateIds("source-a").get(0));
		assertEquals(java.util.List.of("tt0133093:2:3"),
				identities.candidateIds("source-b"));
		assertEquals("tt0133093:2:3",
				identities.select(manifest, "source-b", "stream").orElseThrow());
	}

	@Test
	public void providerReceivesFirstIdMatchingItsOwnManifestConstraints() {
		var identities = ContentIdentitySet.from("source-a", "movie", "tmdb:movie:603",
				"local-603", -1, -1);
		var tmdb = manifest("[\"tmdb:\"]", "movie");
		var imdb = manifest("[\"tt\"]", "movie");

		assertEquals("tmdb:movie:603",
				identities.select(tmdb, "source-b", "stream").orElseThrow());
		assertTrue(identities.select(imdb, "source-b", "stream").isEmpty());
	}

	private static me.aap.fermata.addon.stremio.protocol.model.StremioManifest manifest(
			String prefixes, String type) {
		return ManifestValidator.parse("""
				{
				  "id":"fixture", "name":"Fixture", "description":"Fixture",
				  "version":"1.0.0",
				  "types":["%s"], "idPrefixes":%s,
				  "resources":["stream"], "catalogs":[]
				}
				""".formatted(type, prefixes));
	}
}
