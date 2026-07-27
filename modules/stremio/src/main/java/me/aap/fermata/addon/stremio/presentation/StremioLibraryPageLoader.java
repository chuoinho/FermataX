package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioLibraryItem;
import me.aap.fermata.addon.stremio.session.StremioProgressState;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

/** Builds Library pages without owning navigation or presentation target state. */
final class StremioLibraryPageLoader {
	interface ResumeRecorder {
		void remember(String stableId, long positionMs, long durationMs);
	}

	private final StremioSessionCoordinator sessions;
	private final StremioPresentationText text;
	private final ResumeRecorder resumeRecorder;

	StremioLibraryPageLoader(StremioSessionCoordinator sessions,
			StremioPresentationText text, ResumeRecorder resumeRecorder) {
		this.sessions = sessions;
		this.text = Objects.requireNonNull(text, "text");
		this.resumeRecorder = Objects.requireNonNull(resumeRecorder, "resumeRecorder");
	}

	CompletionStage<StremioPresentationPage> load(
			StremioPresentationRequest request, StremioRoute.Library route) {
		if (sessions == null) return CompletableFuture.completedFuture(
				StremioPresentationPage.of(List.of(StremioPresentationModels.state(
						"state:library", text.label(
								StremioPresentationText.Label.LIBRARY_EMPTY),
						StremioUiModel.StateKind.EMPTY))));
		CompletableFuture<List<StremioLibraryItem>> favorites = request.track(
				sessions.loadLibraryFavorites(StremioSessionCoordinator.MAX_LIBRARY_ITEMS));
		CompletableFuture<List<StremioContinueEntry>> continued = request.track(
				sessions.loadContinue(StremioSessionCoordinator.MAX_CONTINUE_ITEMS));
		return favorites.thenCombine(continued, LibraryPayload::new).thenApply(payload -> {
			request.ensureActive();
			StremioPresentationPageBuilder builder = new StremioPresentationPageBuilder();
			addFilters(builder, route);
			addContinue(builder, route.type(), payload.continueItems());

			List<StremioLibraryItem> items = new ArrayList<>(payload.favorites());
			items.removeIf(item -> !matchesType(route.type(), item.isSeries()));
			if (route.sort() == StremioRoute.LibrarySort.TITLE) {
				items.sort(Comparator.comparing((StremioLibraryItem item) -> item.item().title(),
						String.CASE_INSENSITIVE_ORDER));
			} else {
				items.sort(Comparator.comparingLong(StremioLibraryItem::favoriteUpdatedMs)
						.reversed());
			}

			if (route.type() == StremioRoute.LibraryType.ALL) {
				addSection(builder, "library:movies",
						text.label(StremioPresentationText.Label.SAVED_MOVIES), items, false);
				addSection(builder, "library:series",
						text.label(StremioPresentationText.Label.SAVED_SERIES), items, true);
			} else {
				boolean series = route.type() == StremioRoute.LibraryType.SERIES;
				addSection(builder, series ? "library:series" : "library:movies",
						text.label(series ? StremioPresentationText.Label.SAVED_SERIES :
								StremioPresentationText.Label.SAVED_MOVIES), items, series);
			}
			if (builder.models.stream().noneMatch(StremioUiModel.Section.class::isInstance)) {
				builder.add(StremioPresentationModels.state("state:library-empty",
						text.label(StremioPresentationText.Label.LIBRARY_EMPTY),
						StremioUiModel.StateKind.EMPTY));
			}
			return builder.build();
		});
	}

	private void addFilters(StremioPresentationPageBuilder builder,
			StremioRoute.Library route) {
		List<StremioUiModel.Option> types = new ArrayList<>(3);
		for (StremioRoute.LibraryType type : StremioRoute.LibraryType.values()) {
			String key = "filter:library:type:" + type.name().toLowerCase(java.util.Locale.ROOT);
			StremioPresentationText.Label label = switch (type) {
				case ALL -> StremioPresentationText.Label.ALL;
				case MOVIES -> StremioPresentationText.Label.MOVIES;
				case SERIES -> StremioPresentationText.Label.SERIES;
			};
			types.add(new StremioUiModel.Option(key, text.label(label)));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Library(type, route.sort()), true));
		}
		builder.models.add(new StremioUiModel.Filter("filter:library:type",
				text.label(StremioPresentationText.Label.TYPE),
				"filter:library:type:" + route.type().name().toLowerCase(java.util.Locale.ROOT),
				types));

		List<StremioUiModel.Option> sorts = new ArrayList<>(2);
		for (StremioRoute.LibrarySort sort : StremioRoute.LibrarySort.values()) {
			String key = "filter:library:sort:" + sort.name().toLowerCase(java.util.Locale.ROOT);
			sorts.add(new StremioUiModel.Option(key, text.label(
					sort == StremioRoute.LibrarySort.RECENT ?
							StremioPresentationText.Label.RECENT :
							StremioPresentationText.Label.TITLE)));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Library(route.type(), sort), true));
		}
		builder.models.add(new StremioUiModel.Filter("filter:library:sort",
				text.label(StremioPresentationText.Label.SORT),
				"filter:library:sort:" + route.sort().name().toLowerCase(java.util.Locale.ROOT),
				sorts));
	}

	private void addContinue(StremioPresentationPageBuilder builder,
			StremioRoute.LibraryType type, List<StremioContinueEntry> entries) {
		List<StremioContinueEntry> filtered = entries.stream()
				.filter(entry -> matchesType(type, entry.item().isEpisode())).toList();
		if (filtered.isEmpty()) return;
		List<StremioUiModel.Poster> posters = new ArrayList<>(filtered.size());
		for (StremioContinueEntry entry : filtered) {
			resumeRecorder.remember(entry.item().stableId(), entry.positionMs(), entry.durationMs());
			String key = "continue:poster:" + entry.item().stableId();
			float progress = Math.max(0f, Math.min(1f,
					(float) entry.positionMs() / (float) entry.durationMs()));
			posters.add(new StremioUiModel.Poster(key, entry.item().title(),
					entry.item().subtitle(), optional(entry.item().artwork()), progress, true));
			builder.selections.put(key, new StremioSelection.Restore(
					entry.item().stableId(), true));
		}
		builder.models.add(new StremioUiModel.Section("section:continue",
				text.label(StremioPresentationText.Label.CONTINUE_WATCHING), posters));
	}

	private void addSection(StremioPresentationPageBuilder builder, String sectionKey,
			String title, List<StremioLibraryItem> items, boolean series) {
		List<StremioUiModel.Poster> posters = new ArrayList<>();
		for (StremioLibraryItem item : items) {
			if (item.isSeries() != series) continue;
			StremioSessionItem sessionItem = item.item();
			String key = sectionKey + ":poster:" + sessionItem.stableId();
			posters.add(new StremioUiModel.Poster(key, sessionItem.title(),
					sessionItem.subtitle(), optional(sessionItem.artwork()),
					progress(item.progress())));
			builder.selections.put(key, new StremioSelection.Restore(
					sessionItem.stableId(), sessionItem.isEpisode()));
		}
		if (!posters.isEmpty()) builder.models.add(new StremioUiModel.Section(
				sectionKey, title, posters));
	}

	private static boolean matchesType(StremioRoute.LibraryType type, boolean series) {
		return switch (type) {
			case ALL -> true;
			case MOVIES -> !series;
			case SERIES -> series;
		};
	}

	private static float progress(StremioProgressState state) {
		if ((state == null) || !state.resumable()) return 0f;
		return Math.max(0f, Math.min(1f,
				(float) state.positionMs() / (float) state.durationMs()));
	}

	private static String optional(String value) {
		return Objects.requireNonNullElse(value, "");
	}

	private record LibraryPayload(List<StremioLibraryItem> favorites,
			List<StremioContinueEntry> continueItems) {
	}
}
