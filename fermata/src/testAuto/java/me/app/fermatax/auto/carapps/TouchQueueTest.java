package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TouchQueueTest {

	private static MotionEvent obtainEvent(long downTime, long eventTime, int action, float x, float y) {
		return MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
	}

	@Test
	public void ownershipOfferObtainsCopySoCallerCanRecycleImmediately() {
		TouchQueue queue = new TouchQueue(64, 250_000_000L);
		long now = 1_000_000L;

		MotionEvent e = obtainEvent(100, 100, MotionEvent.ACTION_DOWN, 50f, 50f);
		assertTrue(queue.offer(e, now));
		e.recycle(); // caller recycles immediately, like forwardTouch does

		MotionEvent taken = queue.take(now);
		assertNotNull(taken);
		assertEquals(MotionEvent.ACTION_DOWN, taken.getActionMasked());
		assertEquals(50f, taken.getX(), 0.01f);
		assertEquals(50f, taken.getY(), 0.01f);
		taken.recycle();

		assertNull(queue.take(now));
	}

	@Test
	public void mergeConsecutiveMovesWithSameDownTimeAndPointers() {
		TouchQueue queue = new TouchQueue(64, 250_000_000L);
		long downTime = 100L;

		MotionEvent down = obtainEvent(downTime, downTime, MotionEvent.ACTION_DOWN, 10f, 10f);
		queue.offer(down, 1_000_000L);
		down.recycle();

		MotionEvent move1 = obtainEvent(downTime, 105L, MotionEvent.ACTION_MOVE, 20f, 20f);
		queue.offer(move1, 2_000_000L);
		move1.recycle();

		MotionEvent move2 = obtainEvent(downTime, 110L, MotionEvent.ACTION_MOVE, 30f, 30f);
		queue.offer(move2, 3_000_000L);
		move2.recycle();

		assertEquals(1, queue.counters().merged);

		// Take 1: DOWN
		MotionEvent t1 = queue.take(4_000_000L);
		assertNotNull(t1);
		assertEquals(MotionEvent.ACTION_DOWN, t1.getActionMasked());
		assertEquals(10f, t1.getX(), 0.01f);
		t1.recycle();

		// Take 2: merged MOVE (contains latest coordinates: 30, 30)
		MotionEvent t2 = queue.take(4_000_000L);
		assertNotNull(t2);
		assertEquals(MotionEvent.ACTION_MOVE, t2.getActionMasked());
		assertEquals(30f, t2.getX(), 0.01f);
		assertEquals(30f, t2.getY(), 0.01f);
		t2.recycle();

		assertNull(queue.take(4_000_000L));
	}

	@Test
	public void doesNotMergeAcrossNonMoveEvents() {
		TouchQueue queue = new TouchQueue(64, 250_000_000L);
		long downTime = 100L;

		MotionEvent down = obtainEvent(downTime, 100L, MotionEvent.ACTION_DOWN, 10f, 10f);
		queue.offer(down, 1_000_000L);
		down.recycle();

		MotionEvent move1 = obtainEvent(downTime, 105L, MotionEvent.ACTION_MOVE, 20f, 20f);
		queue.offer(move1, 2_000_000L);
		move1.recycle();

		// Simulate non-move event (e.g. up)
		MotionEvent up = obtainEvent(downTime, 110L, MotionEvent.ACTION_UP, 20f, 20f);
		queue.offer(up, 3_000_000L);
		up.recycle();

		MotionEvent move2 = obtainEvent(downTime, 115L, MotionEvent.ACTION_MOVE, 30f, 30f);
		queue.offer(move2, 4_000_000L);
		move2.recycle();

		assertEquals(0, queue.counters().merged);

		MotionEvent t1 = queue.take(5_000_000L);
		assertEquals(MotionEvent.ACTION_DOWN, t1.getActionMasked());
		t1.recycle();

		MotionEvent t2 = queue.take(5_000_000L);
		assertEquals(MotionEvent.ACTION_MOVE, t2.getActionMasked());
		t2.recycle();

		MotionEvent t3 = queue.take(5_000_000L);
		assertEquals(MotionEvent.ACTION_UP, t3.getActionMasked());
		t3.recycle();

		MotionEvent t4 = queue.take(5_000_000L);
		assertEquals(MotionEvent.ACTION_MOVE, t4.getActionMasked());
		t4.recycle();
	}

	@Test
	public void neverDropNonMoveEventsEvenIfStale() {
		long staleNanos = 250_000_000L;
		TouchQueue queue = new TouchQueue(64, staleNanos);

		MotionEvent down = obtainEvent(100, 100, MotionEvent.ACTION_DOWN, 10f, 10f);
		queue.offer(down, 1_000_000L);
		down.recycle();

		MotionEvent up = obtainEvent(100, 200, MotionEvent.ACTION_UP, 10f, 10f);
		queue.offer(up, 2_000_000L);
		up.recycle();

		// Time advances far into the future (10 seconds)
		long now = 10_000_000_000L;

		MotionEvent t1 = queue.take(now);
		assertNotNull(t1);
		assertEquals(MotionEvent.ACTION_DOWN, t1.getActionMasked());
		t1.recycle();

		MotionEvent t2 = queue.take(now);
		assertNotNull(t2);
		assertEquals(MotionEvent.ACTION_UP, t2.getActionMasked());
		t2.recycle();

		assertEquals(0, queue.counters().dropped);
	}

	@Test
	public void staleMoveDroppedAtTake() {
		long staleNanos = 250_000_000L;
		TouchQueue queue = new TouchQueue(64, staleNanos);

		MotionEvent down = obtainEvent(100, 100, MotionEvent.ACTION_DOWN, 10f, 10f);
		queue.offer(down, 1_000_000L);
		down.recycle();

		MotionEvent move = obtainEvent(100, 105, MotionEvent.ACTION_MOVE, 20f, 20f);
		queue.offer(move, 2_000_000L);
		move.recycle();

		// Take DOWN immediately
		MotionEvent t1 = queue.take(2_000_000L);
		assertEquals(MotionEvent.ACTION_DOWN, t1.getActionMasked());
		t1.recycle();

		// Now advance time beyond stale threshold (2_000_000 + 250_000_000 + 1)
		long now = 252_000_001L;

		MotionEvent t2 = queue.take(now);
		assertNull(t2); // stale MOVE was dropped
		assertEquals(1, queue.counters().dropped);
	}

	@Test
	public void overflowOverloadResetAndSynthesizeCancelForOpenGesture() {
		int capacity = 3;
		TouchQueue queue = new TouchQueue(capacity, 250_000_000L);
		long downTime = 100L;

		MotionEvent down = obtainEvent(downTime, 100L, MotionEvent.ACTION_DOWN, 10f, 10f);
		queue.offer(down, 1_000_000L);
		down.recycle();

		MotionEvent move1 = obtainEvent(downTime, 105L, MotionEvent.ACTION_MOVE, 20f, 20f);
		queue.offer(move1, 2_000_000L);
		move1.recycle();

		// Notice: to prevent merging in this test, change pointer or use distinct events
		// Here we fill up to capacity by offering another event type or using distinct downTime/non-consecutive
		MotionEvent cancelPrev = obtainEvent(downTime, 106L, MotionEvent.ACTION_POINTER_DOWN, 20f, 20f);
		queue.offer(cancelPrev, 3_000_000L);
		cancelPrev.recycle();

		// Queue size is now 3 (capacity reached). Offering another event should trigger overflow!
		MotionEvent moveOverflow = obtainEvent(downTime, 110L, MotionEvent.ACTION_MOVE, 30f, 30f);
		boolean accepted = queue.offer(moveOverflow, 4_000_000L);
		moveOverflow.recycle();
		assertFalse(accepted);

		assertEquals(1, queue.counters().resetOverload);

		// Gesture was open, so queue should now contain exactly one synthesized ACTION_CANCEL
		MotionEvent tCancel = queue.take(5_000_000L);
		assertNotNull(tCancel);
		assertEquals(MotionEvent.ACTION_CANCEL, tCancel.getActionMasked());
		tCancel.recycle();

		// Further events of this gesture (including UP) must be discarded
		MotionEvent moveAfter = obtainEvent(downTime, 115L, MotionEvent.ACTION_MOVE, 35f, 35f);
		assertFalse(queue.offer(moveAfter, 6_000_000L));
		moveAfter.recycle();

		MotionEvent upAfter = obtainEvent(downTime, 120L, MotionEvent.ACTION_UP, 35f, 35f);
		assertFalse(queue.offer(upAfter, 7_000_000L));
		upAfter.recycle();

		assertNull(queue.take(8_000_000L));

		// Next gesture starting with ACTION_DOWN must be accepted normally
		MotionEvent nextDown = obtainEvent(200L, 200L, MotionEvent.ACTION_DOWN, 50f, 50f);
		assertTrue(queue.offer(nextDown, 9_000_000L));
		nextDown.recycle();

		MotionEvent tNext = queue.take(10_000_000L);
		assertNotNull(tNext);
		assertEquals(MotionEvent.ACTION_DOWN, tNext.getActionMasked());
		tNext.recycle();
	}

	private static MotionEvent twoPointerMove(long downTime, int secondPointerId) {
		MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
		MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
		for (int i = 0; i < 2; i++) {
			props[i] = new MotionEvent.PointerProperties();
			props[i].id = (i == 0) ? 0 : secondPointerId;
			coords[i] = new MotionEvent.PointerCoords();
			coords[i].x = 10f * i;
			coords[i].y = 10f * i;
		}
		return MotionEvent.obtain(downTime, downTime + 5, MotionEvent.ACTION_MOVE, 2, props, coords,
				0, 0, 1f, 1f, 0, 0, 0, 0);
	}

	private static int mergedAfterTwoMoves(MotionEvent first, MotionEvent second) {
		TouchQueue queue = new TouchQueue(64, 250_000_000L);
		queue.offer(first, 1L);
		queue.offer(second, 2L);
		first.recycle();
		second.recycle();
		return queue.counters().merged;
	}

	@Test
	public void movesWithDifferentPointersAreNotMerged() {
		assertEquals(0, mergedAfterTwoMoves(obtainEvent(100, 105, MotionEvent.ACTION_MOVE, 1f, 1f), twoPointerMove(100, 1)));
		assertEquals(0, mergedAfterTwoMoves(twoPointerMove(100, 1), twoPointerMove(100, 2)));
		assertEquals(1, mergedAfterTwoMoves(twoPointerMove(100, 1), twoPointerMove(100, 1)));
	}

	@Test
	public void movesOfDifferentGesturesAreNotMerged() {
		assertEquals(0, mergedAfterTwoMoves(obtainEvent(100, 105, MotionEvent.ACTION_MOVE, 1f, 1f),
				obtainEvent(200, 205, MotionEvent.ACTION_MOVE, 2f, 2f)));
	}

	@Test
	public void overflowOnTheLiftStillCancelsAndTheNextGestureWorks() {
		TouchQueue queue = new TouchQueue(1, 250_000_000L);
		MotionEvent down = obtainEvent(100, 100, MotionEvent.ACTION_DOWN, 1f, 1f);
		queue.offer(down, 1L);
		down.recycle();
		MotionEvent up = obtainEvent(100, 110, MotionEvent.ACTION_UP, 1f, 1f);
		assertFalse(queue.offer(up, 2L));
		up.recycle();

		MotionEvent cancel = queue.take(3L);
		assertEquals(MotionEvent.ACTION_CANCEL, cancel.getActionMasked());
		cancel.recycle();

		MotionEvent next = obtainEvent(200, 200, MotionEvent.ACTION_DOWN, 2f, 2f);
		assertTrue(queue.offer(next, 4L));
		next.recycle();
		MotionEvent taken = queue.take(5L);
		assertEquals(MotionEvent.ACTION_DOWN, taken.getActionMasked());
		taken.recycle();
	}

	@Test
	public void takeWhenEmptyReturnsNull() {
		TouchQueue queue = new TouchQueue(64, 250_000_000L);
		assertNull(queue.take(System.nanoTime()));
	}

	@Test
	public void clearRecyclesAllEventsAndResetsState() {
		TouchQueue queue = new TouchQueue(64, 250_000_000L);
		MotionEvent down = obtainEvent(100, 100, MotionEvent.ACTION_DOWN, 10f, 10f);
		queue.offer(down, 1_000_000L);
		down.recycle();

		queue.clear();
		assertNull(queue.take(2_000_000L));
		assertEquals(0, queue.counters().merged);
		assertEquals(0, queue.counters().dropped);
		assertEquals(0, queue.counters().resetOverload);
	}
}
