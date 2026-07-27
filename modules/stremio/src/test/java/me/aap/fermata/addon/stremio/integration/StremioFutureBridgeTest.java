package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class StremioFutureBridgeTest {
	@Test
	public void cancellationPropagatesExactlyOnce() {
		CompletableFuture<String> stage = new CompletableFuture<>();
		AtomicInteger cancellations = new AtomicInteger();
		var bridged = StremioFutureBridge.from(stage, cancellations::incrementAndGet);

		assertTrue(bridged.cancel());
		bridged.cancel();

		assertEquals(1, cancellations.get());
	}

	@Test
	public void defaultBridgeCancelsCompletableFuture() {
		CompletableFuture<String> stage = new CompletableFuture<>();
		var bridged = StremioFutureBridge.from(stage);

		bridged.cancel();

		assertTrue(stage.isCancelled());
	}
}
