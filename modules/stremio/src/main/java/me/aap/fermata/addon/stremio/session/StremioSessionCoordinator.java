package me.aap.fermata.addon.stremio.session;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Phase 5 session facade. StremioAddon can delegate resolver, favorite, SmartTop, queue and voice
 * callbacks here without adding a second playback-progress scheduler.
 */
public final class StremioSessionCoordinator {
	public static final String VOICE_TARGET = "stremio";
	public static final int MAX_CONTINUE_ITEMS = 100;
	public static final int MAX_LIBRARY_ITEMS = 500;
	private static final int VOICE_GATEWAY_LIMIT = 24;
	private static final int MAX_VOICE_QUERY_CHARS = 256;

	private final StremioSessionGateway gateway;
	private final CopyOnWriteArrayList<Runnable> progressObservers =
			new CopyOnWriteArrayList<>();
	private long activationRequest;
	private long latestRequestedGeneration = -1L;
	private String latestRequestedStableId;
	private long ownershipSequence;
	private long voiceGeneration;
	private StremioPlaybackOwnership currentOwnership;
	private StremioSmartTopTarget currentTarget;
	private long dismissedOwnershipToken = -1L;

	public StremioSessionCoordinator(StremioSessionGateway gateway) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
	}

	/** Observes committed Continue-Watching changes; callbacks never run for stale writes. */
	public AutoCloseable observeProgressChanges(Runnable observer) {
		progressObservers.add(Objects.requireNonNull(observer, "observer"));
		return () -> progressObservers.remove(observer);
	}

	public String getVoiceTarget() {
		return VOICE_TARGET;
	}

	/** Loads a bounded, newest-first snapshot without exposing playback/provider URLs. */
	public CompletionStage<List<StremioContinueEntry>> loadContinue(int limit) {
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());
		int boundedLimit = Math.min(limit, MAX_CONTINUE_ITEMS);
		return gateway.loadContinue(boundedLimit).thenApply(entries -> {
			Objects.requireNonNull(entries, "entries");
			LinkedHashMap<String, StremioContinueEntry> unique = new LinkedHashMap<>();
			for (StremioContinueEntry entry : entries) {
				Objects.requireNonNull(entry, "entry");
				unique.putIfAbsent(entry.item().stableId(), entry);
				if (unique.size() == boundedLimit) break;
			}
			return List.copyOf(unique.values());
		});
	}

	/** Loads the Unified Favorites mirror with item and progress data from one DB snapshot. */
	public CompletionStage<List<StremioLibraryItem>> loadLibraryFavorites(int limit) {
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());
		int boundedLimit = Math.min(limit, MAX_LIBRARY_ITEMS);
		return gateway.loadLibraryFavorites(boundedLimit).thenApply(items -> {
			Objects.requireNonNull(items, "items");
			LinkedHashMap<String, StremioLibraryItem> unique = new LinkedHashMap<>();
			for (StremioLibraryItem libraryItem : items) {
				Objects.requireNonNull(libraryItem, "libraryItem");
				String stableId = libraryItem.item().stableId();
				StremioSessionIds.requireOpaque(stableId, "stableId");
				unique.putIfAbsent(stableId, libraryItem);
				if (unique.size() == boundedLimit) break;
			}
			return List.copyOf(unique.values());
		});
	}

	/** Returns a stable-ID keyed progress snapshot without issuing a DB query per item. */
	public CompletionStage<Map<String, StremioProgressState>> loadProgressBatch(
			Collection<String> stableIds) {
		Objects.requireNonNull(stableIds, "stableIds");
		LinkedHashMap<String, Boolean> requested = new LinkedHashMap<>();
		for (String stableId : stableIds) {
			requested.put(StremioSessionIds.requireOpaque(stableId, "stableId"), Boolean.TRUE);
		}
		if (requested.isEmpty()) return CompletableFuture.completedFuture(Map.of());
		return gateway.loadProgressBatch(requested.keySet()).thenApply(progress -> {
			Objects.requireNonNull(progress, "progress");
			LinkedHashMap<String, StremioProgressState> result = new LinkedHashMap<>();
			for (String stableId : requested.keySet()) {
				StremioProgressState state = progress.get(stableId);
				if (state == null) continue;
				if (!stableId.equals(state.stableId())) {
					throw new IllegalStateException("Progress identity mismatch");
				}
				result.put(stableId, state);
			}
			return Map.copyOf(result);
		});
	}

	/** Resolves durable movie/episode session items without one loadItem call per row. */
	public CompletionStage<Map<String, StremioSessionItem>> loadItemsBatch(
			Collection<String> stableIds) {
		Objects.requireNonNull(stableIds, "stableIds");
		LinkedHashMap<String, Boolean> requested = new LinkedHashMap<>();
		for (String stableId : stableIds) {
			requested.put(StremioSessionIds.requireOpaque(stableId, "stableId"), Boolean.TRUE);
		}
		if (requested.isEmpty()) return CompletableFuture.completedFuture(Map.of());
		return gateway.loadItemsBatch(requested.keySet()).thenApply(items -> {
			Objects.requireNonNull(items, "items");
			LinkedHashMap<String, StremioSessionItem> result = new LinkedHashMap<>();
			for (String stableId : requested.keySet()) {
				StremioSessionItem item = items.get(stableId);
				if (item == null) continue;
				if (!stableId.equals(item.stableId())) {
					throw new IllegalStateException("Session item identity mismatch");
				}
				result.put(stableId, item);
			}
			return Map.copyOf(result);
		});
	}

	/** Returns one boolean per requested ID; absent retention rows are reported as false. */
	public CompletionStage<Map<String, Boolean>> loadFavoriteStates(
			Collection<String> stableIds) {
		Objects.requireNonNull(stableIds, "stableIds");
		LinkedHashMap<String, Boolean> requested = new LinkedHashMap<>();
		for (String stableId : stableIds) {
			requested.put(StremioSessionIds.requireOpaque(stableId, "stableId"), Boolean.FALSE);
		}
		if (requested.isEmpty()) return CompletableFuture.completedFuture(Map.of());
		return gateway.loadFavoriteStates(requested.keySet()).thenApply(states -> {
			Objects.requireNonNull(states, "states");
			LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
			for (String stableId : requested.keySet()) {
				result.put(stableId, Boolean.TRUE.equals(states.get(stableId)));
			}
			return Map.copyOf(result);
		});
	}

	/** Dismisses Continue while leaving Unified Favorites and its retention pin untouched. */
	public CompletionStage<Void> dismissContinue(String stableId) {
		stableId = StremioSessionIds.requireOpaque(stableId, "stableId");
		final long suppressedToken;
		synchronized (this) {
			suppressedToken = ((currentOwnership != null) &&
					stableId.equals(currentOwnership.stableId())) ?
					currentOwnership.ownershipToken() : -1L;
			if (suppressedToken >= 0L) dismissedOwnershipToken = suppressedToken;
		}
		return gateway.dismissContinue(stableId).whenComplete((ignored, failure) -> {
			if ((failure == null) || (suppressedToken < 0L)) return;
			synchronized (this) {
				if (dismissedOwnershipToken == suppressedToken) dismissedOwnershipToken = -1L;
			}
		});
	}

	public CompletionStage<StremioItemResolution> resolveStableItem(String stableId) {
		StremioSessionIds.requireOpaque(stableId, "stableId");
		return gateway.loadItem(stableId).thenCompose(item -> {
			if (item == null) return CompletableFuture.completedFuture(StremioItemResolution.missing());
			if (!stableId.equals(item.stableId())) {
				return failed(new IllegalStateException("Resolved Stremio item identity mismatch"));
			}
			return gateway.getProviderState(item.sourceUuid()).thenApply(state ->
					new StremioItemResolution(availability(state), item));
		});
	}

	public CompletionStage<Void> synchronizeFavorite(String stableId, boolean favorite) {
		StremioSessionIds.requireOpaque(stableId, "stableId");
		return gateway.loadItem(stableId).thenCompose(item -> {
			if (item == null) return CompletableFuture.completedFuture(null);
			return gateway.synchronizeFavorite(new StremioFavoriteUpdate(
					item.stableId(), item.canonicalContentKey(), favorite));
		});
	}

	/**
	 * Resolves and binds the exact current item. Only the latest asynchronous activation request
	 * may acquire ownership; its restore pointer is persisted for process-death recovery.
	 */
	public CompletionStage<StremioPlaybackOwnership> activatePlayback(
			String stableId, long playbackGeneration, long nowMs) {
		if ((playbackGeneration < 0L) || (nowMs < 0L)) {
			return failed(new IllegalArgumentException("playback values cannot be negative"));
		}
		final long request;
		synchronized (this) {
			if ((playbackGeneration < latestRequestedGeneration) ||
					((playbackGeneration == latestRequestedGeneration) &&
							(latestRequestedStableId != null) &&
							!stableId.equals(latestRequestedStableId))) {
				return failed(new StaleSessionException());
			}
			latestRequestedGeneration = playbackGeneration;
			latestRequestedStableId = stableId;
			request = ++activationRequest;
		}

		return resolveStableItem(stableId).thenCompose(resolution -> {
			if (!resolution.isAvailable()) {
				return failed(new UnavailableItemException(resolution.availability()));
			}
			StremioSessionItem item = resolution.item();
			StremioPlaybackOwnership ownership;
			StremioPlaybackOwnership previousOwnership;
			StremioSmartTopTarget previousTarget;
			long previousDismissedOwnershipToken;
			synchronized (this) {
				if (request != activationRequest) return failed(new StaleSessionException());
				if ((currentOwnership != null) &&
						(playbackGeneration < currentOwnership.playbackGeneration())) {
					return failed(new StaleSessionException());
				}
				if ((currentOwnership != null) &&
						(playbackGeneration == currentOwnership.playbackGeneration()) &&
						!item.stableId().equals(currentOwnership.stableId())) {
					return failed(new StaleSessionException());
				}
				previousOwnership = currentOwnership;
				previousTarget = currentTarget;
				previousDismissedOwnershipToken = dismissedOwnershipToken;
				ownership = new StremioPlaybackOwnership(item.stableId(), playbackGeneration,
						++ownershipSequence);
				currentOwnership = ownership;
				dismissedOwnershipToken = -1L;
				currentTarget = StremioSmartTopTarget.from(item);
			}

			StremioRestorePoint point = new StremioRestorePoint(item.stableId(),
					item.backToListId(), playbackGeneration, nowMs);
			return gateway.saveRestorePoint(point).handle((ignored, failure) -> {
				if (failure == null) return ownership;
				synchronized (this) {
					if (ownership.equals(currentOwnership)) {
						currentOwnership = previousOwnership;
						currentTarget = previousTarget;
						dismissedOwnershipToken = previousDismissedOwnershipToken;
					}
				}
				throw new CompletionException(failure);
			});
		});
	}

	/** Called only from the base-owned PlaybackProgressPolicy callback path. */
	public Optional<StremioProgressSnapshot> snapshotProgressFromCore(
			StremioPlaybackOwnership ownership, long normalizedPositionMs,
			boolean completed, long nowMs) {
		Objects.requireNonNull(ownership, "ownership");
		synchronized (this) {
			if (!ownership.equals(currentOwnership)) return Optional.empty();
		}
		return Optional.of(new StremioProgressSnapshot(ownership.stableId(),
				ownership.playbackGeneration(), ownership.ownershipToken(),
				normalizedPositionMs, completed, nowMs));
	}

	/** Persists a previously authorized core snapshot only while its generation still owns playback. */
	public CompletionStage<StremioProgressWriteResult> persistCoreProgress(
			StremioProgressSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		synchronized (this) {
			if (!owns(snapshot)) {
				return CompletableFuture.completedFuture(
						StremioProgressWriteResult.REJECTED_STALE);
			}
			if (snapshot.ownershipToken() == dismissedOwnershipToken) {
				return CompletableFuture.completedFuture(
						StremioProgressWriteResult.REJECTED_STALE);
			}
		}
		return gateway.writeProgress(snapshot).thenApply(ignored -> {
			notifyProgressChanged();
			return StremioProgressWriteResult.WRITTEN;
		});
	}

	private void notifyProgressChanged() {
		for (Runnable observer : progressObservers) {
			try {
				observer.run();
			} catch (RuntimeException ignored) {
				// A UI observer must not turn a committed database write into a playback failure.
			}
		}
	}

	public synchronized void releasePlayback(StremioPlaybackOwnership ownership) {
		if (!Objects.equals(currentOwnership, ownership)) return;
		currentOwnership = null;
	}

	public synchronized Optional<StremioSmartTopTarget> getCurrentTarget() {
		return Optional.ofNullable(currentTarget);
	}

	/** Restores display/navigation state only; media-session ownership is reacquired on playback. */
	public CompletionStage<StremioItemResolution> restoreAfterProcessDeath() {
		return gateway.loadRestorePoint().thenCompose(point -> {
			if (point == null) return CompletableFuture.completedFuture(StremioItemResolution.missing());
			return resolveStableItem(point.stableId()).thenApply(resolution -> {
				if (resolution.item() != null) {
					StremioSessionItem item = resolution.item();
					if (!point.backToListId().equals(item.backToListId())) {
						throw new IllegalStateException("Restore destination identity mismatch");
					}
					synchronized (this) {
						currentTarget = StremioSmartTopTarget.from(item);
					}
				}
				return resolution;
			});
		});
	}

	public CompletionStage<StremioItemResolution> adjacentEpisode(
			String stableId, StremioAdjacentDirection direction) {
		Objects.requireNonNull(direction, "direction");
		return resolveStableItem(stableId).thenCompose(resolution -> {
			if (!resolution.isAvailable()) return CompletableFuture.completedFuture(resolution);
			StremioSessionItem current = resolution.item();
			if (!current.isEpisode()) return CompletableFuture.completedFuture(
					StremioItemResolution.missing());
			return gateway.loadEpisodeQueue(current.episodeQueueId()).thenCompose(items -> {
				List<StremioSessionItem> ordered = deterministicQueue(current.episodeQueueId(), items);
				int index = indexOf(ordered, current.stableId());
				int target = index + direction.offset;
				if ((index < 0) || (target < 0) || (target >= ordered.size())) {
					return CompletableFuture.completedFuture(StremioItemResolution.missing());
				}
				return resolveStableItem(ordered.get(target).stableId());
			});
		});
	}

	public CompletionStage<StremioVoiceResult> searchVoice(String query, Locale locale) {
		String normalized = normalizeQuery(query, locale);
		final long generation;
		synchronized (this) {
			generation = ++voiceGeneration;
		}
		return gateway.search(normalized, locale, VOICE_GATEWAY_LIMIT)
				.thenCompose(this::filterEnabledVoiceCandidates).thenApply(candidates -> {
			synchronized (this) {
				if (generation != voiceGeneration) throw new StaleSessionException();
			}
			return new StremioVoiceResult(generation, locale,
					rankVoiceCandidates(normalized, locale, candidates));
		});
	}

	private CompletionStage<List<StremioVoiceCandidate>> filterEnabledVoiceCandidates(
			List<StremioVoiceCandidate> candidates) {
		List<StremioVoiceCandidate> copy = List.copyOf(
				Objects.requireNonNull(candidates, "candidates"));
		List<CompletableFuture<StremioProviderState>> states = new ArrayList<>(copy.size());
		for (StremioVoiceCandidate candidate : copy) {
			states.add(gateway.getProviderState(candidate.sourceUuid())
					.exceptionally(failure -> StremioProviderState.REMOVED).toCompletableFuture());
		}
		return CompletableFuture.allOf(states.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
			List<StremioVoiceCandidate> enabled = new ArrayList<>(copy.size());
			for (int i = 0; i < copy.size(); i++) {
				if (states.get(i).join() == StremioProviderState.ENABLED) enabled.add(copy.get(i));
			}
			return List.copyOf(enabled);
		});
	}

	public CompletionStage<StremioItemResolution> selectVoiceResult(
			StremioVoiceResult result, int oneBasedIndex) {
		Objects.requireNonNull(result, "result");
		StremioVoiceCandidate candidate;
		synchronized (this) {
			if (result.generation() != voiceGeneration) return failed(new StaleSessionException());
			candidate = result.choice(oneBasedIndex);
			if (candidate != null) voiceGeneration++;
		}
		return (candidate == null) ? CompletableFuture.completedFuture(
				StremioItemResolution.missing()) : resolveStableItem(candidate.stableId());
	}

	private synchronized boolean owns(StremioProgressSnapshot snapshot) {
		return (currentOwnership != null) &&
				currentOwnership.stableId().equals(snapshot.stableId()) &&
				(currentOwnership.playbackGeneration() == snapshot.playbackGeneration()) &&
				(currentOwnership.ownershipToken() == snapshot.ownershipToken());
	}

	private static StremioItemAvailability availability(StremioProviderState state) {
		if (state == null) return StremioItemAvailability.PROVIDER_REMOVED;
		return switch (state) {
			case ENABLED -> StremioItemAvailability.AVAILABLE;
			case DISABLED -> StremioItemAvailability.PROVIDER_DISABLED;
			case REMOVED -> StremioItemAvailability.PROVIDER_REMOVED;
		};
	}

	private static List<StremioSessionItem> deterministicQueue(
			String queueId, List<StremioSessionItem> items) {
		Map<String, StremioSessionItem> unique = new LinkedHashMap<>();
		for (StremioSessionItem item : Objects.requireNonNull(items, "items")) {
			if ((item != null) && queueId.equals(item.episodeQueueId())) {
				unique.putIfAbsent(item.stableId(), item);
			}
		}
		List<StremioSessionItem> result = new ArrayList<>(unique.values());
		result.sort(Comparator.comparingInt(StremioSessionItem::seasonNumber)
				.thenComparingInt(StremioSessionItem::episodeNumber)
				.thenComparing(StremioSessionItem::stableId));
		return result;
	}

	private static int indexOf(List<StremioSessionItem> items, String stableId) {
		for (int i = 0; i < items.size(); i++) {
			if (stableId.equals(items.get(i).stableId())) return i;
		}
		return -1;
	}

	private static String normalizeQuery(String query, Locale locale) {
		Objects.requireNonNull(locale, "locale");
		String value = StremioSessionIds.requireText(query, "query");
		if (value.length() > MAX_VOICE_QUERY_CHARS) {
			throw new IllegalArgumentException("query is too long");
		}
		return Normalizer.normalize(value.strip(), Normalizer.Form.NFKC)
				.toLowerCase(locale);
	}

	private static List<StremioVoiceCandidate> rankVoiceCandidates(
			String query, Locale locale, List<StremioVoiceCandidate> candidates) {
		Collator collator = Collator.getInstance(locale);
		collator.setStrength(Collator.PRIMARY);
		Map<String, StremioVoiceCandidate> unique = new LinkedHashMap<>();
		for (StremioVoiceCandidate candidate : Objects.requireNonNull(candidates, "candidates")) {
			if (candidate != null) unique.putIfAbsent(candidate.stableId(), candidate);
		}
		List<StremioVoiceCandidate> result = new ArrayList<>(unique.values());
		result.sort(Comparator
				.comparingInt((StremioVoiceCandidate c) -> matchRank(query,
						normalizeForMatch(c.title(), locale))).reversed()
				.thenComparingInt(StremioVoiceCandidate::providerRank)
				.thenComparing(StremioVoiceCandidate::title, collator)
				.thenComparing(StremioVoiceCandidate::stableId));
		if (result.size() > 3) result.subList(3, result.size()).clear();
		return List.copyOf(result);
	}

	private static int matchRank(String query, String title) {
		if (title.equals(query)) return 3;
		if (title.startsWith(query)) return 2;
		return title.contains(query) ? 1 : 0;
	}

	private static String normalizeForMatch(String value, Locale locale) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(locale);
	}

	private static <T> CompletionStage<T> failed(Throwable failure) {
		return CompletableFuture.failedFuture(failure);
	}

	public static final class UnavailableItemException extends IllegalStateException {
		private final StremioItemAvailability availability;

		UnavailableItemException(StremioItemAvailability availability) {
			super("Stremio item is unavailable: " + availability);
			this.availability = availability;
		}

		public StremioItemAvailability getAvailability() {
			return availability;
		}
	}

	public static final class StaleSessionException extends IllegalStateException {
		StaleSessionException() {
			super("Stale Stremio session operation");
		}
	}
}
