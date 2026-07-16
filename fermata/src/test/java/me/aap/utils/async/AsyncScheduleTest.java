package me.aap.utils.async;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Test;

public class AsyncScheduleTest {
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@After
	public void closeScheduler() {
		scheduler.shutdownNow();
	}

	@Test
	public void cancelBeforeDelayPreventsSupplierFromRunning() throws Exception {
		AtomicBoolean ran = new AtomicBoolean();
		FutureSupplier<Void> future = Async.schedule(scheduler, () -> {
			ran.set(true);
			return Completed.completedVoid();
		}, 10_000L);

		assertTrue(future.cancel());
		scheduler.shutdown();
		assertTrue(scheduler.awaitTermination(1, SECONDS));
		assertFalse(ran.get());
	}

	@Test
	public void cancelAfterStartCancelsChildFuture() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch childCancelled = new CountDownLatch(1);
		Promise<Void> child = new Promise<>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				boolean cancelled = super.cancel(mayInterruptIfRunning);
				childCancelled.countDown();
				return cancelled;
			}
		};
		FutureSupplier<Void> future = Async.schedule(scheduler, () -> {
			started.countDown();
			return child;
		}, 0L);

		assertTrue(started.await(1, SECONDS));
		assertTrue(future.cancel());
		assertTrue(childCancelled.await(1, SECONDS));
		assertTrue(child.isCancelled());
	}
}
