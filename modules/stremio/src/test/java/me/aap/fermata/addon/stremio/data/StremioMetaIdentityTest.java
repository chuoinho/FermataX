package me.aap.fermata.addon.stremio.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class StremioMetaIdentityTest {
	@Test
	public void customIdsAreScopedToSource() {
		StremioMetaIdentity first = StremioMetaIdentity.create("source-a", "movie",
				"movie:1", null);
		StremioMetaIdentity second = StremioMetaIdentity.create("source-b", "movie",
				"movie:1", null);

		assertEquals("source-a", first.identityScope());
		assertEquals("source-b", second.identityScope());
		assertNotEquals(first.metaKey(), second.metaKey());
		assertNull(first.canonicalIdentity());
	}

	@Test
	public void recognizedCanonicalIdsAreSharedAcrossSources() {
		StremioMetaIdentity first = StremioMetaIdentity.create("source-a", "movie",
				"provider-one", "IMDb:TT0133093");
		StremioMetaIdentity second = StremioMetaIdentity.create("source-b", "MOVIE",
				"provider-two", "tt0133093");

		assertEquals("canonical:imdb", first.identityScope());
		assertEquals("imdb:tt0133093", first.canonicalIdentity());
		assertEquals("tt0133093", first.durableMetaId());
		assertEquals(first, second);
	}

	@Test
	public void unknownCanonicalNamespaceRemainsProviderScoped() {
		StremioMetaIdentity first = StremioMetaIdentity.create("source-a", "movie",
				"movie:1", "vendor:shared");
		StremioMetaIdentity second = StremioMetaIdentity.create("source-b", "movie",
				"movie:1", "vendor:shared");

		assertNotEquals(first.metaKey(), second.metaKey());
		assertNull(first.canonicalIdentity());
		assertNull(second.canonicalIdentity());
	}
}
