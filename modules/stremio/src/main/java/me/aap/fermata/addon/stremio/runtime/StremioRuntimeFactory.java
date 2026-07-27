package me.aap.fermata.addon.stremio.runtime;

import android.content.Context;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.addon.stremio.data.StremioRepository;
import me.aap.fermata.addon.stremio.net.AddressResolver;
import me.aap.fermata.addon.stremio.net.NetworkConsent;
import me.aap.fermata.addon.stremio.net.RequestGeneration;
import me.aap.fermata.addon.stremio.net.cache.BoundedLruCache;
import me.aap.fermata.addon.stremio.net.cache.CachedHttpClient;
import me.aap.fermata.addon.stremio.net.http.HttpTransport;
import me.aap.fermata.addon.stremio.net.http.ProjectHttpTransport;
import me.aap.fermata.addon.stremio.net.http.StremioHttpClient;
import me.aap.fermata.addon.stremio.security.StremioSecretStore;
import me.aap.fermata.addon.stremio.source.StremioManifestParser;
import me.aap.fermata.addon.stremio.source.StremioSecretStoreAdapter;
import me.aap.fermata.addon.stremio.source.StremioSourceManager;
import me.aap.fermata.addon.stremio.source.StremioSourceRepositoryAdapter;
import me.aap.fermata.addon.stremio.source.StremioSourceSecretVault;
import me.aap.fermata.addon.stremio.source.StremioSqliteSourceIndexStore;

/** Builds the production runtime without doing database, keystore, DNS or HTTP work on the caller. */
public final class StremioRuntimeFactory {
	static final int JSON_CACHE_ENTRIES = 128;
	static final long JSON_CACHE_BYTES = 12L * 1024L * 1024L;
	static final long JSON_CACHE_ENTRY_BYTES = 1L * 1024L * 1024L;

	private StremioRuntimeFactory() {
	}

	public static CompletableFuture<StremioRuntime> open(
			Context context, NetworkConsent consent) {
		Objects.requireNonNull(context, "context");
		Context application = context.getApplicationContext();
		if (application == null) application = context;
		File directory = new File(application.getFilesDir(), "stremio");
		Context app = application;
		return openOwned(directory, consent, new ProjectHttpTransport(), null,
				secretExecutor -> new StremioSecretStoreAdapter(
						new StremioSecretStore(app), secretExecutor));
	}

	static CompletableFuture<StremioRuntime> openForTest(File directory,
			NetworkConsent consent, HttpTransport transport, AddressResolver resolver,
			StremioSourceSecretVault secretVault) {
		return openOwned(directory, consent, transport, resolver, ignored -> secretVault);
	}

	private static CompletableFuture<StremioRuntime> openOwned(File directory,
			NetworkConsent consent, HttpTransport transport, AddressResolver injectedResolver,
			VaultFactory vaultFactory) {
		Objects.requireNonNull(directory, "directory");
		Objects.requireNonNull(consent, "consent");
		Objects.requireNonNull(transport, "transport");
		Objects.requireNonNull(vaultFactory, "vaultFactory");

		ExecutorService io = Executors.newFixedThreadPool(3, threads("FermataX-Stremio-IO"));
		ExecutorService secrets = Executors.newSingleThreadExecutor(
				threads("FermataX-Stremio-Secrets"));
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
				threads("FermataX-Stremio-Timer"));
		ExecutorService dns = (injectedResolver == null) ? Executors.newSingleThreadExecutor(
				threads("FermataX-Stremio-DNS")) : null;

		CompletableFuture<StremioRuntime> result = new CompletableFuture<>();
		try {
			io.execute(() -> {
				StremioRuntime runtime = null;
				StremioRepository repository = null;
				LifecycleHttpTransport ownedTransport = null;
				RequestGeneration lifecycle = null;
				AddressResolver runtimeResolver = null;
				try {
					if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
						throw new IllegalStateException("Unable to create Stremio storage");
					}
					AddressResolver resolver = (injectedResolver != null) ? injectedResolver :
							new ProductionAddressResolver(dns);
					runtimeResolver = resolver;
					repository = new StremioRepository(
							new File(directory, "stremio.db"));
					ownedTransport = new LifecycleHttpTransport(transport);
					StremioHttpClient rawHttp = new StremioHttpClient(resolver, ownedTransport,
							scheduler, io);
					CachedHttpClient cached = new CachedHttpClient(rawHttp,
							new BoundedLruCache(JSON_CACHE_ENTRIES, JSON_CACHE_BYTES,
									JSON_CACHE_ENTRY_BYTES),
							System::currentTimeMillis);
					lifecycle = new RequestGeneration();
					RequestGeneration.Token token = lifecycle.begin();
					ProductionStremioManifestClient manifests =
							new ProductionStremioManifestClient(rawHttp, consent, scheduler, token);
					StremioRuntimeHttpClient runtimeHttp =
							new StremioRuntimeHttpClient(cached, rawHttp, consent, token);
					StremioSourceRepositoryAdapter sourceStore =
							new StremioSourceRepositoryAdapter(repository,
									new StremioSqliteSourceIndexStore(repository));
					StremioSourceSecretVault vault = vaultFactory.create(secrets);
					StremioSourceManager sources = new StremioSourceManager(sourceStore,
							vault, manifests,
							StremioManifestParser.strict());
					runtime = new StremioRuntime(directory, repository, sources, vault, manifests, runtimeHttp,
							ownedTransport, lifecycle, resolver, io, secrets, scheduler);
					StremioRuntime created = runtime;
					repository.ready().whenCompleteAsync((ignored, error) -> {
						if (error == null) {
							if (!result.complete(created)) created.closeAsync();
						} else {
							created.closeAsync();
							result.completeExceptionally(error);
						}
					}, io);
				} catch (Throwable error) {
					if (runtime != null) runtime.closeAsync();
					else closePartial(repository, ownedTransport, lifecycle, runtimeResolver,
							io, secrets, scheduler, dns);
					result.completeExceptionally(error);
				}
			});
		} catch (Throwable error) {
			shutdown(io, secrets, scheduler, dns);
			result.completeExceptionally(error);
		}
		return result;
	}

	private static void closePartial(StremioRepository repository,
			LifecycleHttpTransport transport, RequestGeneration lifecycle,
			AddressResolver resolver, ExecutorService io, ExecutorService secrets,
			ScheduledExecutorService scheduler, ExecutorService dns) {
		if (lifecycle != null) lifecycle.close();
		if (transport != null) transport.close();
		if (resolver instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception ignored) {
			}
		}
		if (repository == null) shutdown(io, secrets, scheduler, dns);
		else repository.closeAsync().whenComplete((ignored, error) ->
				shutdown(io, secrets, scheduler, dns));
	}

	private static ThreadFactory threads(String prefix) {
		AtomicInteger sequence = new AtomicInteger();
		return task -> {
			Thread thread = new Thread(task, prefix + '-' + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	private static void shutdown(ExecutorService io, ExecutorService secrets,
			ScheduledExecutorService scheduler, ExecutorService dns) {
		if (dns != null) dns.shutdownNow();
		secrets.shutdownNow();
		io.shutdownNow();
		scheduler.shutdownNow();
	}

	@FunctionalInterface
	private interface VaultFactory {
		StremioSourceSecretVault create(ExecutorService executor);
	}
}
