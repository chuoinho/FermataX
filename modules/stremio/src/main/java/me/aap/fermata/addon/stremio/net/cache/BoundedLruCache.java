package me.aap.fermata.addon.stremio.net.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BoundedLruCache {
	private final int maxEntries;
	private final long maxBytes;
	private final long maxEntryBytes;
	private final LinkedHashMap<CacheKey, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
	private long currentBytes;

	public BoundedLruCache(int maxEntries, long maxBytes) {
		this(maxEntries, maxBytes, maxBytes);
	}

	public BoundedLruCache(int maxEntries, long maxBytes, long maxEntryBytes) {
		if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
		if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
		if ((maxEntryBytes <= 0) || (maxEntryBytes > maxBytes)) {
			throw new IllegalArgumentException("maxEntryBytes must be within the cache budget");
		}
		this.maxEntries = maxEntries;
		this.maxBytes = maxBytes;
		this.maxEntryBytes = maxEntryBytes;
	}

	public synchronized CacheLookup lookup(CacheKey key, CachePolicy policy, long nowMillis) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(policy, "policy");
		CacheEntry entry = entries.get(key);
		if (entry == null) return CacheLookup.miss();
		long age = Math.max(0, nowMillis - entry.validatedAtMillis());
		long freshEnd = policy.freshFor().toMillis();
		long staleEnd = saturatedAdd(freshEnd, policy.staleFor().toMillis());
		CacheState state = (age <= freshEnd) ? CacheState.FRESH :
				((age <= staleEnd) ? CacheState.STALE : CacheState.EXPIRED);
		return new CacheLookup(state, entry);
	}

	public synchronized boolean put(CacheKey key, CacheEntry entry) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(entry, "entry");
		if (entry.sizeBytes() > maxEntryBytes) {
			remove(key);
			return false;
		}
		CacheEntry previous = entries.put(key, entry);
		if (previous != null) currentBytes -= previous.sizeBytes();
		currentBytes += entry.sizeBytes();
		evictToBounds();
		return entries.containsKey(key);
	}

	public synchronized void remove(CacheKey key) {
		CacheEntry removed = entries.remove(key);
		if (removed != null) currentBytes -= removed.sizeBytes();
	}

	public synchronized int size() {
		return entries.size();
	}

	public synchronized long sizeBytes() {
		return currentBytes;
	}

	private void evictToBounds() {
		var iterator = entries.entrySet().iterator();
		while (((entries.size() > maxEntries) || (currentBytes > maxBytes)) && iterator.hasNext()) {
			Map.Entry<CacheKey, CacheEntry> eldest = iterator.next();
			currentBytes -= eldest.getValue().sizeBytes();
			iterator.remove();
		}
	}

	private static long saturatedAdd(long first, long second) {
		if (Long.MAX_VALUE - first < second) return Long.MAX_VALUE;
		return first + second;
	}
}
