package me.aap.fermata.action;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Suppresses only the same physical event observed through both Android input paths. */
final class HardwareInputDeduplicator {
	private static final long MAX_CROSS_PATH_DELAY_MILLIS = 250L;
	private static final long RETENTION_MILLIS = 1000L;
	private final Map<Integer, HardwareInputEvent> recent = new HashMap<>();

	synchronized boolean isDuplicate(HardwareInputEvent event) {
		prune(event.eventTime());
		int slot = 31 * event.keyCode() + event.action();
		HardwareInputEvent previous = recent.get(slot);
		if ((previous != null) && (previous.origin() != event.origin()) &&
				(previous.keyCode() == event.keyCode()) && (previous.action() == event.action()) &&
				(previous.repeatCount() == event.repeatCount()) &&
				(previous.downTime() == event.downTime()) &&
				(Math.abs(previous.eventTime() - event.eventTime()) <= MAX_CROSS_PATH_DELAY_MILLIS)) {
			return true;
		}
		recent.put(slot, event);
		return false;
	}

	synchronized void clear() {
		recent.clear();
	}

	private void prune(long now) {
		for (Iterator<HardwareInputEvent> it = recent.values().iterator(); it.hasNext(); ) {
			HardwareInputEvent event = it.next();
			if ((now >= event.eventTime()) && ((now - event.eventTime()) > RETENTION_MILLIS)) {
				it.remove();
			}
		}
	}
}
