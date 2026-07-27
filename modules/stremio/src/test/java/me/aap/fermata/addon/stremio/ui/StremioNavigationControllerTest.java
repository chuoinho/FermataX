package me.aap.fermata.addon.stremio.ui;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.stremio.presentation.StremioUiModel;

public class StremioNavigationControllerTest {
	@Test
	public void modelTitlePreservesExistingStableKeyRules() {
		StremioUiModel.Poster poster = new StremioUiModel.Poster(
				"poster:one", "Movie", "2026", "", 0f);
		StremioUiModel.Action seeAll = new StremioUiModel.Action(
				"action:all", "See all", StremioUiModel.ActionKind.DISCOVER);
		StremioUiModel.Section section = new StremioUiModel.Section(
				"section:one", "Popular", List.of(poster), seeAll);

		assertEquals("Movie", StremioNavigationController.modelTitle(
				"poster:one", List.of(section)));
		assertEquals("Popular", StremioNavigationController.modelTitle(
				"action:all", List.of(section)));
		assertEquals("", StremioNavigationController.modelTitle(
				"missing", List.of(section)));
	}
}
