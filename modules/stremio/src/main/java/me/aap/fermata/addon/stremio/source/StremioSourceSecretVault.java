package me.aap.fermata.addon.stremio.source;

import java.util.concurrent.CompletableFuture;

import me.aap.fermata.addon.stremio.security.StremioSourceSecret;

/** Async encrypted-secret boundary. */
public interface StremioSourceSecretVault {
	CompletableFuture<StremioSourceSecret> load(String sourceUuid);

	CompletableFuture<Void> save(String sourceUuid, StremioSourceSecret secret);

	CompletableFuture<Void> remove(String sourceUuid);
}
