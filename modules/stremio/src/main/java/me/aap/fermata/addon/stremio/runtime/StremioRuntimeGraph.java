package me.aap.fermata.addon.stremio.runtime;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import me.aap.fermata.addon.stremio.browse.StremioBrowseRepository;
import me.aap.fermata.addon.stremio.integration.StremioBrowseGatewayAdapter;
import me.aap.fermata.addon.stremio.integration.StremioItemGatewayAdapter;
import me.aap.fermata.addon.stremio.integration.StremioProtocolClient;
import me.aap.fermata.addon.stremio.integration.StremioProviderCatalog;
import me.aap.fermata.addon.stremio.integration.StremioProviderSecretAccess;
import me.aap.fermata.addon.stremio.integration.StremioSessionGatewayAdapter;
import me.aap.fermata.addon.stremio.integration.StremioSourceUiGatewayAdapter;
import me.aap.fermata.addon.stremio.integration.StremioStreamProviderClientAdapter;
import me.aap.fermata.addon.stremio.integration.StremioSubtitleProviderAdapter;
import me.aap.fermata.addon.stremio.integration.StremioSubtitlePlaybackBridge;
import me.aap.fermata.addon.stremio.item.StremioItemGateway;
import me.aap.fermata.addon.stremio.playback.PlaybackDescriptorFactory;
import me.aap.fermata.addon.stremio.playback.PlaybackHeaderRegistry.HeaderStore;
import me.aap.fermata.addon.stremio.playback.StreamAggregator;
import me.aap.fermata.addon.stremio.session.StremioSessionCoordinator;
import me.aap.fermata.addon.stremio.session.StremioItemResolution;
import me.aap.fermata.addon.stremio.subtitle.SubtitleAggregator;
import me.aap.fermata.addon.stremio.subtitle.SubtitleProvider;
import me.aap.fermata.addon.stremio.ui.source.SourceUiGateway;
import me.aap.fermata.addon.stremio.source.StremioSourceSecretVault;
import me.aap.fermata.addon.stremio.source.StremioSecretReference;
import me.aap.fermata.addon.stremio.torrent.StremioTorrentEngine;
import java.io.File;

/** Fully wired, secret-free facade owned by one {@link StremioRuntime} generation. */
public final class StremioRuntimeGraph implements AutoCloseable {
	private static final int STREAM_CONCURRENCY = 4;
	private static final long STREAM_TIMEOUT_MILLIS = 10_000L;

	private final StremioProtocolClient protocolClient;
	private final StremioRuntimeSources runtimeSources;
	private final StremioProviderCatalog providers;
	private final StremioBrowseRepository browse;
	private final StreamAggregator streams;
	private final SubtitleAggregator subtitles = new SubtitleAggregator();
	private final StremioSubtitlePlaybackBridge subtitlePlayback;
	private final StremioTorrentEngine torrents;
	private final StremioItemGateway itemGateway;
	private final StremioSessionGatewayAdapter sessionGateway;
	private final StremioSessionCoordinator sessions;
	private final CompletionStage<StremioItemResolution> sessionRestoration;
	private final SourceUiGateway sourceUiGateway;
	private final HeaderStore headers = new HeaderStore();
	private final AutoCloseable headerSourceObserver;
	private final AtomicBoolean closed = new AtomicBoolean();

	StremioRuntimeGraph(StremioRuntime runtime, StremioSourceSecretVault vault,
			Executor executor, ScheduledExecutorService scheduler) {
		runtimeSources = runtime.sources();
		StremioProviderSecretAccess secretAccess = source ->
				vault.load(StremioSecretReference.resolve(source));
		headerSourceObserver = runtime.sources().observe(headers::reconcileSources);
		runtime.sources().sources().thenAccept(headers::reconcileSources)
				.exceptionally(ignored -> null);
		protocolClient = new StremioProtocolClient(runtime, secretAccess, executor, scheduler);
		torrents = new StremioTorrentEngine(
				new File(runtime.storageDirectory(), "torrents"), executor,
				runtime.addressResolver(), scheduler);
		providers = new StremioProviderCatalog(runtime, executor);
		browse = new StremioBrowseRepository(
				new StremioBrowseGatewayAdapter(protocolClient), executor, scheduler);
		streams = new StreamAggregator(new StremioStreamProviderClientAdapter(protocolClient),
				new PlaybackDescriptorFactory(headers, runtime.addressResolver()), executor, scheduler,
				System::currentTimeMillis, STREAM_CONCURRENCY, STREAM_TIMEOUT_MILLIS);
		subtitlePlayback = new StremioSubtitlePlaybackBridge(
				runtime, providers, protocolClient, subtitles, executor);
		StremioItemGateway baseItemGateway = new StremioItemGatewayAdapter(providers, browse, streams,
				runtime.repository(), executor, headers, subtitlePlayback, torrents);
		sessionGateway = new StremioSessionGatewayAdapter(runtime.repository(), providers,
				baseItemGateway, executor);
		itemGateway = sessionGateway;
		sessions = sessionGateway.sessions();
		sessionRestoration = sessions.restoreAfterProcessDeath().exceptionally(failure ->
				StremioItemResolution.missing());
		sourceUiGateway = new StremioSourceUiGatewayAdapter(
				runtime.sources(), secretAccess, runtime.httpClient(), protocolClient, providers);
	}

	public StremioItemGateway items() {
		return itemGateway;
	}

	public SourceUiGateway sources() {
		return sourceUiGateway;
	}

	/** Observes committed source revisions without exposing their contents to the UI. */
	public AutoCloseable observeSourceChanges(Runnable observer) {
		java.util.Objects.requireNonNull(observer, "observer");
		return runtimeSources.observe(ignored -> observer.run());
	}

	public StremioSessionCoordinator sessions() {
		return sessions;
	}

	/** Completes after the DB-backed SmartTop/current-item pointer has been reconstructed. */
	public CompletionStage<StremioItemResolution> sessionRestoration() {
		return sessionRestoration;
	}

	public StremioSessionGatewayAdapter sessionItems() {
		return sessionGateway;
	}

	/** Creates enabled subtitle provider bindings without exposing transport secrets. */
	public java.util.concurrent.CompletionStage<List<SubtitleProvider>> subtitleProviders(
			String type, String videoId) {
		return providers.browseProviders().thenApply(list -> list.stream()
				.filter(provider -> provider.manifest().resources().stream()
						.anyMatch(resource -> resource.name().equals("subtitles")))
				.map(provider -> (SubtitleProvider) new StremioSubtitleProviderAdapter(
						protocolClient, provider.sourceUuid(), provider.manifest().id(),
						provider.displayName(), type, videoId))
				.toList());
	}

	public SubtitleAggregator subtitles() {
		return subtitles;
	}

	public boolean isClosed() {
		return closed.get();
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		if (sourceUiGateway instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
			}
		}
		if (itemGateway instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
			}
		}
		streams.close();
		subtitlePlayback.close();
		torrents.close();
		browse.close();
		protocolClient.close();
		try {
			headerSourceObserver.close();
		} catch (Exception ignored) {
		}
		headers.close();
	}

}
