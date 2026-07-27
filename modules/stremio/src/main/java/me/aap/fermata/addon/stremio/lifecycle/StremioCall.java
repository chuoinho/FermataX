package me.aap.fermata.addon.stremio.lifecycle;

import java.util.concurrent.CompletionStage;

/**
 * Common lifecycle boundary for one cancellable Stremio operation.
 *
 * Implementations may expose a more specific future type covariantly. The
 * The existing FutureSupplier/CompletableFuture adapters remain available while
 * lifecycle ownership is migrated gradually.
 */
public interface StremioCall<T> {
	CompletionStage<T> completion();

	void cancel();

	default boolean isActive() {
		return !completion().toCompletableFuture().isDone();
	}
}
