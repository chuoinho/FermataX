package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BoundedEventQueueTest {
	@Test
	public void errorEvictsOldestLowestPriorityAndKeepsTimelineOrder() {
		BoundedEventQueue queue = new BoundedEventQueue(3, 64 * 1024L);
		DiagnosticEvent detail = event("detail", DiagnosticPriority.DETAIL);
		DiagnosticEvent state = event("state", DiagnosticPriority.STATE);
		DiagnosticEvent warn = event("warn", DiagnosticPriority.WARN);
		DiagnosticEvent error = event("error", DiagnosticPriority.ERROR);
		queue.offer(detail);
		queue.offer(state);
		queue.offer(warn);

		BoundedEventQueue.OfferResult result = queue.offer(error);
		assertTrue(result.isAccepted());
		assertSame(detail, result.getDropped());
		assertSame(state, queue.poll());
		assertSame(warn, queue.poll());
		assertSame(error, queue.poll());
	}

	@Test
	public void detailCannotDisplaceHigherPriorityEvents() {
		BoundedEventQueue queue = new BoundedEventQueue(2, 64 * 1024L);
		queue.offer(event("warn", DiagnosticPriority.WARN));
		queue.offer(event("error", DiagnosticPriority.ERROR));
		DiagnosticEvent detail = event("detail", DiagnosticPriority.DETAIL);

		BoundedEventQueue.OfferResult result = queue.offer(detail);
		assertFalse(result.isAccepted());
		assertSame(detail, result.getDropped());
		assertEquals(2, queue.size());
	}

	@Test
	public void equalPriorityKeepsFirstAndMostRecentEvidence() {
		BoundedEventQueue queue = new BoundedEventQueue(2, 64 * 1024L);
		DiagnosticEvent old = event("old", DiagnosticPriority.ERROR);
		DiagnosticEvent middle = event("middle", DiagnosticPriority.ERROR);
		DiagnosticEvent recent = event("recent", DiagnosticPriority.ERROR);
		queue.offer(old);
		queue.offer(middle);

		BoundedEventQueue.OfferResult result = queue.offer(recent);
		assertTrue(result.isAccepted());
		assertSame(middle, result.getDropped());
		assertSame(old, queue.poll());
		assertSame(recent, queue.poll());
	}

	@Test
	public void byteBudgetEvictsLowerPriorityEvidence() {
		DiagnosticEvent detail = event("detail", DiagnosticPriority.DETAIL);
		DiagnosticEvent error = event("error", DiagnosticPriority.ERROR);
		BoundedEventQueue queue = new BoundedEventQueue(10, error.estimatedBytes() + 8L);
		assertTrue(queue.offer(detail).isAccepted());

		BoundedEventQueue.OfferResult result = queue.offer(error);
		assertTrue(result.isAccepted());
		assertSame(detail, result.getDropped());
		assertSame(error, queue.poll());
		assertEquals(0L, queue.bytes());
	}

	private static DiagnosticEvent event(String name, DiagnosticPriority priority) {
		return DiagnosticEvent.builder("test", name).priority(priority).build();
	}
}
