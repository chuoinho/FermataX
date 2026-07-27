package me.aap.fermata.addon.stremio.lifecycle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.stremio.net.RequestGeneration;

public class StremioOperationTest {
	private ScheduledExecutorService scheduler;

	@Before
	public void setUp() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
	}

	@After
	public void tearDown() {
		scheduler.shutdownNow();
	}

	@Test
	public void generationInvalidationCancelsOwnedCallAndRetiresOperation() {
		var generation = new RequestGeneration();
		var scope = new StremioOperationScope(scheduler);
		var operation = scope.open("catalog:home", generation.begin());
		var call = operation.own(new FakeCall());

		generation.begin();

		assertTrue(call.cancelled.get());
		assertFalse(operation.isCurrent());
		assertEquals(0, scope.activeCount());
		scope.close();
	}

	@Test
	public void closeReleasesResourcesInReverseOrderAndCancelsDeadline() {
		var scope = new StremioOperationScope(scheduler);
		var operation = scope.open("stream:resolve", null);
		var order = new StringBuilder();
		operation.own((AutoCloseable) () -> order.append('A'));
		operation.own((AutoCloseable) () -> order.append('B'));
		AtomicInteger deadlines = new AtomicInteger();
		var deadline = operation.deadline(Duration.ofHours(1), deadlines::incrementAndGet);

		operation.close();

		assertEquals("BA", order.toString());
		assertEquals(0, deadlines.get());
		assertTrue(deadline.isCancelled());
		assertEquals(0, scope.activeCount());
	}

	@Test
	public void completedCallDetachesWithoutBeingCancelled() {
		var scope = new StremioOperationScope(scheduler);
		var operation = scope.open("meta:item", null);
		var call = operation.own(new FakeCall());
		call.result.complete("ready");

		operation.close();

		assertFalse(call.cancelled.get());
		assertEquals(0, scope.activeCount());
	}

	@Test
	public void staleGenerationCannotOpenOperation() {
		var generation = new RequestGeneration();
		var stale = generation.begin();
		generation.begin();
		var scope = new StremioOperationScope(scheduler);

		assertThrows(java.util.concurrent.CancellationException.class,
				() -> scope.open("stale", stale));
		assertEquals(0, scope.activeCount());
	}

	private static final class FakeCall implements StremioCall<String> {
		private final CompletableFuture<String> result = new CompletableFuture<>();
		private final AtomicBoolean cancelled = new AtomicBoolean();

		@Override
		public CompletionStage<String> completion() {
			return result;
		}

		@Override
		public void cancel() {
			cancelled.set(true);
			result.cancel(false);
		}
	}
}
