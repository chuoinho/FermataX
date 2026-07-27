package me.aap.fermata.addon.stremio.presentation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import me.aap.fermata.addon.stremio.browse.BrowseEpisode;
import me.aap.fermata.addon.stremio.browse.BrowseMedia;
import me.aap.fermata.addon.stremio.browse.BrowseSeason;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.item.StremioPlaybackSelection;
import me.aap.fermata.addon.stremio.playback.StreamAggregationRequest;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregationResult;
import me.aap.utils.async.FutureSupplier;

/**
 * Maps the existing Stremio repositories to film-first, secret-free renderer state.
 * Playback and persistent MediaLib item ownership remain outside this class.
 */
public final class StremioPresentationGateway implements StremioPresenter.Loader,
		AutoCloseable {
	private final StremioItemGateway items;
	private final StremioSessionCoordinator sessions;
	private final StremioPresentationText text;
	private final StremioPresentationFormatter formatter;
	private final StremioSearchPageLoader searchLoader;
	private final StremioLibraryPageLoader libraryLoader;
	private final StremioDiscoverPageLoader discoverLoader;
	private final StremioHomePageLoader homeLoader;
	private final StremioStreamPageLoader streamLoader;
	private final StremioDetailsPageLoader detailsLoader;
	private final StremioPresentationTargetStore targets = new StremioPresentationTargetStore();
	private volatile boolean closed;

	public StremioPresentationGateway(
			StremioItemGateway items, StremioPresentationText text) {
		this(items, null, text);
	}

	public StremioPresentationGateway(StremioItemGateway items,
			StremioSessionCoordinator sessions, StremioPresentationText text) {
		this.items = Objects.requireNonNull(items, "items");
		this.sessions = sessions;
		this.text = Objects.requireNonNull(text, "text");
		formatter = new StremioPresentationFormatter(this.text);
		searchLoader = new StremioSearchPageLoader(this.items, this.text, this::rememberMedia);
		libraryLoader = new StremioLibraryPageLoader(this.sessions, this.text,
				this::rememberResume);
		discoverLoader = new StremioDiscoverPageLoader(this.items, this.text, formatter,
				this::rememberMedia);
		homeLoader = new StremioHomePageLoader(this.items, this.sessions, this.text,
				this::rememberMedia, this::rememberResume);
		streamLoader = new StremioStreamPageLoader(this.items, this.text, targets);
		detailsLoader = new StremioDetailsPageLoader(this.items, this.sessions, this.text,
				formatter, targets, streamLoader);
	}

	@Override
	public StremioPresenter.Request load(StremioRoute route) {
		StremioPresentationRequest request = new StremioPresentationRequest(
				Objects.requireNonNull(route, "route"), () -> closed);
		if (closed) {
			request.fail(new IllegalStateException("Stremio presentation is closed"));
			return request;
		}
		try {
			CompletionStage<StremioPresentationPage> result;
			if (route instanceof StremioRoute.Home) result = homeLoader.load(request);
			else if (route instanceof StremioRoute.Discover discover) {
				result = discoverLoader.load(request, discover);
			} else if (route instanceof StremioRoute.Search search) {
				result = searchLoader.load(request, search);
			} else if (route instanceof StremioRoute.Details details) {
				result = detailsLoader.load(request, details);
			} else if (route instanceof StremioRoute.Streams streams) {
				result = streamLoader.load(request, streams);
			} else if (route instanceof StremioRoute.Library library) {
				result = libraryLoader.load(request, library);
			} else {
				result = CompletableFuture.failedFuture(
						new IllegalArgumentException("Unsupported Stremio route"));
			}
			request.finish(result);
		} catch (RuntimeException failure) {
			request.fail(failure);
		}
		return request;
	}

	private String rememberMedia(BrowseMedia media) {
		return targets.rememberMedia(media);
	}

	public void rememberResume(String stableId, long positionMs, long durationMs) {
		targets.rememberResume(stableId, positionMs, durationMs);
	}

	public void forgetResume(String stableId) {
		targets.forgetResume(stableId);
	}

	public void transferResume(String persistentId, String routeId) {
		targets.transferResume(persistentId, routeId);
	}

	private String rememberEpisode(
			BrowseMedia media, BrowseEpisode episode, BrowseSeason season) {
		return targets.rememberEpisode(media, episode, season);
	}

	public StremioRoute.Details adopt(BrowseMedia media) {
		return new StremioRoute.Details(rememberMedia(media));
	}

	public StremioRoute.Streams adopt(BrowseMedia media, BrowseEpisode episode,
			BrowseSeason season) {
		String key = (episode == null) ? rememberMedia(media) :
				rememberEpisode(media, episode, season);
		return new StremioRoute.Streams(key);
	}

	public StremioPlaybackSelection playbackTarget(String key) {
		return targets.playback(key);
	}

	public FavoriteTarget favoriteTarget(String key) {
		return targets.favorite(key);
	}

	public SubtitleTarget subtitleTarget(String key) {
		return targets.subtitle(key);
	}

	public FutureSupplier<SubtitleAggregationResult> subtitles(SubtitleTarget target) {
		Objects.requireNonNull(target, "target");
		return items.subtitles(target.request().type(), target.request().videoId());
	}

	static String streamQuality(String... values) {
		return StremioPresentationFormatter.streamQuality(values);
	}

	@Override
	public void close() {
		closed = true;
		targets.close();
		discoverLoader.close();
	}

	public record FavoriteTarget(String stableId, boolean favorite) {
		public FavoriteTarget {
			if ((stableId == null) || stableId.isBlank() || stableId.contains("://")) {
				throw new IllegalArgumentException("favorite target must be opaque");
			}
		}
	}

	public record SubtitleTarget(StreamAggregationRequest request) {
		public SubtitleTarget {
			Objects.requireNonNull(request, "request");
		}

		public String videoKey() {
			return request.identity().videoKey();
		}
	}


}
