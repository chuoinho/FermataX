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

		broker.complete("activity", null);

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

		broker.complete("activity", null);

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
}
