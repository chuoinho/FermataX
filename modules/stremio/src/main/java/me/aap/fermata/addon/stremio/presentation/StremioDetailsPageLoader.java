package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.item.StremioStreamRequestFactory;
import me.aap.fermata.addon.stremio.session.StremioProgressState;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

/** Builds Details pages and enriches them with persisted session state. */
final class StremioDetailsPageLoader {
	private final StremioItemGateway items;
	private final StremioSessionCoordinator sessions;
	private final StremioPresentationText text;
	private final StremioPresentationFormatter formatter;
	private final StremioPresentationTargetStore targets;
	private final StremioStreamPageLoader streams;

	StremioDetailsPageLoader(StremioItemGateway items, StremioSessionCoordinator sessions,
			StremioPresentationText text, StremioPresentationFormatter formatter,
			StremioPresentationTargetStore targets, StremioStreamPageLoader streams) {
		this.items = Objects.requireNonNull(items, "items");
		this.sessions = sessions;
		this.text = Objects.requireNonNull(text, "text");
		this.formatter = Objects.requireNonNull(formatter, "formatter");
		this.targets = Objects.requireNonNull(targets, "targets");
		this.streams = Objects.requireNonNull(streams, "streams");
	}

	CompletionStage<StremioPresentationPage> load(
			StremioPresentationRequest request, StremioRoute.Details route) {
		BrowseMedia media = targets.media(route.stableId());
		if (media == null) return request.track(items.presentationTarget(route.stableId()))
				.thenCompose(restored -> {
					if (restored == null) return CompletableFuture.failedFuture(
							new IllegalStateException("Content selection expired"));
					BrowseMedia restoredMedia = restored.media();
					targets.putMedia(route.stableId(), restoredMedia);
					return request.track(items.meta(restoredMedia)).thenCompose(details -> {
						request.ensureActive();
						verifyIdentity(restoredMedia, details.media());
						return enrich(request, route, details);
					});
				});
		return request.track(items.meta(media)).thenCompose(details -> {
			request.ensureActive();
			verifyIdentity(media, details.media());
			return enrich(request, route, details);
		});
	}

	private CompletionStage<StremioPresentationPage> enrich(
			StremioPresentationRequest request, StremioRoute.Details route,
			BrowseDetails details) {
		CompletionStage<StremioPresentationPage> detailsStage;
		if (sessions == null) {
			detailsStage = CompletableFuture.completedFuture(
					page(route, details, Map.of(), Map.of(), Map.of(), Map.of()));
		} else {
			CompletableFuture<Boolean> prepared =
					request.track(items.preparePersistentItem(details.media(), null))
							.handle((value, failure) -> failure == null);
			detailsStage = prepared.thenCompose(preparedSuccessfully -> {
				request.ensureActive();
				Map<String, String> routeToPersistent = persistentIds(details);
				List<String> persistentIds = List.copyOf(routeToPersistent.values());
				CompletableFuture<Map<String, StremioProgressState>> progress = request.track(
						sessions.loadProgressBatch(persistentIds));
				CompletableFuture<Map<String, Boolean>> favorites = request.track(
						sessions.loadFavoriteStates(persistentIds));
				CompletableFuture<Map<String, StremioSessionItem>> available = request.track(
						sessions.loadItemsBatch(persistentIds));
				return progress.thenCombine(favorites, DetailsState::new)
						.thenCombine(available, (state, loadedItems) -> {
							request.ensureActive();
							for (var entry : routeToPersistent.entrySet()) {
								StremioProgressState value = state.progress().get(entry.getValue());
								if ((value != null) && value.resumable()) {
									targets.rememberResume(entry.getKey(), value.positionMs(),
											value.durationMs());
								} else {
									targets.forgetResume(entry.getKey());
								}
							}
							StremioPresentationPage page = page(route, details,
									routeToPersistent, state.progress(), state.favorites(), loadedItems);
							return preparedSuccessfully ? page : withFavoriteFailure(page);
						});
			});
		}
		return detailsStage.thenCompose(page -> details.series() ?
				CompletableFuture.completedFuture(page) :
				streams.loadInlineMovie(request, route, details.media(), page));
	}

	private StremioPresentationPage page(StremioRoute.Details route,
			BrowseDetails details, Map<String, String> routeToPersistent,
			Map<String, StremioProgressState> progress, Map<String, Boolean> favorites,
			Map<String, StremioSessionItem> available) {
		BrowseMedia media = details.media();
		StremioPresentationPageBuilder builder = new StremioPresentationPageBuilder();
		String headerKey = "details:" + route.stableId();
		StremioPresentationTargetStore.EpisodeTarget resumeEpisode =
				latestResumeEpisode(details, routeToPersistent, progress);
		boolean resumable = (targets.resumePosition(route.stableId()) > 0L) ||
				(resumeEpisode != null);
		String persistentId = routeToPersistent.get(route.stableId());
		boolean favoriteSupported =
				(persistentId != null) && available.containsKey(persistentId);
		boolean favorite = favoriteSupported && Boolean.TRUE.equals(favorites.get(persistentId));
		targets.removeFavorite(headerKey);
		targets.removeSubtitle(headerKey);
		builder.models.add(new StremioUiModel.DetailsHeader(headerKey, media.title(),
				formatter.metadata(media), optional(media.description()), optional(media.poster()),
				optional(media.background()), details.series() && (resumeEpisode != null), resumable,
				favoriteSupported, favorite, false));
		if (favoriteSupported) {
			targets.putFavorite(headerKey,
					new StremioPresentationGateway.FavoriteTarget(persistentId, favorite));
		}
		if (!details.series()) {
			streams.addSubtitleAction(builder, route.stableId(),
					StremioStreamRequestFactory.create(media, null));
			return builder.build();
		}
		if (resumeEpisode != null) {
			String episodeKey = targets.rememberEpisode(resumeEpisode.media(),
					resumeEpisode.episode(), resumeEpisode.season());
			builder.selections.put(headerKey,
					new StremioSelection.Navigate(new StremioRoute.Streams(episodeKey)));
		}

		List<BrowseSeason> seasons = details.seasons().stream()
				.sorted(Comparator.comparingInt(season ->
						(season.number() == 0) ? Integer.MAX_VALUE : season.number())).toList();
		if (seasons.isEmpty()) {
			builder.models.add(StremioPresentationModels.state(
					"state:no-episodes:" + route.stableId(),
					text.label(StremioPresentationText.Label.NO_CONTENT),
					StremioUiModel.StateKind.EMPTY));
			return builder.build();
		}
		int selectedNumber = (route.season() >= 0) ? route.season() :
				(resumeEpisode == null ? -1 : resumeEpisode.season().number());
		BrowseSeason selected = seasons.stream()
				.filter(season -> season.number() == selectedNumber).findFirst()
				.orElse(seasons.get(0));
		List<StremioUiModel.Option> options = new ArrayList<>(seasons.size());
		for (BrowseSeason season : seasons) {
			String key = "season:" + route.stableId() + ':' + season.number();
			options.add(new StremioUiModel.Option(key,
					text.label(StremioPresentationText.Label.SEASON) + ' ' + season.number()));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Details(route.stableId(), season.number()), true));
		}
		String selectedKey = "season:" + route.stableId() + ':' + selected.number();
		builder.models.add(new StremioUiModel.Filter("filter:season:" + route.stableId(),
				text.label(StremioPresentationText.Label.SEASON), selectedKey, options));
		for (BrowseEpisode episode : selected.episodes()) {
			String episodeKey = targets.rememberEpisode(media, episode, selected);
			String number = "S" + episode.season() + " E" + episode.episode();
			String artwork = (episode.thumbnail() == null) ? media.poster() : episode.thumbnail();
			builder.models.add(new StremioUiModel.Episode(episodeKey, number,
					episode.title(), StremioPresentationFormatter.episodeMetadata(episode),
					optional(artwork), targets.resumeProgress(episodeKey, episode.duration())));
			builder.selections.put(episodeKey,
					new StremioSelection.Navigate(new StremioRoute.Streams(episodeKey)));
		}
		return builder.build();
	}

	private StremioPresentationPage withFavoriteFailure(StremioPresentationPage page) {
		List<StremioUiModel> models = new ArrayList<>(page.models());
		Map<String, StremioSelection> selections = new LinkedHashMap<>(page.selections());
		models.add(StremioPresentationModels.state("state:favorite-unavailable",
				text.label(StremioPresentationText.Label.FAVORITE_UNAVAILABLE),
				StremioUiModel.StateKind.WARNING));
		String retryKey = "action:retry-favorite";
		models.add(new StremioUiModel.Action(retryKey,
				text.action(StremioUiModel.ActionKind.RETRY), StremioUiModel.ActionKind.RETRY));
		selections.put(retryKey, new StremioSelection.Command(StremioUiModel.ActionKind.RETRY));
		return new StremioPresentationPage(models, selections);
	}

	private static StremioPresentationTargetStore.EpisodeTarget latestResumeEpisode(
			BrowseDetails details, Map<String, String> routeToPersistent,
			Map<String, StremioProgressState> progress) {
		StremioPresentationTargetStore.EpisodeTarget latest = null;
		long lastPlayed = -1L;
		for (BrowseSeason season : details.seasons()) {
			for (BrowseEpisode episode : season.episodes()) {
				String routeId = StremioItemIds.episode(episode);
				String persistentId = routeToPersistent.get(routeId);
				StremioProgressState state = (persistentId == null) ? null :
						progress.get(persistentId);
				if ((state != null) && state.resumable() &&
						(state.lastPlayedMs() > lastPlayed)) {
					latest = new StremioPresentationTargetStore.EpisodeTarget(
							details.media(), episode, season);
					lastPlayed = state.lastPlayedMs();
				}
			}
		}
		return latest;
	}

	private static Map<String, String> persistentIds(BrowseDetails details) {
		Map<String, String> result = new LinkedHashMap<>();
		BrowseMedia media = details.media();
		result.put(StremioItemIds.meta(media),
				StremioStreamRequestFactory.create(media, null).identity().videoKey());
		if (!details.series()) return Map.copyOf(result);
		for (BrowseSeason season : details.seasons()) {
			for (BrowseEpisode episode : season.episodes()) {
				result.put(StremioItemIds.episode(episode),
						StremioStreamRequestFactory.create(media, episode).identity().videoKey());
			}
		}
		return Map.copyOf(result);
	}

	private static void verifyIdentity(BrowseMedia expected, BrowseMedia actual) {
		if (!expected.sourceUuid().equals(actual.sourceUuid()) ||
				!expected.type().equals(actual.type()) || !expected.id().equals(actual.id())) {
			throw new IllegalStateException("Stremio metadata identity mismatch");
		}
	}

	private static String optional(String value) {
		return Objects.requireNonNullElse(value, "");
	}

	private record DetailsState(Map<String, StremioProgressState> progress,
			Map<String, Boolean> favorites) {
	}
}
