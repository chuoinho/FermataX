package me.aap.fermata.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Thread-safe bounded history used by crash and support-report integrations. */
final class BreadcrumbBuffer {
	private final int capacity;
	private final AtomicReferenceArray<DiagnosticEvent> events;
	private final AtomicLong cursor = new AtomicLong();

	BreadcrumbBuffer(int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
		this.capacity = capacity;
		events = new AtomicReferenceArray<>(capacity);
	}

	void add(DiagnosticEvent event) {
		long index = cursor.getAndIncrement();
		events.set((int) Math.floorMod(index, capacity), event);
	}

	List<String> snapshot() {
		List<DiagnosticEvent> snapshot = new ArrayList<>(capacity);
		for (int i = 0; i < capacity; i++) {
			DiagnosticEvent event = events.get(i);
			if (event != null) snapshot.add(event);
		}
		snapshot.sort(Comparator.comparingLong(DiagnosticEvent::getSequence));
		List<String> result = new ArrayList<>(snapshot.size());
		for (DiagnosticEvent event : snapshot) result.add(event.encodedJson());
		return Collections.unmodifiableList(result);
	}

	void clear() {
		for (int i = 0; i < capacity; i++) events.set(i, null);
		cursor.set(0L);
	}
}
