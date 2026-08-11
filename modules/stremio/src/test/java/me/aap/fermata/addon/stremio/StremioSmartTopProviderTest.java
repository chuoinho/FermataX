package me.aap.fermata.addon.stremio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.SmartTopCandidate;
import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioLibraryItem;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

public class StremioSmartTopProviderTest {
	@Test
	public void unopenedRuntimeReturnsImmediatelyWithoutBootstrapping() {
		StremioAddon addon = new StremioAddon();
		var result = addon.loadCachedSmartTopCandidates("stremio", 7L);
		assertTrue(result.isDoneNotFailed());
		assertTrue(result.peek().isEmpty());
	}

	@Test
	public void cachedContinuePrecedesFavoriteRecommendationAndDeduplicates() {
		StremioSessionItem resumeItem = item("stremio:video:resume", "Resume");
		StremioSessionItem recommendedItem = item("stremio:video:recommended", "Recommended");
		StremioContinueEntry resume = new StremioContinueEntry(
				resumeItem, 120_000L, 600_000L, 500L);
		StremioLibraryItem duplicate = new StremioLibraryItem(
				resumeItem, "movie", 600L, null);
		StremioLibraryItem recommendation = new StremioLibraryItem(
				recommendedItem, "movie", 400L, null);

		List<SmartTopCandidate> result = StremioAddon.smartTopCandidates(
				"stremio", 7L, List.of(resume), List.of(duplicate, recommendation));
		assertEquals(2, result.size());
		assertEquals(SmartTopCandidate.Kind.RESUME, result.get(0).kind());
		assertEquals("stremio:video:resume", result.get(0).opaqueId());
		assertEquals(SmartTopCandidate.Kind.RECOMMENDED, result.get(1).kind());
		assertEquals("stremio:video:recommended", result.get(1).opaqueId());
		assertTrue(result.get(1).favoriteKnown());
	}

	@Test
	public void nearStartAndNearEndContinueRowsAreFiltered() {
		StremioSessionItem start = item("stremio:video:start", "Start");
		StremioSessionItem end = item("stremio:video:end", "End");
		List<SmartTopCandidate> result = StremioAddon.smartTopCandidates(
				"stremio", 7L,
				List.of(new StremioContinueEntry(start, 10_000L, 600_000L, 2L),
						new StremioContinueEntry(end, 570_000L, 600_000L, 1L)),
				List.of());
		assertTrue(result.isEmpty());
	}

	private static StremioSessionItem item(String stableId, String title) {
		return new StremioSessionItem(stableId, "meta:" + title.toLowerCase(),
				"source", title, "Movie", "", 600_000L,
				"catalog:movie", null, -1, -1);
	}
}
