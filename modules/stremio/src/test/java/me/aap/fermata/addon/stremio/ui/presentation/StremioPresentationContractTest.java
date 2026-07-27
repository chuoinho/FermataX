package me.aap.fermata.addon.stremio.ui.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;

import me.aap.fermata.addon.stremio.presentation.StremioUiModel;

public class StremioPresentationContractTest {
	@Test
	public void everyUiModelHasADedicatedViewType() {
		List<StremioUiModel> models = allModels();
		assertEquals(10, models.stream()
				.map(StremioPresentationContract::viewType)
				.distinct().count());
		assertEquals(StremioPresentationContract.ACTION,
				StremioPresentationContract.viewType(models.get(0)));
		assertEquals(StremioPresentationContract.STATE_ROW,
				StremioPresentationContract.viewType(models.get(9)));
	}

	@Test
	public void onlyStandalonePostersUseOneGridSpan() {
		for (StremioUiModel model : allModels()) {
			int expected = (model instanceof StremioUiModel.Poster) ? 1 : 5;
			assertEquals(expected, StremioPresentationContract.spanSize(model, 5));
		}
		assertThrows(IllegalArgumentException.class,
				() -> StremioPresentationContract.spanSize(allModels().get(0), 0));
	}

	@Test
	public void stableIdsIncludeTypeAndRemainDeterministic() {
		StremioUiModel.Action action = new StremioUiModel.Action(
				"shared:key", "Search", StremioUiModel.ActionKind.SEARCH);
		StremioUiModel.StateRow state = new StremioUiModel.StateRow(
				"shared:key", "Empty", StremioUiModel.StateKind.EMPTY);
		assertEquals(StremioPresentationContract.stableId(action),
				StremioPresentationContract.stableId(action));
		assertNotEquals(StremioPresentationContract.stableId(action),
				StremioPresentationContract.stableId(state));
	}

	private static List<StremioUiModel> allModels() {
		StremioUiModel.Poster poster = new StremioUiModel.Poster(
				"poster:one", "Movie", "2026", "", 0.25f);
		return List.of(
				new StremioUiModel.Action("action:search", "Search",
						StremioUiModel.ActionKind.SEARCH),
				new StremioUiModel.ActionBar("actions:home", List.of(
						new StremioUiModel.Action("action:discover", "Discover",
								StremioUiModel.ActionKind.DISCOVER))),
				new StremioUiModel.Section("section:popular", "Popular", List.of(poster)),
				poster,
				new StremioUiModel.Filter("filter:type", "Type", "movie",
						List.of(new StremioUiModel.Option("movie", "Movies"))),
				new StremioUiModel.DetailsHeader("details:one", "Movie", "2026",
						"Overview", "", "", true, false, true, false),
				new StremioUiModel.Episode("episode:one", "1", "Pilot", "42 min",
						"", 0f),
				new StremioUiModel.StreamGroup("stream-group:one", "Provider",
						StremioUiModel.ProviderState.READY),
				new StremioUiModel.StreamChoice("stream:one", "1080p", "HLS", true),
				new StremioUiModel.StateRow("state:empty", "No results",
						StremioUiModel.StateKind.EMPTY));
	}
}
