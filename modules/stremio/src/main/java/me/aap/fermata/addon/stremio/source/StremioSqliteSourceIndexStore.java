package me.aap.fermata.addon.stremio.source;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioRepository.SourceState;

/** Production source index stored in the same SQLite transaction as provider rows. */
public final class StremioSqliteSourceIndexStore implements StremioSourceIndexStore.Transactional {
	private final StremioRepository repository;

	public StremioSqliteSourceIndexStore(StremioRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public CompletableFuture<Index> load() {
		return repository.getSourceState().thenApply(StremioSqliteSourceIndexStore::index);
	}

	@Override
	public CompletableFuture<Boolean> compareAndSet(Index expected, Index replacement) {
		return repository.getSourceState().thenCompose(current -> {
			if (!index(current).equals(expected)) return CompletableFuture.completedFuture(false);
			if (!expected.orderedSourceUuids().equals(replacement.orderedSourceUuids())) {
				return CompletableFuture.failedFuture(new IllegalArgumentException(
						"Row changes require compareAndSetSnapshot"));
			}
			SourceState next = new SourceState(replacement.revision(), current.sources(),
					replacement.cinemetaInstallHandled());
			return repository.compareAndSetSourceState(current, next);
		});
	}

	@Override
	public CompletableFuture<StremioSourceSnapshot> loadSnapshot() {
		return repository.getSourceState().thenApply(StremioSqliteSourceIndexStore::snapshot);
	}

	@Override
	public CompletableFuture<Boolean> compareAndSetSnapshot(
			StremioSourceSnapshot expected, StremioSourceSnapshot replacement) {
		return repository.compareAndSetSourceState(state(expected), state(replacement));
	}

	private static Index index(SourceState state) {
		return new Index(state.revision(), state.sources().stream()
				.map(source -> source.sourceUuid()).toList(), state.cinemetaInstallHandled());
	}

	private static SourceState state(StremioSourceSnapshot snapshot) {
		return new SourceState(snapshot.revision(), snapshot.sources(),
				snapshot.cinemetaInstallHandled());
	}

	private static StremioSourceSnapshot snapshot(SourceState state) {
		return new StremioSourceSnapshot(state.revision(), state.sources(),
				state.cinemetaInstallHandled());
	}
}
