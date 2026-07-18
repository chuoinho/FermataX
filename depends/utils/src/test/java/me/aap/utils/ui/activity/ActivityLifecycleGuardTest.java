package me.aap.utils.ui.activity;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ActivityLifecycleGuardTest {
	@Test
	void ignoresCompletionAfterCancel() {
		ActivityLifecycleGuard guard = new ActivityLifecycleGuard();
		long generation = guard.begin();
		AtomicInteger calls = new AtomicInteger();

		guard.cancel();

		assertFalse(guard.runIfCurrent(generation, calls::incrementAndGet));
		assertEquals(0, calls.get());
	}

	@Test
	void ignoresCompletionFromAnOlderGeneration() {
		ActivityLifecycleGuard guard = new ActivityLifecycleGuard();
		long oldGeneration = guard.begin();
		long currentGeneration = guard.begin();
		AtomicInteger calls = new AtomicInteger();

		assertFalse(guard.runIfCurrent(oldGeneration, calls::incrementAndGet));
		assertTrue(guard.runIfCurrent(currentGeneration, calls::incrementAndGet));
		assertEquals(1, calls.get());
	}

	@Test
	void cancelWaitsForRunningCompletion() throws Exception {
		ActivityLifecycleGuard guard = new ActivityLifecycleGuard();
		long generation = guard.begin();
		CountDownLatch completionStarted = new CountDownLatch(1);
		CountDownLatch releaseCompletion = new CountDownLatch(1);
		CountDownLatch cancellationStarted = new CountDownLatch(1);
		AtomicBoolean completionRan = new AtomicBoolean();
		AtomicBoolean cancelReturned = new AtomicBoolean();

		Thread completion = new Thread(() -> guard.runIfCurrent(generation, () -> {
			completionStarted.countDown();
			try {
				releaseCompletion.await();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			completionRan.set(true);
		}));
		Thread cancellation = new Thread(() -> {
			cancellationStarted.countDown();
			guard.cancel();
			cancelReturned.set(true);
		});

		completion.start();
		assertTrue(completionStarted.await(1, SECONDS));
		cancellation.start();
		assertTrue(cancellationStarted.await(1, SECONDS));
		assertFalse(cancelReturned.get());

		releaseCompletion.countDown();
		completion.join(1000);
		cancellation.join(1000);

		assertTrue(completionRan.get());
		assertTrue(cancelReturned.get());
	}
}
