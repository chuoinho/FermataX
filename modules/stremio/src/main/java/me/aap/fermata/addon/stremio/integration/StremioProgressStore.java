package me.aap.fermata.addon.stremio.integration;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

import me.aap.fermata.addon.stremio.data.StremioProgressRecord;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.session.StremioPlaybackOwnership;
import me.aap.fermata.addon.stremio.session.StremioProgressSnapshot;
import me.aap.fermata.addon.stremio.session.StremioProgressWriteResult;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.utils.async.FutureSupplier;

/** Owns playback generation activation and all durable progress writes. */
final class StremioProgressStore {
	private final StremioRepository repository;
	private final StremioSessionCoordinator sessions;
	private final BooleanSupplier closed;
	private final Object lock = new Object();
	private long legacyGeneration;
	private long ownershipGeneration = -1L;
	private String stableId;
	private CompletableFuture<StremioPlaybackOwnership> ownership;

	StremioProgressStore(StremioRepository repository, StremioSessionCoordinator sessions,
			BooleanSupplier closed) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.closed = Objects.requireNonNull(closed, "closed");
	}

	FutureSupplier<Void> save(StremioPlaybackIdentity identity,
			long position, boolean completed) {
		long generation;
		synchronized (lock) {
			generation = ++legacyGeneration;
		}
		return save(identity, position, completed, generation);
	}

	FutureSupplier<Void> save(StremioPlaybackIdentity identity, long position,
			boolean completed, long playbackGeneration) {
		Objects.requireNonNull(identity, "identity");
		if (playbackGeneration < 0L) return StremioFutureBridge.from(
				CompletableFuture.failedFuture(
						new IllegalArgumentException("Negative playback generation")));
		long normalized = completed ? 0L : Math.max(position, 0L);
		long now = System.currentTimeMillis();
		CompletionStage<Void> write = ownership(identity.videoKey(), playbackGeneration, now)
				.thenCompose(value -> {
					var snapshot = sessions.snapshotProgressFromCore(
							value, normalized, completed, now);
					if (snapshot.isEmpty()) return CompletableFuture.completedFuture(null);
					return sessions.persistCoreProgress(snapshot.get()).thenApply(result -> {
						if (result != StremioProgressWriteResult.WRITTEN &&
								result != StremioProgressWriteResult.REJECTED_STALE) {
							throw new IllegalStateException("Unexpected Stremio progress result");
						}
						return null;
					});
				}).handle((value, failure) -> {
					if ((failure == null) || stale(failure)) {
						return CompletableFuture.<Void>completedFuture(null);
					}
					return CompletableFuture.<Void>failedFuture(unwrap(failure));
				}).thenCompose(stage -> stage);
		return StremioFutureBridge.from(write);
	}

	CompletionStage<Void> write(StremioProgressSnapshot snapshot) {
		return open(repository.getVideo(snapshot.stableId())).thenCompose(video -> {
			if (video == null) return CompletableFuture.failedFuture(
					new IllegalStateException("Stremio progress item is unavailable"));
			return open(repository.putProgress(new StremioProgressRecord(video.videoKey(),
					snapshot.positionMs(), video.durationMs(), snapshot.completed(),
					snapshot.updatedAtMs(), snapshot.updatedAtMs())));
		});
	}

	void close() {
		synchronized (lock) {
			stableId = null;
			ownership = null;
			ownershipGeneration = -1L;
		}
	}

	private CompletionStage<StremioPlaybackOwnership> ownership(
			String requestedStableId, long generation, long nowMs) {
		synchronized (lock) {
			if (closed.getAsBoolean()) return CompletableFuture.failedFuture(
					new IllegalStateException("Stremio runtime is closed"));
			if (requestedStableId.equals(stableId) && (generation == ownershipGeneration) &&
					(ownership != null)) return ownership;

			stableId = requestedStableId;
			ownershipGeneration = generation;
			CompletableFuture<StremioPlaybackOwnership> activation = sessions
					.activatePlayback(requestedStableId, generation, nowMs).toCompletableFuture();
			ownership = activation;
			activation.whenComplete((value, failure) -> {
				if (failure == null) return;
				synchronized (lock) {
					if (ownership == activation) {
						ownership = null;
						ownershipGeneration = -1L;
					}
				}
			});
			return activation;
		}
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> future) {
		return closed.getAsBoolean() ? CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed")) : future;
	}

	private static boolean stale(Throwable failure) {
		return unwrap(failure) instanceof StremioSessionCoordinator.StaleSessionException;
	}

	private static Throwable unwrap(Throwable failure) {
		while (((failure instanceof CompletionException) ||
				(failure instanceof java.util.concurrent.ExecutionException)) &&
				(failure.getCause() != null)) failure = failure.getCause();
		return failure;
	}
}
