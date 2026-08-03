package me.aap.fermata.addon.stremio.integration;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioSessionRecord;
import me.aap.fermata.addon.stremio.session.StremioRestorePoint;

/** Owns process-restoration persistence and destination identity validation. */
final class StremioRestoreStore {
	private final StremioRepository repository;
	private final BooleanSupplier closed;
	private final Function<String, CompletionStage<StremioPersistedItem>> projectionLoader;

	StremioRestoreStore(StremioRepository repository, BooleanSupplier closed,
			Function<String, CompletionStage<StremioPersistedItem>> projectionLoader) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.closed = Objects.requireNonNull(closed, "closed");
		this.projectionLoader = Objects.requireNonNull(projectionLoader, "projectionLoader");
	}

	CompletionStage<Void> save(StremioRestorePoint restorePoint) {
		return projectionLoader.apply(restorePoint.stableId()).thenCompose(projection -> {
			if (projection == null) return StremioFutures.failedFuture(
					new IllegalStateException("Restore item is unavailable"));
			if (!projection.item().backToListId().equals(restorePoint.backToListId())) {
				return StremioFutures.failedFuture(
						new IllegalStateException("Restore destination identity mismatch"));
			}
			return open(repository.putSessionState(new StremioSessionRecord(
					restorePoint.stableId(), restorePoint.backToListId(),
					restorePoint.playbackGeneration(), restorePoint.updatedAtMs())));
		});
	}

	CompletionStage<StremioRestorePoint> load() {
		return open(repository.getSessionState()).thenApply(session ->
				(session == null) ? null : new StremioRestorePoint(session.videoKey(),
						session.backToListId(), session.playbackGeneration(), session.updatedMs()));
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> future) {
		return closed.getAsBoolean() ? StremioFutures.failedFuture(
				new IllegalStateException("Stremio runtime is closed")) : future;
	}
}
