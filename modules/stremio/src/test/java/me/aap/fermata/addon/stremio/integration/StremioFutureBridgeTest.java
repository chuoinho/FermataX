package me.aap.fermata.addon.stremio.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.utils.async.Promise;

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

	@Test
	public void toCompletablePreservesCompletion() {
		Promise<String> supplier = new Promise<>();
		CompletableFuture<String> stage = StremioFutureBridge.toCompletable(supplier);

		supplier.complete("value");

		assertEquals("value", stage.join());
	}

	@Test
	public void toCompletablePreservesFailure() {
		Promise<String> supplier = new Promise<>();
		CompletableFuture<String> stage = StremioFutureBridge.toCompletable(supplier);
		RuntimeException failure = new RuntimeException("failure");

		supplier.completeExceptionally(failure);

		try {
			stage.join();
			fail("Expected completion failure");
		} catch (CompletionException error) {
			assertSame(failure, error.getCause());
		}
	}
}
