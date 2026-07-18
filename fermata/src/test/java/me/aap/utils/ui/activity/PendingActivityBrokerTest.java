package me.aap.utils.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PendingActivityBrokerTest {
	@Test
	public void concurrentSubsystemRequestsShareCompletionWithoutCancellation() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();
		PendingActivityBroker.Request<String> addon = broker.acquire();
		PendingActivityBroker.Request<String> vfs = broker.acquire();

		assertTrue(addon.first());
		assertFalse(vfs.first());
		assertFalse(addon.future().isDone());
		assertFalse(vfs.future().isDone());

		long generation = broker.beginActivity();
		broker.complete(generation, "activity", null);

		assertTrue(addon.future().isDoneNotFailed());
		assertTrue(vfs.future().isDoneNotFailed());
		assertEquals("activity", addon.future().peek());
		assertEquals("activity", vfs.future().peek());
	}

	@Test
	public void cancellingOneWaiterDoesNotCancelOthers() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();
		PendingActivityBroker.Request<String> first = broker.acquire();
		PendingActivityBroker.Request<String> second = broker.acquire();
		first.future().cancel();

		long generation = broker.beginActivity();
		broker.complete(generation, "activity", null);

		assertTrue(first.future().isCancelled());
		assertTrue(second.future().isDoneNotFailed());
	}

	@Test
	public void nextRequestBecomesFirstAfterAllPreviousWaitersCancel() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();
		PendingActivityBroker.Request<String> cancelled = broker.acquire();
		cancelled.future().cancel();

		PendingActivityBroker.Request<String> replacement = broker.acquire();

		assertTrue(replacement.first());
		assertFalse(replacement.future().isDone());
	}

	@Test
	public void staleActivityCannotCompleteReplacementWaiters() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();
		PendingActivityBroker.Request<String> waiter = broker.acquire();
		long stale = broker.beginActivity();
		long current = broker.beginActivity();

		broker.complete(stale, "stale", null);
		assertFalse(waiter.future().isDone());

		broker.complete(current, "current", null);
		assertEquals("current", waiter.future().peek());
	}

	@Test
	public void destroyedActivityCompletesEveryWaiterWithFailure() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();
		PendingActivityBroker.Request<String> first = broker.acquire();
		PendingActivityBroker.Request<String> second = broker.acquire();
		long generation = broker.beginActivity();

		broker.cancel(generation, new IllegalStateException("destroyed"));

		assertTrue(first.future().isFailed());
		assertTrue(second.future().isFailed());
		assertTrue(broker.acquire().first());
	}

	@Test
	public void requestDuringInitializationDoesNotLaunchDuplicateActivity() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();
		long generation = broker.beginActivity();
		PendingActivityBroker.Request<String> waiter = broker.acquire();

		assertFalse(waiter.first());
		broker.complete(generation, "activity", null);
		assertEquals("activity", waiter.future().peek());
	}

	@Test
	public void activityWithoutGenerationCannotClaimOrCancelWaiters() {
		PendingActivityBroker<String> broker = new PendingActivityBroker<>();

		broker.cancel(0, new IllegalStateException("no delegate"));
		PendingActivityBroker.Request<String> waiter = broker.acquire();

		assertTrue(waiter.first());
		assertFalse(waiter.future().isDone());
	}
}
