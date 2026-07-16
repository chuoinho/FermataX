package me.aap.fermata.media.lib;

import static me.aap.fermata.media.lib.RefreshCoordinator.FailureKind.NETWORK;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.CANCELLED;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.FAILED;
import static me.aap.fermata.media.lib.RefreshCoordinator.Status.INACTIVE;
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
}
