package me.aap.fermata.addon.stremio.playback;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.aap.fermata.addon.stremio.lifecycle.StremioCall;

public interface StreamAggregationCall extends StremioCall<StreamAggregationResult> {
	/** Interactive result: all providers completed, or the 2.5-second settle snapshot. */
	CompletableFuture<StreamAggregationResult> response();

	/** Final result after every provider has completed or reached its provider timeout. */
	CompletableFuture<StreamAggregationResult> completion();

	/** Receives immutable incremental snapshots and the final snapshot. */
	AutoCloseable observe(Consumer<StreamAggregationResult> observer);

	void cancel();
}
