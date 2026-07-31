package me.aap.fermata.diagnostics;

import java.util.ArrayDeque;
import java.util.Iterator;

/** Bounded FIFO queue that sacrifices the oldest lower-priority event under pressure. */
final class BoundedEventQueue {
	private final int capacity;
	private final long maxBytes;
	private final ArrayDeque<DiagnosticEvent> events;
	private long queuedBytes;

	BoundedEventQueue(int capacity, long maxBytes) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
		if (maxBytes <= 0L) throw new IllegalArgumentException("maxBytes must be positive");
		this.capacity = capacity;
		this.maxBytes = maxBytes;
		events = new ArrayDeque<>(capacity);
	}

	synchronized OfferResult offer(DiagnosticEvent event) {
		long eventBytes = event.estimatedBytes();
		if (eventBytes > maxBytes) return OfferResult.rejected(event);
		if ((events.size() < capacity) && ((queuedBytes + eventBytes) <= maxBytes)) {
			events.addLast(event);
			queuedBytes += eventBytes;
			notifyAll();
			return OfferResult.accepted(null);
		}

		DiagnosticEvent firstDropped = null;
		while ((events.size() >= capacity) || ((queuedBytes + eventBytes) > maxBytes)) {
			DiagnosticEvent lowest = null;
			for (DiagnosticEvent queued : events) {
				if ((lowest == null) ||
						(queued.getPriority().weight() < lowest.getPriority().weight())) {
					lowest = queued;
				}
			}
			if ((lowest == null) ||
					(event.getPriority().weight() < lowest.getPriority().weight())) {
				return OfferResult.rejected(event);
			}
			if (event.getPriority().weight() == lowest.getPriority().weight()) {
				DiagnosticEvent first = null;
				DiagnosticEvent latest = null;
				for (DiagnosticEvent queued : events) {
					if (queued.getPriority() != lowest.getPriority()) continue;
					if (first == null) first = queued;
					latest = queued;
				}
				if ((latest == null) || (latest == first)) return OfferResult.rejected(event);
				lowest = latest;
			}
			for (Iterator<DiagnosticEvent> iterator = events.iterator(); iterator.hasNext(); ) {
				if (iterator.next() == lowest) {
					iterator.remove();
					queuedBytes -= lowest.estimatedBytes();
					if (firstDropped == null) firstDropped = lowest;
					break;
				}
			}
		}
		events.addLast(event);
		queuedBytes += eventBytes;
		notifyAll();
		return OfferResult.accepted(firstDropped);
	}

	synchronized DiagnosticEvent poll(long timeoutMillis) throws InterruptedException {
		if (events.isEmpty() && (timeoutMillis > 0L)) wait(timeoutMillis);
		return removeFirst();
	}

	synchronized DiagnosticEvent poll() {
		return removeFirst();
	}

	synchronized DiagnosticEvent peek() {
		return events.peekFirst();
	}

	synchronized boolean isEmpty() {
		return events.isEmpty();
	}

	synchronized int size() {
		return events.size();
	}

	synchronized long bytes() {
		return queuedBytes;
	}

	synchronized void wake() {
		notifyAll();
	}

	private DiagnosticEvent removeFirst() {
		DiagnosticEvent event = events.pollFirst();
		if (event != null) queuedBytes -= event.estimatedBytes();
		return event;
	}

	static final class OfferResult {
		private final boolean accepted;
		private final DiagnosticEvent dropped;

		private OfferResult(boolean accepted, DiagnosticEvent dropped) {
			this.accepted = accepted;
			this.dropped = dropped;
		}

		static OfferResult accepted(DiagnosticEvent dropped) {
			return new OfferResult(true, dropped);
		}

		static OfferResult rejected(DiagnosticEvent dropped) {
			return new OfferResult(false, dropped);
		}

		boolean isAccepted() {
			return accepted;
		}

		DiagnosticEvent getDropped() {
			return dropped;
		}
	}
}
