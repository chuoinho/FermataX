package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioFutureBridge.toCompletable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;
import me.aap.fermata.addon.stremio.session.StremioVoiceCandidate;

/** Resolves voice candidates and persists their durable navigation projections. */
final class StremioVoiceSearchResolver {
	private final Supplier<CompletionStage<List<BrowseProvider>>> providerSource;
	private final StremioItemGateway items;
	private final StremioProjectionStore projections;
	private final Executor executor;

	StremioVoiceSearchResolver(Supplier<CompletionStage<List<BrowseProvider>>> providerSource,
			StremioItemGateway items, StremioProjectionStore projections, Executor executor) {
		this.providerSource = Objects.requireNonNull(providerSource, "providerSource");
		this.items = Objects.requireNonNull(items, "items");
		this.projections = Objects.requireNonNull(projections, "projections");
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	CompletionStage<List<StremioVoiceCandidate>> search(
			String normalizedQuery, Locale locale, int limit) {
		Objects.requireNonNull(locale, "locale");
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());
		CompletableFuture<me.aap.fermata.addon.stremio.browse.SearchResults> search =
				toCompletable(items.search(normalizedQuery));
		return search.thenCombineAsync(providerSource.get().toCompletableFuture(),
				(results, available) -> {
					Map<String, Integer> ranks = new HashMap<>();
					available.forEach(provider ->
							ranks.put(provider.sourceUuid(), provider.position()));
					List<StremioPersistedItem> values = new ArrayList<>();
					for (BrowseMedia media : results.items()) {
						if (values.size() >= limit) break;
						StremioPersistedItem projection = projections.project(media);
						if (projection != null) values.add(projection);
					}
					return new SearchProjection(values, ranks);
				}, executor).thenCompose(projection -> {
			List<CompletableFuture<Void>> writes = projection.items().stream()
					.map(projections::persist).map(CompletionStage::toCompletableFuture)
					.toList();
			return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
					.thenApply(ignored -> projection.items().stream().map(item -> {
						StremioSessionItem value = item.item();
						return new StremioVoiceCandidate(value.stableId(), value.sourceUuid(),
								value.title(), value.subtitle(), projection.ranks().getOrDefault(
										value.sourceUuid(), Integer.MAX_VALUE));
					}).toList());
		});
	}
	private record SearchProjection(List<StremioPersistedItem> items,
			Map<String, Integer> ranks) {
	}
}
