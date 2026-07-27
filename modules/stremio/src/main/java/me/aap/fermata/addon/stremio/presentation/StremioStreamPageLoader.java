package me.aap.fermata.addon.stremio.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.browse.StremioBrowseTarget;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.item.StremioPlaybackSelection;
import me.aap.fermata.addon.stremio.item.StremioStreamRequestFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptor;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.playback.StreamAggregationResult;
import me.aap.fermata.addon.stremio.playback.StremioStreamEligibilityPolicy;

/** Owns stream aggregation updates and immutable stream selection models. */
final class StremioStreamPageLoader {
	private final StremioItemGateway items;
	private final StremioPresentationText text;
	private final StremioPresentationTargetStore targets;

	StremioStreamPageLoader(StremioItemGateway items, StremioPresentationText text,
			StremioPresentationTargetStore targets) {
		this.items = Objects.requireNonNull(items, "items");
		this.text = Objects.requireNonNull(text, "text");
		this.targets = Objects.requireNonNull(targets, "targets");
	}

	CompletionStage<StremioPresentationPage> load(
			StremioPresentationRequest request, StremioRoute.Streams route) {
		BrowseMedia media = targets.media(route.stableId());
		BrowseEpisode episode = null;
		BrowseSeason season = null;
		if (media == null) {
			StremioPresentationTargetStore.EpisodeTarget target = targets.episode(route.stableId());
			if (target == null) return request.track(items.presentationTarget(route.stableId()))
					.thenCompose(restored -> loadRestored(request, route, restored));
			media = target.media();
			episode = target.episode();
			season = target.season();
		}
		StreamAggregationRequest streamRequest =
				StremioStreamRequestFactory.create(media, episode);
		return load(request, route.stableId(), media, episode, season, streamRequest);
	}

	CompletionStage<StremioPresentationPage> loadInlineMovie(
			StremioPresentationRequest request, StremioRoute.Details route,
			BrowseMedia media, StremioPresentationPage detailsPage) {
		request.ensureActive();
		request.publishUpdate(detailsPage);
		String routeKey = route.stableId();
		StreamAggregationRequest streamRequest = StremioStreamRequestFactory.create(media, null);
		CompletableFuture<StreamAggregationResult> terminalResult = new CompletableFuture<>();
		return request.track(items.streams(media.sourceUuid(), streamRequest, (result, error) -> {
			if (error != null) terminalResult.completeExceptionally(error);
			else terminalResult.complete(result);
		})).thenCompose(initial -> {
			request.ensureActive();
			StremioPresentationPage initialPage = append(detailsPage,
					page(request, routeKey, media, null, null, streamRequest, initial, false));
			if (!initial.hasPendingProviders()) {
				return CompletableFuture.completedFuture(initialPage);
			}
			request.publishUpdate(initialPage);
			return request.track(terminalResult).handle((result, error) -> {
				request.ensureActive();
				if (error != null) return append(initialPage, inlineFailure(routeKey));
				return append(detailsPage,
						page(request, routeKey, media, null, null, streamRequest, result, false));
			});
		}).exceptionally(error -> {
			request.ensureActive();
			return append(detailsPage, inlineFailure(routeKey));
		});
	}

	private CompletionStage<StremioPresentationPage> loadRestored(
			StremioPresentationRequest request, StremioRoute.Streams route,
			StremioBrowseTarget restored) {
		request.ensureActive();
		if (restored == null) return CompletableFuture.failedFuture(
				new IllegalStateException("Content selection expired"));
		if (restored.episode() == null) targets.putMedia(route.stableId(), restored.media());
		else targets.putEpisode(route.stableId(), restored.media(), restored.episode(),
				restored.season());
		StreamAggregationRequest streamRequest = StremioStreamRequestFactory.create(
				restored.media(), restored.episode());
		return load(request, route.stableId(), restored.media(), restored.episode(),
				restored.season(), streamRequest);
	}

	private CompletionStage<StremioPresentationPage> load(
			StremioPresentationRequest request, String routeKey,
			BrowseMedia media, BrowseEpisode episode, BrowseSeason season,
			StreamAggregationRequest streamRequest) {
		CompletableFuture<StreamAggregationResult> finalResult = new CompletableFuture<>();
		return request.track(items.streams(media.sourceUuid(), streamRequest, (result, error) -> {
			if (error != null) finalResult.completeExceptionally(error);
			else finalResult.complete(result);
		})).thenCompose(initial -> {
			StremioPresentationPage initialPage = page(request, routeKey, media,
					episode, season, streamRequest, initial, true);
			if (!initial.hasPendingProviders()) {
				return CompletableFuture.completedFuture(initialPage);
			}
			request.publishUpdate(initialPage);
			return request.track(finalResult).thenApply(result -> page(request,
					routeKey, media, episode, season, streamRequest, result, true));
		});
	}

	private StremioPresentationPage page(StremioPresentationRequest request, String routeKey,
			BrowseMedia media, BrowseEpisode episode, BrowseSeason season,
			StreamAggregationRequest streamRequest, StreamAggregationResult result,
			boolean providerFilter) {
		request.ensureActive();
		StremioPresentationPageBuilder builder = new StremioPresentationPageBuilder();
		if (providerFilter) addSubtitleAction(builder, routeKey, streamRequest);
		List<StreamAggregationResult.ProviderGroup> groups = result.providerGroups();
		String selectedProvider = providerFilter ? currentProvider(request) : "";
		if (!selectedProvider.isEmpty() && !containsProvider(groups, selectedProvider)) {
			selectedProvider = "";
		}
		if (providerFilter && (groups.size() > 1)) {
			addProviderFilter(builder, routeKey, groups, selectedProvider);
		}
		PlaybackDescriptor recommended = firstPlayable(result.orderedDescriptors());
		boolean hasDirectStreams = false;
		int groupIndex = 0;
		for (StreamAggregationResult.ProviderGroup group : groups) {
			if (!selectedProvider.isEmpty() &&
					!selectedProvider.equals(group.provider().sourceUuid())) continue;
			String groupKey = "stream-group:" + routeKey + ':' + groupIndex++;
			if ((group.status() != StreamAggregationResult.ProviderStatus.SUCCESS) ||
					!group.descriptors().isEmpty()) {
				builder.models.add(new StremioUiModel.StreamGroup(groupKey,
						group.provider().displayName(),
						StremioPresentationFormatter.providerState(group.status())));
			}
			if (group.status() != StreamAggregationResult.ProviderStatus.SUCCESS) continue;
			for (PlaybackDescriptor descriptor : group.descriptors()) {
				if (!isPlayable(descriptor)) continue;
				String key = StremioItemIds.stream(descriptor);
				builder.models.add(new StremioUiModel.StreamChoice(key,
						StremioPresentationFormatter.streamTitle(descriptor),
						StremioPresentationFormatter.streamDetails(descriptor),
						recommended != null && recommended.descriptorId()
								.equals(descriptor.descriptorId())));
				builder.selections.put(key, new StremioSelection.Play(key));
				hasDirectStreams = true;
				targets.putPlayback(key, new StremioPlaybackSelection(routeKey, media, episode,
						season, streamRequest, descriptor, targets.resumePosition(routeKey)));
			}
		}
		if (!hasDirectStreams && !result.hasPendingProviders()) {
			builder.models.add(StremioPresentationModels.state(
					"state:no-direct-streams:" + routeKey,
					text.label(StremioPresentationText.Label.NO_DIRECT_STREAMS),
					StremioUiModel.StateKind.EMPTY));
		}
		return builder.build();
	}

	void addSubtitleAction(StremioPresentationPageBuilder builder, String routeKey,
			StreamAggregationRequest request) {
		String key = "subtitles:" + routeKey;
		builder.models.add(new StremioUiModel.Action(key,
				text.action(StremioUiModel.ActionKind.SUBTITLES),
				StremioUiModel.ActionKind.SUBTITLES));
		builder.selections.put(key, new StremioSelection.Subtitles(key));
		targets.putSubtitle(key, new StremioPresentationGateway.SubtitleTarget(request));
	}

	private void addProviderFilter(StremioPresentationPageBuilder builder, String routeKey,
			List<StreamAggregationResult.ProviderGroup> groups, String selectedProvider) {
		List<StremioUiModel.Option> options = new ArrayList<>(groups.size() + 1);
		String allKey = "filter:provider:all";
		options.add(new StremioUiModel.Option(allKey,
				text.label(StremioPresentationText.Label.ALL_PROVIDERS)));
		builder.selections.put(allKey, new StremioSelection.Navigate(
				new StremioRoute.Streams(routeKey), true));
		String selectedKey = allKey;
		for (StreamAggregationResult.ProviderGroup group : groups) {
			String provider = group.provider().sourceUuid();
			String key = "filter:provider:" + StremioItemIds.provider(provider);
			options.add(new StremioUiModel.Option(key, providerFilterLabel(group)));
			builder.selections.put(key, new StremioSelection.Navigate(
					new StremioRoute.Streams(routeKey, provider), true));
			if (provider.equals(selectedProvider)) selectedKey = key;
		}
		builder.models.add(new StremioUiModel.Filter("filter:provider",
				text.label(StremioPresentationText.Label.PROVIDER), selectedKey, options));
	}

	private StremioPresentationPage inlineFailure(String routeKey) {
		String retryKey = "action:retry-inline-streams:" + routeKey;
		return new StremioPresentationPage(List.of(
				StremioPresentationModels.state("state:inline-streams:" + routeKey,
						text.label(StremioPresentationText.Label.NO_DIRECT_STREAMS),
						StremioUiModel.StateKind.ERROR),
				new StremioUiModel.Action(retryKey,
						text.action(StremioUiModel.ActionKind.RETRY),
						StremioUiModel.ActionKind.RETRY)),
				Map.of(retryKey,
						new StremioSelection.Command(StremioUiModel.ActionKind.RETRY)));
	}

	private static StremioPresentationPage append(
			StremioPresentationPage first, StremioPresentationPage second) {
		List<StremioUiModel> models = new ArrayList<>(
				first.models().size() + second.models().size());
		models.addAll(first.models());
		models.addAll(second.models());
		Map<String, StremioSelection> selections = new LinkedHashMap<>(first.selections());
		selections.putAll(second.selections());
		return new StremioPresentationPage(models, selections);
	}

	private static PlaybackDescriptor firstPlayable(List<PlaybackDescriptor> descriptors) {
		for (PlaybackDescriptor descriptor : descriptors) {
			if (isPlayable(descriptor)) return descriptor;
		}
		return null;
	}

	private static boolean isPlayable(PlaybackDescriptor descriptor) {
		return StremioStreamEligibilityPolicy.classify(descriptor) !=
				StremioStreamEligibilityPolicy.Kind.UNSUPPORTED;
	}

	private static String providerFilterLabel(StreamAggregationResult.ProviderGroup group) {
		String suffix = switch (group.status()) {
			case SUCCESS -> Integer.toString(group.descriptors().size());
			case PENDING -> "...";
			case FAILED -> "!";
			case TIMED_OUT -> "timeout";
		};
		return group.provider().displayName() + " | " + suffix;
	}

	private static String currentProvider(StremioPresentationRequest request) {
		StremioRoute route = request.route();
		return (route instanceof StremioRoute.Streams streams) ?
				streams.providerSourceUuid() : "";
	}

	private static boolean containsProvider(
			List<StreamAggregationResult.ProviderGroup> groups, String sourceUuid) {
		for (StreamAggregationResult.ProviderGroup group : groups) {
			if (sourceUuid.equals(group.provider().sourceUuid())) return true;
		}
		return false;
	}
}
