package me.app.fermatax.auto.carapps;

import android.view.MotionEvent;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-safe bounding touch queue with MOVE coalescing, stale drop, and overload reset.
 */
public final class TouchQueue {

	public static final class Counters {
		public final int merged;
		public final int dropped;
		public final int resetOverload;

		public Counters(int merged, int dropped, int resetOverload) {
			this.merged = merged;
			this.dropped = dropped;
			this.resetOverload = resetOverload;
		}
	}

	private static final class Entry {
		final MotionEvent event;
		final long enqueuedNanos;

		Entry(MotionEvent event, long enqueuedNanos) {
			this.event = event;
			this.enqueuedNanos = enqueuedNanos;
		}
	}

	private final int capacity;
	private final long staleNanos;
	private final Deque<Entry> queue = new ArrayDeque<>();

	private int mergedCount = 0;
	private int droppedCount = 0;
	private int resetOverloadCount = 0;

	private boolean gestureOpen = false;
	private boolean discardingCurrentGesture = false;
	private MotionEvent lastEventForCancel = null;

	public TouchQueue(int capacity, long staleNanos) {
		this.capacity = capacity;
		this.staleNanos = staleNanos;
	}

	public synchronized Counters counters() {
		return new Counters(mergedCount, droppedCount, resetOverloadCount);
	}

	public synchronized boolean offer(MotionEvent e, long nowNanos) {
		if (e == null) return false;

		int action = e.getActionMasked();

		// Track open gesture by received events
		if (action == MotionEvent.ACTION_DOWN) {
			gestureOpen = true;
			discardingCurrentGesture = false;
		}

		if (discardingCurrentGesture) {
			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				gestureOpen = false;
				discardingCurrentGesture = false;
				if (lastEventForCancel != null) {
					lastEventForCancel.recycle();
					lastEventForCancel = null;
				}
			}
			return false;
		}

		// Keep a copy of the latest event in the active gesture for potential CANCEL synthesis on overflow
		if (gestureOpen) {
			if (lastEventForCancel != null) {
				lastEventForCancel.recycle();
			}
			lastEventForCancel = MotionEvent.obtain(e);
		}

		// Coalescing: only merge consecutive MOVEs into the last MOVE in queue
		if (action == MotionEvent.ACTION_MOVE && !queue.isEmpty()) {
			Entry lastEntry = queue.peekLast();
			if (lastEntry.event.getActionMasked() == MotionEvent.ACTION_MOVE && canMerge(lastEntry.event, e)) {
				queue.removeLast();
				lastEntry.event.recycle();

				MotionEvent copy = MotionEvent.obtain(e);
				queue.addLast(new Entry(copy, nowNanos));
				mergedCount++;
				return true;
			}
		}

		// Overflow / overload handling
		if (queue.size() >= capacity) {
			resetOverloadCount++;

			// Clear the queue, recycling all queued events
			while (!queue.isEmpty()) {
				queue.pollFirst().event.recycle();
			}

			// If gesture is open, synthesize an ACTION_CANCEL from the last received event
			if (gestureOpen && lastEventForCancel != null) {
				MotionEvent cancel = MotionEvent.obtain(lastEventForCancel);
				cancel.setAction(MotionEvent.ACTION_CANCEL);
				queue.addLast(new Entry(cancel, nowNanos));
				discardingCurrentGesture = true;
			}

			return false;
		}

		// Normal enqueue
		MotionEvent copy = MotionEvent.obtain(e);
		queue.addLast(new Entry(copy, nowNanos));

		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			gestureOpen = false;
			if (lastEventForCancel != null) {
				lastEventForCancel.recycle();
				lastEventForCancel = null;
			}
		}

		return true;
	}

	private static boolean canMerge(MotionEvent a, MotionEvent b) {
		if (a.getDownTime() != b.getDownTime()) return false;
		int count = a.getPointerCount();
		if (count != b.getPointerCount()) return false;
		for (int i = 0; i < count; i++) {
			if (a.getPointerId(i) != b.getPointerId(i)) return false;
		}
		return true;
	}

	@Nullable
	public synchronized MotionEvent take(long nowNanos) {
		while (!queue.isEmpty()) {
			Entry head = queue.peekFirst();
			// Only MOVE exceeding staleNanos from offer time is dropped
			if (head.event.getActionMasked() == MotionEvent.ACTION_MOVE && (nowNanos - head.enqueuedNanos > staleNanos)) {
				queue.pollFirst();
				head.event.recycle();
				droppedCount++;
				continue;
			}
			queue.pollFirst();
			return head.event; // caller takes ownership and must recycle
		}
		return null;
	}

	public synchronized boolean isEmpty() {
		return queue.isEmpty();
	}

	public synchronized void clear() {
		while (!queue.isEmpty()) {
			queue.pollFirst().event.recycle();
		}
		if (lastEventForCancel != null) {
			lastEventForCancel.recycle();
			lastEventForCancel = null;
		}
		gestureOpen = false;
		discardingCurrentGesture = false;
		mergedCount = 0;
		droppedCount = 0;
		resetOverloadCount = 0;
	}
}
