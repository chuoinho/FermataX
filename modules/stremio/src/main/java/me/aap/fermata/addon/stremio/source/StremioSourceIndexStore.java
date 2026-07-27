package me.aap.fermata.addon.stremio.source;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Durable provider order and one-time default marker stored outside source rows. */
public interface StremioSourceIndexStore {
	CompletableFuture<Index> load();

	/** Implementations must return false without changing state when expected does not match. */
	CompletableFuture<Boolean> compareAndSet(Index expected, Index replacement);

	/** SQLite implementations use this boundary to commit rows and index in one transaction. */
	interface Transactional extends StremioSourceIndexStore {
		CompletableFuture<StremioSourceSnapshot> loadSnapshot();

		CompletableFuture<Boolean> compareAndSetSnapshot(
				StremioSourceSnapshot expected, StremioSourceSnapshot replacement);
	}

	record Index(long revision, List<String> orderedSourceUuids,
			boolean cinemetaInstallHandled) {
		public Index {
			if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
			orderedSourceUuids = List.copyOf(Objects.requireNonNull(
					orderedSourceUuids, "orderedSourceUuids"));
			if (orderedSourceUuids.stream().distinct().count() != orderedSourceUuids.size()) {
				throw new IllegalArgumentException("Duplicate source UUID in index");
			}
		}
	}
}
