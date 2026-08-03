package me.aap.fermata.media.lib;

import static me.aap.fermata.media.lib.RefreshCoordinator.FailureKind.NETWORK;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.CANCELLED;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.FAILED;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.INACTIVE;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.SKIPPED_BACKOFF;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.SKIPPED_COOLDOWN;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;

public class RefreshCoordinatorTest {
	private static final long BACKOFF_BASE_MILLIS = 100L;
	private static final long BACKOFF_MAX_MILLIS = 400L;

	@Test
	public void simultaneousAutoAndManualRequestsJoinByKey() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(600_000L, now::get);
		Promise<Void> operation = new Promise<>();
		AtomicInteger starts = new AtomicInteger();

		FutureSupplier<RefreshCoordinator.Result<String>> auto = coordinator.auto("source-1", () -> {
			starts.incrementAndGet();
			return operation;
		});
		FutureSupplier<RefreshCoordinator.Result<String>> manual = coordinator.manual("source-1", () -> {
			starts.incrementAndGet();
			return new Promise<>();
		});

		assertEquals(1, starts.get());
		operation.complete(null);
		assertEquals(SUCCESS, auto.peek().status());
		assertEquals(SUCCESS, manual.peek().status());
	}

	@Test
	public void cooldownStartsOnlyAfterSuccess() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(600_000L, now::get);
		AtomicInteger starts = new AtomicInteger();

		RefreshCoordinator.Result<String> first = coordinator.auto("source", () -> {
			starts.incrementAndGet();
			Promise<Void> failed = new Promise<>();
			failed.completeExceptionally(new IllegalStateException("provider failed"));
			return failed;
		}).peek();
		assertEquals(FAILED, first.status());

		now.addAndGet(60_000L);
		Promise<Void> success = new Promise<>();
		RefreshCoordinator.Result<String> retry = coordinator.auto("source", () -> {
			starts.incrementAndGet();
			return success;
		}).peek();
		assertNull(retry);
		success.complete(null);
		assertEquals(2, starts.get());

		now.addAndGet(599_999L);
		assertEquals(SKIPPED_COOLDOWN,
				coordinator.auto("source", () -> new Promise<>()).peek().status());
		now.incrementAndGet();
		assertFalse(coordinator.auto("source", Promise::new).isDone());
	}

	@Test
	public void autoRequestIsSkippedWithinFailureBackoff() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = coordinator(now);
		AtomicInteger starts = new AtomicInteger();

		assertEquals(FAILED, coordinator.auto("source", () -> {
			starts.incrementAndGet();
			return failedNetworkOperation();
		}).peek().status());
		assertFalse(coordinator.isDue("source", now.get()));

		RefreshCoordinator.Result<String> skipped = coordinator.auto("source", () -> {
			starts.incrementAndGet();
			return new Promise<>();
		}).peek();
		assertEquals(SKIPPED_BACKOFF, skipped.status());
		assertEquals(1, starts.get());
	}

	@Test
	public void autoRequestProceedsAfterFailureBackoffElapses() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = coordinator(now);
		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);
		now.addAndGet(BACKOFF_BASE_MILLIS);
		assertTrue(coordinator.isDue("source", now.get()));

		AtomicInteger starts = new AtomicInteger();
		FutureSupplier<RefreshCoordinator.Result<String>> retry =
				coordinator.auto("source", () -> {
					starts.incrementAndGet();
					return new Promise<>();
				});

		assertEquals(1, starts.get());
		assertFalse(retry.isDone());
	}

	@Test
	public void failureBackoffEscalatesAndCaps() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = coordinator(now);

		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);
		assertBackoffBoundary(coordinator, now, BACKOFF_BASE_MILLIS);

		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);
		assertBackoffBoundary(coordinator, now, BACKOFF_BASE_MILLIS * 2L);

		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);
		assertBackoffBoundary(coordinator, now, BACKOFF_MAX_MILLIS);

		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);
		assertBackoffBoundary(coordinator, now, BACKOFF_MAX_MILLIS);
	}

	@Test
	public void defaultBackoffGrowsFromOneMinuteAndCapsAtThirtyMinutes() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(0L, now::get);
		long[] delays = {60_000L, 120_000L, 240_000L, 480_000L, 960_000L,
				1_800_000L, 1_800_000L};

		for (long delay : delays) {
			assertEquals(FAILED,
					coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation)
							.peek().status());
			assertBackoffBoundary(coordinator, now, delay);
		}
	}

	@Test
	public void providerFailuresAlsoUseBackoff() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(0L, now::get);

		assertEquals(FAILED,
				coordinator.auto("source", RefreshCoordinatorTest::failedProviderOperation)
						.peek().status());
		now.addAndGet(59_999L);
		assertEquals(SKIPPED_BACKOFF,
				coordinator.auto("source", Promise::new).peek().status());
		now.incrementAndGet();
		assertFalse(coordinator.auto("source", Promise::new).isDone());
	}

	@Test
	public void successResetsFailureBackoff() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = coordinator(now);
		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);
		now.addAndGet(BACKOFF_BASE_MILLIS);
		assertEquals(SUCCESS,
				coordinator.auto("source", () -> completedOperation()).peek().status());
		assertEquals(FAILED,
				coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation)
						.peek().status());

		now.addAndGet(BACKOFF_BASE_MILLIS - 1L);
		assertEquals(SKIPPED_BACKOFF,
				coordinator.auto("source", Promise::new).peek().status());
		now.incrementAndGet();
		assertFalse(coordinator.auto("source", Promise::new).isDone());
	}

	@Test
	public void manualAndReplaceBypassFailureBackoff() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = coordinator(now);
		coordinator.auto("manual", RefreshCoordinatorTest::failedNetworkOperation);
		coordinator.auto("edit", RefreshCoordinatorTest::failedNetworkOperation);

		AtomicInteger starts = new AtomicInteger();
		FutureSupplier<?> manual = coordinator.manual("manual", () -> {
			starts.incrementAndGet();
			return new Promise<>();
		});
		FutureSupplier<?> edit = coordinator.replace("edit", () -> {
			starts.incrementAndGet();
			return new Promise<>();
		});

		assertEquals(2, starts.get());
		assertFalse(manual.isDone());
		assertFalse(edit.isDone());
	}

	@Test
	public void resetClearsFailureBackoff() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = coordinator(now);
		coordinator.auto("source", RefreshCoordinatorTest::failedNetworkOperation);

		coordinator.reset();

		assertFalse(coordinator.auto("source", Promise::new).isDone());
	}

	@Test
	public void allSuccessBehaviorStillUsesOnlyTheConfiguredCooldown() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(600_000L, now::get);
		AtomicInteger starts = new AtomicInteger();

		assertEquals(SUCCESS, coordinator.auto("source", () -> {
			starts.incrementAndGet();
			return completedOperation();
		}).peek().status());
		now.addAndGet(599_999L);
		assertEquals(SKIPPED_COOLDOWN, coordinator.auto("source", () -> {
			starts.incrementAndGet();
			return completedOperation();
		}).peek().status());
		now.incrementAndGet();
		assertEquals(SUCCESS, coordinator.auto("source", () -> {
			starts.incrementAndGet();
			return completedOperation();
		}).peek().status());
		assertEquals(2, starts.get());
	}

	@Test
	public void distinctKeysRefreshIndependently() {
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(600_000L, () -> 1_000L);
		Promise<Void> first = new Promise<>();
		Promise<Void> second = new Promise<>();

		FutureSupplier<?> one = coordinator.auto("one", () -> first);
		FutureSupplier<?> two = coordinator.auto("two", () -> second);

		assertFalse(one.isDone());
		assertFalse(two.isDone());
		first.complete(null);
		assertTrue(one.isDone());
		assertFalse(two.isDone());
	}

	@Test
	public void observerCancellationDoesNotCancelSharedOperation() {
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(0L);
		Promise<Void> operation = new Promise<>();
		FutureSupplier<RefreshCoordinator.Result<String>> first =
				coordinator.manual("source", () -> operation);
		FutureSupplier<RefreshCoordinator.Result<String>> second =
				coordinator.manual("source", Promise::new);

		assertTrue(first.cancel());
		assertFalse(operation.isCancelled());
		operation.complete(null);
		assertEquals(SUCCESS, second.peek().status());
	}

	@Test
	public void replaceCancelsOldWorkAndStartsEditedSource() {
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(0L);
		Promise<Void> old = new Promise<>();
		Promise<Void> replacement = new Promise<>();
		FutureSupplier<RefreshCoordinator.Result<String>> oldResult =
				coordinator.manual("source", () -> old);
		FutureSupplier<RefreshCoordinator.Result<String>> newResult =
				coordinator.replace("source", () -> replacement);

		assertTrue(old.isCancelled());
		assertEquals(CANCELLED, oldResult.peek().status());
		assertFalse(newResult.isDone());
		replacement.complete(null);
		assertEquals(SUCCESS, newResult.peek().status());
	}

	@Test
	public void lateSupersededFailureCannotRestoreBackoffAfterReplacementSuccess() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(0L, now::get);
		UncancellablePromise<Void> staleTask = new UncancellablePromise<>();
		FutureSupplier<RefreshCoordinator.Result<String>> stale =
				coordinator.auto("source", () -> staleTask);

		assertEquals(SUCCESS,
				coordinator.replace("source", RefreshCoordinatorTest::completedOperation)
						.peek().status());
		assertEquals(CANCELLED, stale.peek().status());

		// Simulate a provider that ignores cancellation and reports its old failure after the
		// replacement has already succeeded and reset the key's failure state.
		staleTask.completeExceptionally(new UnknownHostException("stale-offline.example"));

		FutureSupplier<RefreshCoordinator.Result<String>> next =
				coordinator.auto("source", Promise::new);
		assertFalse(next.isDone());
	}

	@Test
	public void stopCancelsOnlyOwnedWorkAndRejectsNewRequestsUntilRestart() {
		RefreshCoordinator<String> firstCoordinator = new RefreshCoordinator<>(0L);
		RefreshCoordinator<String> secondCoordinator = new RefreshCoordinator<>(0L);
		Promise<Void> firstTask = new Promise<>();
		Promise<Void> secondTask = new Promise<>();
		FutureSupplier<RefreshCoordinator.Result<String>> first =
				firstCoordinator.manual("source", () -> firstTask);
		FutureSupplier<RefreshCoordinator.Result<String>> second =
				secondCoordinator.manual("source", () -> secondTask);

		firstCoordinator.stop();
		assertTrue(firstTask.isCancelled());
		assertEquals(CANCELLED, first.peek().status());
		assertFalse(secondTask.isCancelled());
		assertEquals(INACTIVE,
				firstCoordinator.manual("source", Promise::new).peek().status());

		firstCoordinator.start();
		assertFalse(firstCoordinator.manual("source", Promise::new).isDone());
		secondTask.complete(null);
		assertEquals(SUCCESS, second.peek().status());
	}

	@Test
	public void networkFailureIsNormalizedButOriginalErrorIsRetained() {
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(0L);
		UnknownHostException failure = new UnknownHostException("panel.example");
		Promise<Void> task = new Promise<>();
		task.completeExceptionally(failure);

		RefreshCoordinator.Result<String> result =
				coordinator.manual("source", () -> task).peek();

		assertEquals(FAILED, result.status());
		assertEquals(NETWORK, result.failureKind());
		assertSame(failure, result.error());
	}

	@Test
	public void rootResetCancelsOldWorkAndClearsSuccessfulCooldown() {
		AtomicLong now = new AtomicLong(1_000L);
		RefreshCoordinator<String> coordinator = new RefreshCoordinator<>(600_000L, now::get);
		Promise<Void> oldRootTask = new Promise<>();
		FutureSupplier<RefreshCoordinator.Result<String>> oldRoot =
				coordinator.auto("source", () -> oldRootTask);

		coordinator.reset();
		assertTrue(oldRootTask.isCancelled());
		assertEquals(CANCELLED, oldRoot.peek().status());

		Promise<Void> newRootTask = new Promise<>();
		FutureSupplier<RefreshCoordinator.Result<String>> newRoot =
				coordinator.auto("source", () -> newRootTask);
		assertFalse(newRoot.isDone());
		newRootTask.complete(null);
		assertEquals(SUCCESS, newRoot.peek().status());

		coordinator.reset();
		assertFalse(coordinator.auto("source", Promise::new).isDone());
	}

	private static RefreshCoordinator<String> coordinator(AtomicLong now) {
		return new RefreshCoordinator<>(0L, now::get, (failures, kind) -> {
			long delay = BACKOFF_BASE_MILLIS;
			for (int i = 1; (i < failures) && (delay < BACKOFF_MAX_MILLIS); i++)
				delay = Math.min(delay * 2L, BACKOFF_MAX_MILLIS);
			return delay;
		});
	}

	private static FutureSupplier<?> failedNetworkOperation() {
		Promise<Void> failed = new Promise<>();
		failed.completeExceptionally(new UnknownHostException("offline.example"));
		return failed;
	}

	private static FutureSupplier<?> failedProviderOperation() {
		Promise<Void> failed = new Promise<>();
		failed.completeExceptionally(new IllegalStateException("malformed provider response"));
		return failed;
	}

	private static FutureSupplier<?> completedOperation() {
		Promise<Void> completed = new Promise<>();
		completed.complete(null);
		return completed;
	}

	private static void assertBackoffBoundary(RefreshCoordinator<String> coordinator,
			AtomicLong now, long delayMillis) {
		now.addAndGet(delayMillis - 1L);
		assertEquals(SKIPPED_BACKOFF,
				coordinator.auto("source", Promise::new).peek().status());
		now.incrementAndGet();
	}

	private static final class UncancellablePromise<T> extends Promise<T> {
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}
	}
}
