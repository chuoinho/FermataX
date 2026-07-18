package me.aap.fermata.media.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class SearchFolderRevisionTest {
	@Test
	public void invalidationChangesOnlyTheTargetRootCacheIdentity() {
		Object library = new Object();
		String tvBefore = cacheId(library, "TV", "VTV1");
		String radioBefore = cacheId(library, "Radio", "VTV1");

		assertEquals(1L, SearchFolder.invalidateSearchRevision(library, "TV"));
		String tvAfter = cacheId(library, "TV", "VTV1");
		String radioAfter = cacheId(library, "Radio", "VTV1");

		assertNotEquals(tvBefore, tvAfter);
		assertEquals(radioBefore, radioAfter);
	}

	@Test
	public void recreatedRootKeepsRevisionForTheSameLibraryAndRootId() {
		Object library = new Object();
		assertEquals(1L, SearchFolder.invalidateSearchRevision(library, "Radio"));
		assertEquals(2L, SearchFolder.invalidateSearchRevision(library, "Radio"));
		assertEquals(2L, SearchFolder.searchRevision(library, "Radio"));
	}

	@Test
	public void differentLibrariesDoNotShareSourceRevisions() {
		Object first = new Object();
		Object second = new Object();
		SearchFolder.invalidateSearchRevision(first, "TV");

		assertEquals(1L, SearchFolder.searchRevision(first, "TV"));
		assertEquals(0L, SearchFolder.searchRevision(second, "TV"));
	}

	private static String cacheId(Object library, String rootId, String query) {
		return SearchFolder.cacheId(query, rootId,
				SearchFolder.searchRevision(library, rootId));
	}
}
