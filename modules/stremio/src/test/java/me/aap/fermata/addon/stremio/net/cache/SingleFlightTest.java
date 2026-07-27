package me.aap.fermata.addon.stremio.net.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class SingleFlightTest {
	@Test
	public void coalescesConcurrentSubscribersAndCancelsOnlyAfterLastLeaves() {
		var singleFlight = new SingleFlight<String, String>();
		var created = new AtomicInteger();
		var operation = new TestOperation();
		var first = singleFlight.execute("key", () -> {
			created.incrementAndGet();
			return operation;
		});
		var second = singleFlight.execute("key", () -> {
			created.incrementAndGet();
			return operation;
		});

		assertEquals(1, created.get());
		assertEquals(1, singleFlight.activeCount());
		first.cancel();
		assertFalse(operation.cancelled.get());
		second.cancel();
		assertTrue(operation.cancelled.get());
	}

	@Test
	public void fansOutSingleResultAndRemovesCompletedOperation() {
		var singleFlight = new SingleFlight<String, String>();
		var operation = new TestOperation();
		var first = singleFlight.execute("key", () -> operation);
		var second = singleFlight.execute("key", () -> operation);
		operation.result.complete("done");

		assertEquals("done", first.response().join());
		assertEquals("done", second.response().join());
		assertEquals(0, singleFlight.activeCount());
	}

	private static final class TestOperation implements SingleFlight.Operation<String> {
		private final CompletableFuture<String> result = new CompletableFuture<>();
		private final AtomicBoolean cancelled = new AtomicBoolean();

		@Override
		public CompletableFuture<String> response() {
			return result;
		}

		@Override
		public void cancel() {
			cancelled.set(true);
		}
	}
}
