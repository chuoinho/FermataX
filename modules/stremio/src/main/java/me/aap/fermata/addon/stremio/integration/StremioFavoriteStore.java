package me.aap.fermata.addon.stremio.integration;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.session.StremioFavoriteUpdate;
import me.aap.fermata.addon.stremio.session.StremioProviderState;

/** Owns provider state and durable favorite-retention synchronization. */
final class StremioFavoriteStore {
	private final StremioRepository repository;
	private final BooleanSupplier closed;
	private final Function<String, CompletionStage<StremioPersistedItem>> projectionLoader;
	private final Function<StremioPersistedItem, CompletionStage<Void>> projectionWriter;

	StremioFavoriteStore(StremioRepository repository, BooleanSupplier closed,
			Function<String, CompletionStage<StremioPersistedItem>> projectionLoader,
			Function<StremioPersistedItem, CompletionStage<Void>> projectionWriter) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.closed = Objects.requireNonNull(closed, "closed");
		this.projectionLoader = Objects.requireNonNull(projectionLoader, "projectionLoader");
		this.projectionWriter = Objects.requireNonNull(projectionWriter, "projectionWriter");
	}

	CompletionStage<StremioProviderState> providerState(String sourceUuid) {
		return open(repository.getSource(sourceUuid)).thenApply(source -> {
			if (source == null) return StremioProviderState.REMOVED;
			return source.enabled() ? StremioProviderState.ENABLED :
					StremioProviderState.DISABLED;
		});
	}

	CompletionStage<Void> synchronize(StremioFavoriteUpdate update) {
		if (!update.favorite()) {
			return open(repository.setFavoriteRetention(update.stableId(), false,
					System.currentTimeMillis()));
		}
		return projectionLoader.apply(update.stableId()).thenCompose(projection -> {
			if (projection == null) return CompletableFuture.completedFuture(null);
			if (!projection.item().canonicalContentKey().equals(update.canonicalContentKey())) {
				return CompletableFuture.failedFuture(
						new IllegalStateException("Favorite content identity mismatch"));
			}
			return projectionWriter.apply(projection).thenCompose(ignored ->
					open(repository.setFavoriteRetention(update.stableId(), true,
							System.currentTimeMillis())));
		});
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> future) {
		return closed.getAsBoolean() ? CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed")) : future;
	}
}
