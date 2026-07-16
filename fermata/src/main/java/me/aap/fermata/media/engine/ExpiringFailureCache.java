package me.aap.fermata.media.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ExpiringFailureCache {
	private final long ttl;
	private final Map<String, Long> failures = new ConcurrentHashMap<>();

	ExpiringFailureCache(long ttl) {
		this.ttl = ttl;
	}

	void record(String key, long now) {
		failures.put(key, now + ttl);
	}

	boolean contains(String key, long now) {
		Long expiresAt = failures.get(key);
		if (expiresAt == null) return false;
		if (expiresAt > now) return true;
		failures.remove(key, expiresAt);
		return false;
	}

	void remove(String key) {
		failures.remove(key);
	}
}
