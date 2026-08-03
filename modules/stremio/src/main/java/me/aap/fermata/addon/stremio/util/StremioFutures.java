package me.aap.fermata.addon.stremio.util;

import java.util.concurrent.CompletableFuture;

/** Java API compatibility helpers used by the Stremio runtime. */
public final class StremioFutures {
	private StremioFutures() {
	}

	/** Equivalent to Java 9's failedFuture without requiring Android API 31. */
	public static <T> CompletableFuture<T> failedFuture(Throwable failure) {
		CompletableFuture<T> future = new CompletableFuture<>();
		future.completeExceptionally(failure);
		return future;
	}
}
