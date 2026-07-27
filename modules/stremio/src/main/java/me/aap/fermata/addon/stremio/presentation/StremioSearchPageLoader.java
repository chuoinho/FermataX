package me.aap.fermata.addon.stremio.presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;

/** Builds the immutable Search page without owning navigation or Android views. */
final class StremioSearchPageLoader {
	private final StremioItemGateway items;
	private final StremioPresentationText text;
	private final Function<BrowseMedia, String> rememberMedia;

	StremioSearchPageLoader(StremioItemGateway items, StremioPresentationText text,
			Function<BrowseMedia, String> rememberMedia) {
		this.items = Objects.requireNonNull(items, "items");
		this.text = Objects.requireNonNull(text, "text");
		this.rememberMedia = Objects.requireNonNull(rememberMedia, "rememberMedia");
	}

	CompletionStage<StremioPresentationPage> load(
			StremioPresentationRequest request, StremioRoute.Search route) {
		return request.track(items.search(route.query())).thenApply(results -> {
			request.ensureActive();
			StremioPresentationPageBuilder builder = new StremioPresentationPageBuilder();
			for (BrowseMedia media : deduplicate(results.items())) {
				String detailsKey = rememberMedia.apply(media);
				String key = "search:" + detailsKey;
				builder.add(StremioPresentationModels.poster(key, media, 0f),
						new StremioSelection.Navigate(new StremioRoute.Details(detailsKey)));
			}
			if (builder.isEmpty()) {
				builder.add(StremioPresentationModels.state("state:no-content",
						text.label(StremioPresentationText.Label.NO_CONTENT),
						StremioUiModel.StateKind.EMPTY));
			}
			return builder.build();
		});
	}

	private static Iterable<BrowseMedia> deduplicate(Iterable<BrowseMedia> items) {
		Map<String, BrowseMedia> unique = new LinkedHashMap<>();
		for (BrowseMedia media : items) unique.putIfAbsent(StremioItemIds.meta(media), media);
		return unique.values();
	}
}
