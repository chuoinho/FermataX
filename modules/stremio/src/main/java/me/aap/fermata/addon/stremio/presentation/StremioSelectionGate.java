package me.aap.fermata.addon.stremio.presentation;

import java.util.Objects;

/** Coalesces one accidental repeated selection while preserving immediate different choices. */
public final class StremioSelectionGate {
	private final long debounceMs;
	private String lastKey;
	private long lastAcceptedAt = Long.MIN_VALUE;

	public StremioSelectionGate(long debounceMs) {
		if (debounceMs < 0L) throw new IllegalArgumentException("debounceMs must not be negative");
		this.debounceMs = debounceMs;
	}

	public synchronized boolean accept(String key, long nowMs) {
		Objects.requireNonNull(key, "key");
		if (key.equals(lastKey) && (nowMs >= lastAcceptedAt) &&
				((nowMs - lastAcceptedAt) < debounceMs)) return false;
		lastKey = key;
		lastAcceptedAt = nowMs;
		return true;
	}

	public synchronized void release(String key) {
		if (!Objects.equals(lastKey, key)) return;
		lastKey = null;
		lastAcceptedAt = Long.MIN_VALUE;
	}
}
