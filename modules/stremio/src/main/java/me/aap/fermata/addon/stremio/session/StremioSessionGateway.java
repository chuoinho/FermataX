package me.aap.fermata.addon.stremio.session;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter boundary implemented by the final Stremio runtime. Every method must be asynchronous;
 * item, favorite, progress and restore operations are backed by the Stremio database.
 */
public interface StremioSessionGateway {
	CompletionStage<List<StremioContinueEntry>> loadContinue(int limit);

	default CompletionStage<List<StremioLibraryItem>> loadLibraryFavorites(int limit) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
				"Library favorites are not supported by this gateway"));
	}

	default CompletionStage<Map<String, StremioSessionItem>> loadItemsBatch(
			Collection<String> stableIds) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
				"Batch item loading is not supported by this gateway"));
	}

	default CompletionStage<Map<String, StremioProgressState>> loadProgressBatch(
			Collection<String> stableIds) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
				"Batch progress loading is not supported by this gateway"));
	}

	default CompletionStage<Map<String, Boolean>> loadFavoriteStates(
			Collection<String> stableIds) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
				"Batch favorite loading is not supported by this gateway"));
	}

	default CompletionStage<Void> dismissContinue(String stableId) {
		return CompletableFuture.failedFuture(new UnsupportedOperationException(
				"Continue dismissal is not supported by this gateway"));
	}

	CompletionStage<StremioSessionItem> loadItem(String stableId);

	CompletionStage<StremioProviderState> getProviderState(String sourceUuid);

	CompletionStage<Void> synchronizeFavorite(StremioFavoriteUpdate update);

	CompletionStage<Void> writeProgress(StremioProgressSnapshot snapshot);

	CompletionStage<Void> saveRestorePoint(StremioRestorePoint restorePoint);

	CompletionStage<StremioRestorePoint> loadRestorePoint();

	CompletionStage<List<StremioSessionItem>> loadEpisodeQueue(String episodeQueueId);

	/** Must query currently installed and enabled providers on every invocation. */
	CompletionStage<List<StremioVoiceCandidate>> search(
			String normalizedQuery, Locale locale, int limit);
}
