package me.aap.fermata.addon.stremio.runtime;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.net.AddressResolver;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.source.StremioSourceManager;
import me.aap.fermata.addon.stremio.source.StremioSourceSecretVault;

/** Lifecycle owner for all Stremio persistence, source and network resources. */
public final class StremioRuntime implements AutoCloseable {
	private final StremioRepository repository;
	private final File storageDirectory;
	private final StremioSourceManager sourceManager;
	private final StremioRuntimeSources sources;
	private final StremioRuntimeGraph graph;
	private final ProductionStremioManifestClient manifestClient;
	private final StremioRuntimeHttpClient httpClient;
	private final LifecycleHttpTransport transport;
	private final RequestGeneration lifecycleGeneration;
	private final AddressResolver addressResolver;
	private final ExecutorService ioExecutor;
	private final ExecutorService secretExecutor;
	private final ScheduledExecutorService scheduler;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicReference<CompletableFuture<Void>> closeFuture = new AtomicReference<>();

	StremioRuntime(File storageDirectory, StremioRepository repository, StremioSourceManager sourceManager,
			StremioSourceSecretVault secretVault,
			ProductionStremioManifestClient manifestClient, StremioRuntimeHttpClient httpClient,
			LifecycleHttpTransport transport, RequestGeneration lifecycleGeneration,
			AddressResolver addressResolver,
			ExecutorService ioExecutor, ExecutorService secretExecutor,
			ScheduledExecutorService scheduler) {
		this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory");
		this.repository = Objects.requireNonNull(repository, "repository");
		this.sourceManager = Objects.requireNonNull(sourceManager, "sourceManager");
		this.sources = new StremioRuntimeSources(sourceManager);
		this.manifestClient = Objects.requireNonNull(manifestClient, "manifestClient");
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
		this.transport = Objects.requireNonNull(transport, "transport");
		this.lifecycleGeneration = Objects.requireNonNull(lifecycleGeneration,
				"lifecycleGeneration");
		this.addressResolver = Objects.requireNonNull(addressResolver, "addressResolver");
		this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
		this.secretExecutor = Objects.requireNonNull(secretExecutor, "secretExecutor");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		graph = new StremioRuntimeGraph(this,
				Objects.requireNonNull(secretVault, "secretVault"), ioExecutor, scheduler);
	}

	public StremioSourceManager sourceManager() {
		return sourceManager;
	}

	/** UI integrations use this facade so stremio:// deep links are canonicalized consistently. */
	public StremioRuntimeSources sources() {
		return sources;
	}

	public StremioRepository repository() {
		return repository;
	}

	File storageDirectory() {
		return storageDirectory;
	}

	public StremioRuntimeHttpClient httpClient() {
		return httpClient;
	}

	AddressResolver addressResolver() {
		return addressResolver;
	}

	/** Secret-free runtime graph consumed by the addon shell and native UI. */
	public StremioRuntimeGraph graph() {
		return graph;
	}

	public boolean isClosed() {
		return closed.get();
	}

	public CompletableFuture<Void> closeAsync() {
		CompletableFuture<Void> existing = closeFuture.get();
		if (existing != null) return existing;

		var closing = new CompletableFuture<Void>();
		if (!closeFuture.compareAndSet(null, closing)) return closeFuture.get();
		closed.set(true);
		graph.close();
		sourceManager.close();
		manifestClient.close();
		httpClient.close();
		transport.close();
		lifecycleGeneration.close();
		closeResolver();
		secretExecutor.shutdownNow();
		ioExecutor.shutdownNow();
		scheduler.shutdownNow();
		repository.closeAsync().whenComplete((ignored, error) -> {
			if (error == null) closing.complete(null);
			else closing.completeExceptionally(error);
		});
		return closing;
	}

	@Override
	public void close() {
		closeAsync();
	}

	private void closeResolver() {
		if (addressResolver instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
				// Repository close remains the authoritative lifecycle result.
			}
		}
	}
}
