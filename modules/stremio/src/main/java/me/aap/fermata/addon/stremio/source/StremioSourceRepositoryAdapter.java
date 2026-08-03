package me.aap.fermata.addon.stremio.source;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.source.StremioSourceException.Code;
import me.aap.fermata.addon.stremio.source.StremioSourceIndexStore.Index;

/**
 * Serialized adapter over the existing row repository and a durable order/marker index.
 * Multi-row failures are compensated before the failed future is exposed.
 */
public final class StremioSourceRepositoryAdapter implements StremioSourceStore {
	private final StremioRepository repository;
	private final StremioSourceIndexStore indexStore;
	private final Object serialLock = new Object();
	private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

	/** Production constructor: source rows and index metadata share one SQLite transaction. */
	public StremioSourceRepositoryAdapter(StremioRepository repository) {
		this(repository, new StremioSqliteSourceIndexStore(repository));
	}

	public StremioSourceRepositoryAdapter(
			StremioRepository repository, StremioSourceIndexStore indexStore) {
		this.repository = java.util.Objects.requireNonNull(repository, "repository");
		this.indexStore = java.util.Objects.requireNonNull(indexStore, "indexStore");
	}

	@Override
	public CompletableFuture<StremioSourceSnapshot> load() {
		return serialized(this::loadUnserialized);
	}

	@Override
	public CompletableFuture<Void> commit(
			StremioSourceSnapshot expected, StremioSourceSnapshot replacement) {
		java.util.Objects.requireNonNull(expected, "expected");
		java.util.Objects.requireNonNull(replacement, "replacement");
		if (replacement.revision() != expected.revision() + 1) {
			return StremioFutures.failedFuture(new IllegalArgumentException(
					"Replacement revision must advance exactly once"));
		}
		return serialized(() -> commitUnserialized(expected, replacement));
	}

	private CompletableFuture<StremioSourceSnapshot> loadUnserialized() {
		if (indexStore instanceof StremioSourceIndexStore.Transactional transactional) {
			return transactional.loadSnapshot();
		}
		return indexStore.load().thenCompose(index -> {
			List<CompletableFuture<StremioSourceRecord>> reads = new ArrayList<>();
			for (String sourceUuid : index.orderedSourceUuids()) {
				reads.add(repository.getSource(sourceUuid));
			}
			return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
					.thenApply(ignored -> {
						List<StremioSourceRecord> sources = new ArrayList<>(reads.size());
						for (int i = 0; i < reads.size(); i++) {
							StremioSourceRecord source = reads.get(i).join();
							if ((source == null) || (source.position() != i)) {
								throw new StremioSourceException(Code.PERSISTENCE);
							}
							sources.add(source);
						}
						return new StremioSourceSnapshot(index.revision(), sources,
								index.cinemetaInstallHandled());
					});
		});
	}

	private CompletableFuture<Void> commitUnserialized(
			StremioSourceSnapshot expected, StremioSourceSnapshot replacement) {
		if (indexStore instanceof StremioSourceIndexStore.Transactional transactional) {
			return transactional.compareAndSetSnapshot(expected, replacement)
					.thenCompose(updated -> updated ? CompletableFuture.completedFuture(null) :
							StremioFutures.failedFuture(new StremioSourceException(
									Code.CONCURRENT_MODIFICATION)));
		}
		return loadUnserialized().thenCompose(current -> {
			if (!current.equals(expected)) {
				return StremioFutures.failedFuture(
						new StremioSourceException(Code.CONCURRENT_MODIFICATION));
			}

			return applyRows(expected, replacement)
					.thenCompose(ignored -> indexStore.compareAndSet(index(expected), index(replacement)))
					.thenCompose(updated -> updated ? CompletableFuture.completedFuture(null) :
							StremioFutures.failedFuture(
									new StremioSourceException(Code.CONCURRENT_MODIFICATION)))
					.handle((ignored, failure) -> failure)
					.thenCompose(failure -> {
						if (failure == null) return CompletableFuture.completedFuture(null);
						Throwable original = unwrap(failure);
						return restoreRows(expected, replacement).handle((unused, rollbackFailure) -> {
							if (rollbackFailure != null) {
								StremioSourceException rollback = new StremioSourceException(
										Code.ROLLBACK, unwrap(rollbackFailure));
								rollback.addSuppressed(original);
								throw rollback;
							}
							throw asDomainFailure(original);
						});
					});
		});
	}

	private CompletableFuture<Void> applyRows(
			StremioSourceSnapshot expected, StremioSourceSnapshot replacement) {
		Map<String, StremioSourceRecord> next = byId(replacement.sources());
		CompletableFuture<Void> operation = CompletableFuture.completedFuture(null);
		for (StremioSourceRecord source : replacement.sources()) {
			operation = operation.thenCompose(ignored -> repository.putSource(source));
		}
		for (StremioSourceRecord source : expected.sources()) {
			if (!next.containsKey(source.sourceUuid())) {
				operation = operation.thenCompose(ignored -> repository.deleteSource(source.sourceUuid())
						.thenApply(deleted -> null));
			}
		}
		return operation;
	}

	private CompletableFuture<Void> restoreRows(
			StremioSourceSnapshot expected, StremioSourceSnapshot attempted) {
		Set<String> originalIds = new HashSet<>();
		CompletableFuture<Void> rollback = CompletableFuture.completedFuture(null);
		for (StremioSourceRecord source : expected.sources()) {
			originalIds.add(source.sourceUuid());
			rollback = rollback.thenCompose(ignored -> repository.putSource(source));
		}
		for (StremioSourceRecord source : attempted.sources()) {
			if (!originalIds.contains(source.sourceUuid())) {
				rollback = rollback.thenCompose(ignored -> repository.deleteSource(source.sourceUuid())
						.thenApply(deleted -> null));
			}
		}
		return rollback;
	}

	private <T> CompletableFuture<T> serialized(Supplier<CompletableFuture<T>> operation) {
		synchronized (serialLock) {
			CompletableFuture<T> result = tail.handle((ignored, failure) -> null)
					.thenCompose(ignored -> operation.get());
			tail = result.handle((ignored, failure) -> null);
			return result;
		}
	}

	private static Index index(StremioSourceSnapshot snapshot) {
		return new Index(snapshot.revision(), snapshot.sources().stream()
				.map(StremioSourceRecord::sourceUuid).toList(), snapshot.cinemetaInstallHandled());
	}

	private static Map<String, StremioSourceRecord> byId(List<StremioSourceRecord> sources) {
		Map<String, StremioSourceRecord> result = new HashMap<>();
		for (StremioSourceRecord source : sources) result.put(source.sourceUuid(), source);
		return result;
	}

	private static RuntimeException asDomainFailure(Throwable failure) {
		return (failure instanceof StremioSourceException sourceFailure) ? sourceFailure :
				new StremioSourceException(Code.PERSISTENCE, failure);
	}

	private static Throwable unwrap(Throwable failure) {
		while ((failure instanceof CompletionException) && (failure.getCause() != null)) {
			failure = failure.getCause();
		}
		return failure;
	}
}
