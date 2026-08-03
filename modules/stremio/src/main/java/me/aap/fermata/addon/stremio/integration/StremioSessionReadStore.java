package me.aap.fermata.addon.stremio.integration;

import me.aap.fermata.addon.stremio.util.StremioFutures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

import me.aap.fermata.addon.stremio.data.StremioFavoriteRecord;
import me.aap.fermata.addon.stremio.data.StremioProgressRecord;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioSessionData;
import me.aap.fermata.addon.stremio.data.StremioVideoRecord;
import me.aap.fermata.addon.stremio.session.StremioContinueEntry;
import me.aap.fermata.addon.stremio.session.StremioLibraryItem;
import me.aap.fermata.addon.stremio.session.StremioProgressState;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

/** Materializes session-facing read models from repository snapshots. */
final class StremioSessionReadStore {
	private final StremioRepository repository;
	private final StremioProjectionStore projections;
	private final BooleanSupplier closed;

	StremioSessionReadStore(StremioRepository repository,
			StremioProjectionStore projections, BooleanSupplier closed) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.projections = Objects.requireNonNull(projections, "projections");
		this.closed = Objects.requireNonNull(closed, "closed");
	}

	CompletionStage<List<StremioContinueEntry>> loadContinue(int limit) {
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());
		return open(repository.getContinueSessionData(limit)).thenApply(data -> {
			Map<String, StremioPersistedItem> items = projections.sessionProjections(data);
			List<StremioProgressRecord> progress = new ArrayList<>(data.progress().values());
			progress.sort((first, second) -> {
				int played = Long.compare(second.lastPlayedMs(), first.lastPlayedMs());
				if (played != 0) return played;
				int updated = Long.compare(second.updatedMs(), first.updatedMs());
				return (updated != 0) ? updated :
						first.videoKey().compareTo(second.videoKey());
			});
			List<StremioContinueEntry> result = new ArrayList<>(progress.size());
			for (StremioProgressRecord row : progress) {
				StremioPersistedItem projection = items.get(row.videoKey());
				if (projection == null) continue;
				result.add(new StremioContinueEntry(projection.item(), row.positionMs(),
						row.durationMs(), row.lastPlayedMs()));
			}
			return List.copyOf(result);
		});
	}

	CompletionStage<List<StremioLibraryItem>> loadLibraryFavorites(int limit) {
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());
		return open(repository.getLibraryData(limit)).thenApply(data -> {
			StremioSessionData session = data.session();
			Map<String, StremioPersistedItem> items = projections.sessionProjections(session);
			List<StremioLibraryItem> result = new ArrayList<>(data.favorites().size());
			for (StremioFavoriteRecord favorite : data.favorites()) {
				StremioVideoRecord video = session.videos().get(favorite.stableId());
				if (video == null) continue;
				StremioPersistedItem projection = items.get(favorite.stableId());
				if (projection == null) continue;
				StremioProgressRecord progress = session.progress().get(favorite.stableId());
				result.add(new StremioLibraryItem(projection.item(), video.type(),
						favorite.updatedMs(), StremioProjectionStore.progressState(progress)));
			}
			return List.copyOf(result);
		});
	}

	CompletionStage<Map<String, StremioSessionItem>> loadItemsBatch(
			Collection<String> stableIds) {
		return open(repository.getSessionData(stableIds)).thenApply(data -> {
			Map<String, StremioSessionItem> result = new LinkedHashMap<>();
			for (Map.Entry<String, StremioPersistedItem> entry :
					projections.sessionProjections(data).entrySet()) {
				result.put(entry.getKey(), entry.getValue().item());
			}
			return Map.copyOf(result);
		});
	}

	CompletionStage<Map<String, StremioProgressState>> loadProgressBatch(
			Collection<String> stableIds) {
		return open(repository.getProgressBatch(stableIds)).thenApply(rows -> {
			Map<String, StremioProgressState> result = new LinkedHashMap<>();
			for (Map.Entry<String, StremioProgressRecord> entry : rows.entrySet()) {
				result.put(entry.getKey(), StremioProjectionStore.progressState(entry.getValue()));
			}
			return Map.copyOf(result);
		});
	}

	CompletionStage<Map<String, Boolean>> loadFavoriteStates(Collection<String> stableIds) {
		List<String> requested = List.copyOf(stableIds);
		return open(repository.getFavoriteIdsBatch(requested)).thenApply(favorites -> {
			Map<String, Boolean> result = new LinkedHashMap<>();
			for (String stableId : requested) result.put(stableId, favorites.contains(stableId));
			return Map.copyOf(result);
		});
	}

	CompletionStage<Void> dismissContinue(String stableId) {
		return open(repository.deleteProgress(stableId)).thenApply(ignored -> null);
	}

	CompletionStage<StremioSessionItem> loadItem(String stableId) {
		return projections.load(stableId).thenApply(projection ->
				(projection == null) ? null : projection.item());
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> future) {
		return closed.getAsBoolean() ? StremioFutures.failedFuture(
				new IllegalStateException("Stremio runtime is closed")) : future;
	}
}
