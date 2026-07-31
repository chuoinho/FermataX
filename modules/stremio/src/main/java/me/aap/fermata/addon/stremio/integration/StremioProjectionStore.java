package me.aap.fermata.addon.stremio.integration;

import static me.aap.fermata.addon.stremio.integration.StremioFutureBridge.toCompletable;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import me.aap.fermata.addon.stremio.StremioRootItem;
import me.aap.fermata.addon.stremio.browse.BrowseDetails;
import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseProvider;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.data.StremioCacheRecord;
import me.aap.fermata.addon.stremio.data.StremioMetaProviderRecord;
import me.aap.fermata.addon.stremio.data.StremioMetaRecord;
import me.aap.fermata.addon.stremio.data.StremioProgressRecord;
import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.data.StremioSessionData;
import me.aap.fermata.addon.stremio.data.StremioSourceRecord;
import me.aap.fermata.addon.stremio.data.StremioVideoRecord;
import me.aap.fermata.addon.stremio.item.StremioCanonicalIdentity;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioItemIds;
import me.aap.fermata.addon.stremio.playback.StremioPlaybackIdentity;
import me.aap.fermata.addon.stremio.protocol.response.StremioDuration;
import me.aap.fermata.addon.stremio.session.StremioProgressState;
import me.aap.fermata.addon.stremio.session.StremioProviderState;
import me.aap.fermata.addon.stremio.session.StremioSessionItem;

/** Owns durable item projection, provider rebinding, cache replacement and queue materialization. */
final class StremioProjectionStore {
	private static final String QUEUE_PREFIX = "stremio:queue:";
	private static final String CONTENT_PREFIX = "stremio:content:";
	private static final Pattern URL_TITLE = Pattern.compile(
			"(?i)^(?:https?://|www\\.|file:|content:|javascript:|intent:).*");

	private final StremioRepository repository;
	private final Supplier<CompletionStage<List<BrowseProvider>>> providerSource;
	private final StremioItemGateway items;
	private final BooleanSupplier closed;

	StremioProjectionStore(StremioRepository repository,
			Supplier<CompletionStage<List<BrowseProvider>>> providerSource,
			StremioItemGateway items, BooleanSupplier closed) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.providerSource = Objects.requireNonNull(providerSource, "providerSource");
		this.items = Objects.requireNonNull(items, "items");
		this.closed = Objects.requireNonNull(closed, "closed");
	}

	CompletableFuture<StremioPersistedItem> load(String stableId) {
		if (closed.getAsBoolean()) return CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed"));
		return open(repository.getVideo(stableId)).thenCompose(video -> {
			if (video == null) return loadCached(stableId);
			return projection(video).thenCompose(value -> {
				if (value == null) return loadCached(stableId);
				return persist(value).thenApply(ignored -> value);
			});
		});
	}

	Map<String, StremioPersistedItem> sessionProjections(StremioSessionData data) {
		Map<String, StremioSourceRecord> sources = new HashMap<>();
		for (StremioSourceRecord source : data.sourceState().sources()) {
			sources.put(source.sourceUuid(), source);
		}
		Map<String, StremioPersistedItem> result = new LinkedHashMap<>();
		for (StremioVideoRecord video : data.videos().values()) {
			StremioMetaRecord meta = data.metadata().get(video.metaKey());
			if (meta == null) continue;
			ProviderBinding binding = libraryBinding(meta, data, sources);
			if (binding != null) result.put(video.videoKey(), projection(binding, meta, video));
		}
		return result;
	}

	@Nullable
	StremioPersistedItem project(BrowseMedia media) {
		String title = safeTitle(media.title(), "Stremio");
		if (unsafeIdentity(media.id())) return null;
		String stableId = StremioItemIds.meta(media);
		StremioCanonicalIdentity canonical =
				StremioCanonicalIdentity.from(media.type(), media.id());
		String content = (canonical == null) ?
				StremioPlaybackIdentity.scoped(media.sourceUuid(), media.type(),
						media.id(), media.id()).contentKey() : canonical.contentId();
		StremioSessionItem item = new StremioSessionItem(stableId, content,
				media.sourceUuid(), title, Objects.requireNonNullElse(media.releaseInfo(), ""),
				null, durationMillis(media.duration()), StremioRootItem.ID,
				null, -1, -1);
		return new StremioPersistedItem(item, media.type(), media.id(), null,
				new BrowseMedia(media.sourceUuid(), media.type(), media.id(), title,
						media.poster(), media.background(), media.description(), media.releaseInfo(),
						media.imdbRating(), media.duration(), media.genres(), media.language()), null);
	}

	CompletableFuture<Void> persist(StremioPersistedItem projection) {
		String key = StremioProjectionCodec.itemCacheKey(projection.item().stableId());
		return open(repository.getCache(key)).thenCompose(existing -> {
			if ((existing == null) ||
					existing.sourceUuid().equals(projection.item().sourceUuid())) {
				return write(key, projection);
			}
			StremioPersistedItem cached = StremioProjectionCodec.decode(existing.payload());
			if (!replaceableCanonicalOwner(existing, cached, projection)) {
				return CompletableFuture.completedFuture(null);
			}
			return providerState(existing.sourceUuid()).thenCombine(
					providerState(projection.item().sourceUuid()),
					(previous, replacement) -> (previous != StremioProviderState.ENABLED) &&
							(replacement == StremioProviderState.ENABLED))
					.thenCompose(replace -> replace ? replace(key, projection) :
							CompletableFuture.completedFuture(null));
		});
	}

	CompletableFuture<StremioPersistedItem> refreshEpisode(StremioPersistedItem projection) {
		if (projection.episode() == null) return CompletableFuture.completedFuture(projection);
		return toCompletable(items.meta(projection.media())).handle((details, failure) -> {
			if ((failure != null) || (details == null)) return projection;
			List<BrowseEpisode> siblings = details.seasons().stream()
					.filter(season -> season.number() == projection.item().seasonNumber())
					.findFirst().map(BrowseSeason::episodes).orElse(List.of());
			BrowseEpisode selected = siblings.stream().filter(episode ->
					episode.episode() == projection.item().episodeNumber()).findFirst()
					.orElse(projection.episode());
			return new StremioPersistedItem(projection.item(), projection.type(),
					projection.media().id(), selected.videoId(), projection.media(), selected,
					siblings);
		}).toCompletableFuture();
	}

	CompletionStage<List<StremioSessionItem>> loadEpisodeQueue(String episodeQueueId) {
		if ((episodeQueueId == null) || !episodeQueueId.startsWith(QUEUE_PREFIX)) {
			return CompletableFuture.completedFuture(List.of());
		}
		String metaKey = episodeQueueId.substring(QUEUE_PREFIX.length());
		return open(repository.getMeta(metaKey)).thenCompose(meta -> {
			if (meta == null) return CompletableFuture.completedFuture(List.of());
			return sourceFor(meta).thenCompose(binding -> {
				if (binding == null) return CompletableFuture.completedFuture(List.of());
				return toCompletable(items.meta(media(binding, meta))).thenCompose(details ->
						persistQueue(meta, details));
			});
		});
	}

	@Nullable
	static StremioProgressState progressState(@Nullable StremioProgressRecord progress) {
		return (progress == null) ? null : new StremioProgressState(progress.videoKey(),
				progress.positionMs(), progress.durationMs(), progress.completed(),
				progress.lastPlayedMs(), progress.updatedMs());
	}

	private CompletableFuture<StremioPersistedItem> loadCached(String stableId) {
		return open(repository.getCache(StremioProjectionCodec.itemCacheKey(stableId)))
				.thenApply(cache -> (cache == null) ? null :
						StremioProjectionCodec.decode(cache.payload()));
	}

	private CompletableFuture<StremioPersistedItem> projection(StremioVideoRecord video) {
		return open(repository.getMeta(video.metaKey())).thenCompose(meta -> {
			if (meta == null) return CompletableFuture.completedFuture(null);
			return sourceFor(meta).thenApply(binding -> (binding == null) ? null :
					projection(binding, meta, video));
		});
	}

	private CompletableFuture<ProviderBinding> sourceFor(StremioMetaRecord meta) {
		if (meta.identityScope().startsWith("canonical:")) {
			return repository.getMetaProviders(meta.metaKey()).thenCompose(owners ->
					providerSource.get().thenCombine(repository.getSourceState(),
							(providers, sourceState) -> {
						Map<String, StremioMetaProviderRecord> mapped = new HashMap<>();
						for (StremioMetaProviderRecord owner : owners) {
							mapped.put(owner.sourceUuid(), owner);
						}
						for (var provider : providers) {
							if (!provider.enabled()) continue;
							StremioMetaProviderRecord owner = mapped.get(provider.sourceUuid());
							if (owner != null) return new ProviderBinding(provider.sourceUuid(),
									owner.providerMetaId());
						}
						for (StremioSourceRecord source : sourceState.sources()) {
							StremioMetaProviderRecord owner = mapped.get(source.sourceUuid());
							if (owner != null) return new ProviderBinding(source.sourceUuid(),
									owner.providerMetaId());
						}
						return null;
					})).toCompletableFuture();
		}
		return open(repository.getSource(meta.identityScope())).thenApply(source ->
				(source == null || !source.enabled()) ? null :
						new ProviderBinding(source.sourceUuid(), meta.providerMetaId()));
	}

	@Nullable
	private static ProviderBinding libraryBinding(StremioMetaRecord meta,
			StremioSessionData data, Map<String, StremioSourceRecord> sources) {
		if (!meta.identityScope().startsWith("canonical:")) {
			return sources.containsKey(meta.identityScope()) ?
					new ProviderBinding(meta.identityScope(), meta.providerMetaId()) : null;
		}
		Map<String, StremioMetaProviderRecord> owners = new HashMap<>();
		for (StremioMetaProviderRecord owner :
				data.metadataProviders().getOrDefault(meta.metaKey(), List.of())) {
			owners.put(owner.sourceUuid(), owner);
		}
		for (StremioSourceRecord source : data.sourceState().sources()) {
			StremioMetaProviderRecord owner = owners.get(source.sourceUuid());
			if (source.enabled() && (owner != null)) {
				return new ProviderBinding(source.sourceUuid(), owner.providerMetaId());
			}
		}
		for (StremioSourceRecord source : data.sourceState().sources()) {
			StremioMetaProviderRecord owner = owners.get(source.sourceUuid());
			if (owner != null) return new ProviderBinding(source.sourceUuid(), owner.providerMetaId());
		}
		return null;
	}

	private StremioPersistedItem projection(ProviderBinding binding,
			StremioMetaRecord meta, StremioVideoRecord video) {
		String sourceUuid = binding.sourceUuid();
		BrowseMedia media = media(binding, meta);
		BrowseEpisode episode = null;
		String queue = null;
		String back = StremioItemIds.meta(media);
		int season = -1;
		int episodeNumber = -1;
		if ((video.seasonNumber() != null) && (video.episodeNumber() != null)) {
			season = video.seasonNumber();
			episodeNumber = video.episodeNumber();
			episode = new BrowseEpisode(sourceUuid, meta.type(), binding.providerMetaId(),
					video.providerVideoId(), safeTitle(video.title(), meta.name()), season,
					episodeNumber, null, video.thumbnailUrl(), meta.description(),
					duration(video.durationMs()));
			queue = QUEUE_PREFIX + meta.metaKey();
			back = StremioItemIds.season(media, season);
		}
		String artwork = firstText(video.thumbnailUrl(), meta.posterUrl(), meta.backgroundUrl());
		StremioSessionItem item = new StremioSessionItem(video.videoKey(), canonical(meta),
				sourceUuid, safeTitle(video.title(), meta.name()), episodeSubtitle(season,
				episodeNumber), artwork, video.durationMs(), back, queue, season, episodeNumber);
		return new StremioPersistedItem(item, meta.type(), binding.providerMetaId(),
				(episode == null) ? binding.providerMetaId() : video.providerVideoId(), media, episode);
	}

	private CompletableFuture<List<StremioSessionItem>> persistQueue(
			StremioMetaRecord meta, BrowseDetails details) {
		List<StremioPersistedItem> values = new ArrayList<>();
		List<CompletableFuture<Void>> writes = new ArrayList<>();
		for (var season : details.seasons()) {
			for (BrowseEpisode episode : season.episodes()) {
				StremioCanonicalIdentity canonical = StremioCanonicalIdentity.from(
						episode.seriesType(), episode.seriesId());
				StremioPlaybackIdentity identity = (canonical == null) ?
						StremioPlaybackIdentity.scoped(episode.sourceUuid(), episode.seriesType(),
								episode.seriesId(), episode.videoId()) :
						canonical.playbackIdentity(episode.seriesType(), episode.videoId(),
								episode.season(), episode.episode());
				StremioVideoRecord video = new StremioVideoRecord(identity.videoKey(),
						meta.metaKey(), episode.seriesType(), episode.videoId(), episode.title(),
						episode.season(), episode.episode(), 0L,
						durationMillis(episode.duration()), episode.thumbnail(),
						System.currentTimeMillis());
				StremioPersistedItem value = projection(new ProviderBinding(
						episode.sourceUuid(), episode.seriesId()), meta, video);
				values.add(value);
				writes.add(open(repository.putVideo(video)));
			}
		}
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
				.thenApply(ignored -> values.stream().map(StremioPersistedItem::item).toList());
	}

	private CompletableFuture<Void> write(String key, StremioPersistedItem projection) {
		return open(repository.putCache(StremioProjectionCodec.cache(
				key, projection.item().sourceUuid(), projection)));
	}

	private CompletableFuture<Void> replace(String key, StremioPersistedItem projection) {
		return open(repository.deleteCache(key)).thenCompose(ignored -> write(key, projection));
	}

	private CompletionStage<StremioProviderState> providerState(String sourceUuid) {
		return open(repository.getSource(sourceUuid)).thenApply(source -> {
			if (source == null) return StremioProviderState.REMOVED;
			return source.enabled() ? StremioProviderState.ENABLED :
					StremioProviderState.DISABLED;
		});
	}

	private static boolean replaceableCanonicalOwner(StremioCacheRecord existing,
			StremioPersistedItem cached, StremioPersistedItem replacement) {
		StremioSessionItem current = cached.item();
		StremioSessionItem next = replacement.item();
		if (!StremioProjectionCodec.ITEM_RESOURCE.equals(existing.resource()) ||
				!existing.sourceUuid().equals(current.sourceUuid()) ||
				!current.sourceUuid().equals(cached.media().sourceUuid()) ||
				!next.sourceUuid().equals(replacement.media().sourceUuid()) ||
				!current.stableId().equals(next.stableId()) ||
				!current.canonicalContentKey().equals(next.canonicalContentKey()) ||
				!cached.type().equals(replacement.type())) return false;
		return (StremioCanonicalIdentity.from(cached.type(),
				current.canonicalContentKey()) != null) &&
				(StremioCanonicalIdentity.from(replacement.type(),
						next.canonicalContentKey()) != null);
	}

	private static BrowseMedia media(ProviderBinding binding, StremioMetaRecord meta) {
		return new BrowseMedia(binding.sourceUuid(), meta.type(), binding.providerMetaId(),
				safeTitle(meta.name(), "Stremio"), meta.posterUrl(), meta.backgroundUrl(),
				meta.description(), meta.releaseInfo(), duration(meta.runtimeMs()), List.of(), null);
	}

	private static String canonical(StremioMetaRecord meta) {
		String canonical = meta.canonicalIdentity();
		return ((canonical != null) && !canonical.isBlank() && !unsafeIdentity(canonical)) ?
				canonical : CONTENT_PREFIX + StremioProjectionCodec.digest(meta.metaKey());
	}

	private static String episodeSubtitle(int season, int episode) {
		return (season < 0) ? "" : "S" + season + " E" + episode;
	}

	private static String safeTitle(@Nullable String title, @Nullable String fallback) {
		String value = (title == null) ? "" : title.strip();
		if (value.isEmpty() || URL_TITLE.matcher(value).matches()) {
			value = (fallback == null) ? "" : fallback.strip();
		}
		return (value.isEmpty() || URL_TITLE.matcher(value).matches()) ? "Stremio" : value;
	}

	private static boolean unsafeIdentity(String value) {
		return (value == null) || value.isBlank() || value.contains("://") ||
				value.regionMatches(true, 0, "javascript:", 0, 11);
	}

	@Nullable
	private static String firstText(String... values) {
		for (String value : values) if ((value != null) && !value.isBlank()) return value;
		return null;
	}

	@Nullable
	private static StremioDuration duration(long millis) {
		return (millis < 0) ? null : new StremioDuration(Long.toString(millis), millis);
	}

	private static long durationMillis(@Nullable StremioDuration duration) {
		return (duration == null) ? -1L : duration.milliseconds();
	}

	private <T> CompletableFuture<T> open(CompletableFuture<T> future) {
		return closed.getAsBoolean() ? CompletableFuture.failedFuture(
				new IllegalStateException("Stremio runtime is closed")) : future;
	}
	private record ProviderBinding(String sourceUuid, String providerMetaId) {
	}
}
