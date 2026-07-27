package me.aap.fermata.addon.stremio.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;

public class StremioPresentationModelTest {
	@Test
	public void routesRejectTransportUrlsAndInvalidPagination() {
		assertThrows(IllegalArgumentException.class,
				() -> new StremioRoute.Details("https://provider.invalid/secret"));
		assertThrows(IllegalArgumentException.class,
				() -> new StremioRoute.Discover("stremio:catalog:popular", "", -1));
	}

	@Test
	public void modelsAreImmutableAndProgressIsBounded() {
		var poster = new StremioUiModel.Poster(
				"stremio:meta:a", "Movie", "2026", "poster", 0.5f);
		var section = new StremioUiModel.Section("section:popular", "Popular", List.of(poster));
		assertEquals(1, section.posters().size());
		assertThrows(UnsupportedOperationException.class, () -> section.posters().clear());
		assertThrows(IllegalArgumentException.class, () -> new StremioUiModel.Poster(
				"stremio:meta:b", "Movie", "", "", 1.1f));
	}

	@Test
	public void pageKeepsSelectionIdentitySeparateFromLabels() {
		var poster = new StremioUiModel.Poster(
				"stremio:meta:a", "Same title", "2026", "poster", 0f);
		var target = new StremioSelection.Navigate(
				new StremioRoute.Details("stremio:meta:a"));
		var page = new StremioPresentationPage(List.of(poster),
				java.util.Map.of(poster.stableKey(), target));

		assertEquals(target, page.selections().get("stremio:meta:a"));
		assertThrows(UnsupportedOperationException.class,
				() -> page.selections().clear());
	}

	@Test
	public void viewportRejectsTransportKeysAndNegativeOffsets() {
		assertThrows(IllegalArgumentException.class, () ->
				new StremioViewportState("https://provider.invalid", 0, java.util.Map.of()));
		assertThrows(IllegalArgumentException.class, () ->
				new StremioViewportState("poster", -1, java.util.Map.of()));
		assertThrows(IllegalArgumentException.class, () ->
				new StremioViewportState("poster", 0,
						java.util.Map.of("section", -1)));
	}
}
