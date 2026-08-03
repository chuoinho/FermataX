package me.aap.fermata.addon.stremio.data;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import android.database.Cursor;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class StremioRepository implements Closeable {
	static final int MAX_CACHE_ROWS = 512;
	static final long MAX_CACHE_BYTES = 16L * 1024L * 1024L;
	static final int MAX_CACHE_ENTRY_BYTES = 1024 * 1024;
	static final int MAX_META_ROWS = 4096;
	static final int MAX_VIDEO_ROWS = 8192;
	static final int MAX_PROGRESS_ROWS = 1024;
	static final int MAX_CONTINUE_ROWS = 100;
	static final int MAX_LIBRARY_ROWS = 500;

	private final SerialDatabaseExecutor worker;
	private final StremioSourceDao sourceDao = new StremioSourceDao();
	private final StremioMetaDao metaDao = new StremioMetaDao();
	private final StremioVideoDao videoDao = new StremioVideoDao();
	private final StremioProgressFavoriteDao progressFavoriteDao =
			new StremioProgressFavoriteDao();
	private final StremioSessionDao sessionDao = new StremioSessionDao(
			sourceDao, metaDao, videoDao, progressFavoriteDao);
	private final StremioCacheDao cacheDao = new StremioCacheDao();

	public StremioRepository(File file) {
		worker = new SerialDatabaseExecutor(file);
	}

	StremioRepository(File file, int targetVersion,
			List<StremioSchema.Migration> migrations) {
		worker = new SerialDatabaseExecutor(file, targetVersion, migrations);
	}

	public CompletableFuture<Void> ready() {
		return worker.ready();
	}

	public CompletableFuture<Void> putSource(StremioSourceRecord source) {
		return worker.submit(database -> {
			sourceDao.put(database, source);
			return null;
		});
	}

	/** Loads provider rows and their ordering metadata from one serialized DB snapshot. */
	public CompletableFuture<SourceState> getSourceState() {
		return worker.submit(sourceDao::readState);
	}

	/**
	 * Atomically replaces source rows, ordering, revision and the one-time default marker.
	 * Returns false without changing the database when the expected snapshot is stale.
	 */
	public CompletableFuture<Boolean> compareAndSetSourceState(
			SourceState expected, SourceState replacement) {
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(replacement, "replacement");
		StremioSourceDao.validateState(replacement);
		return worker.submit(database -> sourceDao.compareAndSet(database, expected, replacement));
	}

	public CompletableFuture<StremioSourceRecord> getSource(String sourceUuid) {
		return worker.submit(database -> sourceDao.get(database, sourceUuid));
	}

	public CompletableFuture<Boolean> deleteSource(String sourceUuid) {
		return worker.submit(database -> sourceDao.delete(database, sourceUuid));
	}

	public CompletableFuture<Void> putMeta(StremioMetaRecord meta) {
		return worker.submit(database -> {
			metaDao.put(database, meta);
			return null;
		});
	}

	/**
	 * Persists metadata together with the provider that supplied it. For canonical rows shared by
	 * multiple providers, only the highest-priority enabled provider may refresh display metadata.
	 */
	public CompletableFuture<Void> putOwnedMeta(
			StremioMetaRecord meta, StremioMetaProviderRecord provider) {
		Objects.requireNonNull(meta, "meta");
		Objects.requireNonNull(provider, "provider");
		if (!meta.metaKey().equals(provider.metaKey())) {
			return StremioFutures.failedFuture(
					new IllegalArgumentException("Metadata owner key mismatch"));
		}
		return worker.submit(database -> {
			metaDao.putOwned(database, meta, provider);
			return null;
		});
	}

	public CompletableFuture<StremioMetaRecord> getMeta(String metaKey) {
		return worker.submit(database -> metaDao.get(database, metaKey));
	}

	public CompletableFuture<Void> putMetaProvider(StremioMetaProviderRecord provider) {
		return worker.submit(database -> {
			metaDao.putProvider(database, provider);
			return null;
		});
	}

	public CompletableFuture<List<StremioMetaProviderRecord>> getMetaProviders(String metaKey) {
		Objects.requireNonNull(metaKey, "metaKey");
		return worker.submit(database -> metaDao.getProviders(database, metaKey));
	}

	public CompletableFuture<Void> putVideo(StremioVideoRecord video) {
		return worker.submit(database -> {
			videoDao.put(database, video);
			return null;
		});
	}

	public CompletableFuture<StremioVideoRecord> getVideo(String videoKey) {
		return worker.submit(database -> videoDao.get(database, videoKey));
	}

	public CompletableFuture<Void> putProgress(StremioProgressRecord progress) {
		return worker.submit(database -> {
			progressFavoriteDao.putProgress(database, progress);
			return null;
		});
	}

	/** Pins only storage reachability; global Favorites remains the source of truth. */
	public CompletableFuture<Void> setFavoriteRetention(
			String videoKey, boolean retained, long updatedMs) {
		Objects.requireNonNull(videoKey, "videoKey");
		if (updatedMs < 0L) throw new IllegalArgumentException("updatedMs cannot be negative");
		return worker.submit(database -> {
			progressFavoriteDao.setFavoriteRetention(database, videoKey, retained, updatedMs);
			return null;
		});
	}

	public CompletableFuture<StremioProgressRecord> getProgress(String videoKey) {
		return worker.submit(database -> progressFavoriteDao.getProgress(database, videoKey));
	}

	/**
	 * Reads favorites and every row needed to project them in one serialized DB snapshot.
	 * The retention table mirrors Unified Favorites; it does not own favorite state.
	 */
	public CompletableFuture<StremioLibraryData> getLibraryData(int limit) {
		if (limit <= 0) {
			return worker.submit(database -> new StremioLibraryData(List.of(),
					sessionDao.readData(database, Set.of())));
		}
		int boundedLimit = Math.min(limit, MAX_LIBRARY_ROWS);
		return worker.submit(database -> {
			List<StremioFavoriteRecord> favorites =
					progressFavoriteDao.readFavorites(database, boundedLimit);
			Set<String> videoKeys = new LinkedHashSet<>();
			for (StremioFavoriteRecord favorite : favorites) {
				videoKeys.add(favorite.stableId());
			}
			return new StremioLibraryData(favorites, sessionDao.readData(database, videoKeys));
		});
	}

	/** Reads movie/episode items and progress in a fixed number of batched DB queries. */
	public CompletableFuture<StremioSessionData> getSessionData(
			Collection<String> stableIds) {
		Set<String> ids = stableIds(stableIds);
		return worker.submit(database -> sessionDao.readData(database, ids));
	}

	/** Reads a bounded Continue snapshot without resolving each progress row separately. */
	public CompletableFuture<StremioSessionData> getContinueSessionData(int limit) {
		if (limit <= 0) return worker.submit(database -> sessionDao.readData(database, Set.of()));
		int boundedLimit = Math.min(limit, MAX_CONTINUE_ROWS);
		return worker.submit(database -> sessionDao.readData(database,
				progressFavoriteDao.readContinueKeys(database, boundedLimit)));
	}

	/** Returns the requested IDs currently mirrored from Unified Favorites. */
	public CompletableFuture<Set<String>> getFavoriteIdsBatch(Collection<String> stableIds) {
		Set<String> ids = stableIds(stableIds);
		if (ids.isEmpty()) return CompletableFuture.completedFuture(Set.of());
		return worker.submit(database -> progressFavoriteDao.readFavoriteIds(database, ids));
	}

	/** Returns all existing progress rows for the requested stable IDs using bounded batch queries. */
	public CompletableFuture<Map<String, StremioProgressRecord>> getProgressBatch(
			Collection<String> stableIds) {
		Set<String> ids = stableIds(stableIds);
		if (ids.isEmpty()) return CompletableFuture.completedFuture(Map.of());
		return worker.submit(database ->
				Map.copyOf(progressFavoriteDao.readProgress(database, ids)));
	}

	/** Removes Continue/progress only. Favorite retention and item metadata are intentionally kept. */
	public CompletableFuture<Boolean> deleteProgress(String stableId) {
		Objects.requireNonNull(stableId, "stableId");
		return worker.submit(database -> progressFavoriteDao.deleteProgress(database, stableId));
	}

	/**
	 * Returns only resumable finite content. The hard bound keeps accidental callers from
	 * materializing the complete durable progress history.
	 */
	public CompletableFuture<List<StremioProgressRecord>> listContinue(int limit) {
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());
		int boundedLimit = Math.min(limit, MAX_CONTINUE_ROWS);
		return worker.submit(database -> progressFavoriteDao.listContinue(database, boundedLimit));
	}

	/** Atomically replaces the singleton process-restoration pointer. */
	public CompletableFuture<Void> putSessionState(StremioSessionRecord session) {
		Objects.requireNonNull(session, "session");
		return worker.submit(database -> {
			sessionDao.putState(database, session);
			return null;
		});
	}

	public CompletableFuture<StremioSessionRecord> getSessionState() {
		return worker.submit(sessionDao::getState);
	}

	public CompletableFuture<Void> putCache(StremioCacheRecord cache) {
		return worker.submit(database -> {
			cacheDao.put(database, cache);
			return null;
		});
	}

	public CompletableFuture<Void> deleteCache(String cacheKey) {
		Objects.requireNonNull(cacheKey, "cacheKey");
		return worker.submit(database -> {
			cacheDao.delete(database, cacheKey);
			return null;
		});
	}

	public CompletableFuture<StremioCacheRecord> getCache(String cacheKey) {
		return worker.submit(database -> cacheDao.get(database, cacheKey));
	}

	public CompletableFuture<Integer> schemaVersion() {
		return worker.submit(StremioSchema::readVersion);
	}

	CompletableFuture<Boolean> foreignKeysEnabled() {
		return worker.submit(database -> {
			try (Cursor cursor = database.rawQuery("PRAGMA foreign_keys", null)) {
				return cursor.moveToFirst() && (cursor.getInt(0) == 1);
			}
		});
	}

	<T> CompletableFuture<T> executeForTest(SerialDatabaseExecutor.DatabaseOperation<T> operation) {
		return worker.submit(operation);
	}

	CompletableFuture<Void> pruneForTest(int metaRows, int videoRows, int progressRows) {
		return worker.submit(database -> {
			StremioProgressFavoriteDao.pruneDurableRows(
					database, metaRows, videoRows, progressRows);
			return null;
		});
	}

	public CompletableFuture<Void> closeAsync() {
		return worker.closeAsync();
	}

	@Override
	public void close() {
		closeAsync();
	}

	public record SourceState(long revision, List<StremioSourceRecord> sources,
			boolean cinemetaInstallHandled) {
		public SourceState {
			if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
			sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
		}
	}

	private static Set<String> stableIds(Collection<String> stableIds) {
		Objects.requireNonNull(stableIds, "stableIds");
		Set<String> result = new LinkedHashSet<>(stableIds.size());
		for (String stableId : stableIds) result.add(Objects.requireNonNull(stableId, "stableId"));
		return result;
	}
}
