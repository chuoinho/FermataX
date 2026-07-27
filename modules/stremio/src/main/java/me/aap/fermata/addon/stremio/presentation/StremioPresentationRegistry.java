package me.aap.fermata.addon.stremio.presentation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Thread-safe, bounded registry for transient presentation targets. */
public final class StremioPresentationRegistry<T> {
	private final Map<String, T> values;

	public StremioPresentationRegistry(int maxEntries) {
		if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
		values = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
				return size() > maxEntries;
			}
		};
	}

	public synchronized void put(String key, T value) {
		values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
	}

	public synchronized void putIfAbsent(String key, T value) {
		values.putIfAbsent(Objects.requireNonNull(key, "key"),
				Objects.requireNonNull(value, "value"));
	}

	public synchronized T get(String key) {
		return (key == null) ? null : values.get(key);
	}

	public synchronized T remove(String key) {
		return (key == null) ? null : values.remove(key);
	}

	public synchronized void clear() {
		values.clear();
	}
}
