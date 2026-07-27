package me.aap.fermata.addon.stremio.source;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import me.aap.fermata.addon.stremio.security.StremioSecretStore;
import me.aap.fermata.addon.stremio.security.StremioSourceSecret;

/** Executes the existing encrypted store away from the caller thread. */
public final class StremioSecretStoreAdapter implements StremioSourceSecretVault {
	private final StremioSecretStore store;
	private final Executor executor;

	public StremioSecretStoreAdapter(StremioSecretStore store, Executor executor) {
		this.store = Objects.requireNonNull(store, "store");
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	@Override
	public CompletableFuture<StremioSourceSecret> load(String sourceUuid) {
		return CompletableFuture.supplyAsync(() -> store.load(sourceUuid), executor);
	}

	@Override
	public CompletableFuture<Void> save(String sourceUuid, StremioSourceSecret secret) {
		return CompletableFuture.runAsync(() -> store.save(sourceUuid, secret), executor);
	}

	@Override
	public CompletableFuture<Void> remove(String sourceUuid) {
		return CompletableFuture.runAsync(() -> store.remove(sourceUuid), executor);
	}
}
