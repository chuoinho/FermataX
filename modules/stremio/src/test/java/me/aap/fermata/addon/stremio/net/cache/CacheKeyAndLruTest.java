package me.aap.fermata.addon.stremio.net.cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.Test;

public class CacheKeyAndLruTest {
	private static final UUID SOURCE = UUID.fromString("12345678-1234-1234-1234-123456789abc");

	@Test
	public void cacheKeyIsDeterministicAndNeverRetainsSecretUrl() {
		String secret = "https://provider.example.invalid/token-secret/catalog/movie.json";
		CacheKey first = CacheKey.derive(SOURCE, "catalog", secret);
		CacheKey second = CacheKey.derive(SOURCE, "catalog", secret);

		assertEquals(first, second);
		assertFalse(first.toString().contains("provider"));
		assertFalse(first.toString().contains("token-secret"));
		assertEquals(64, first.requestDigest().length());
	}

	@Test
	public void classifiesFreshStaleExpiredAndDefensivelyCopiesBodies() {
		var cache = new BoundedLruCache(2, 10);
		var policy = new CachePolicy(Duration.ofMillis(10), Duration.ofMillis(20));
		CacheKey key = key("a");
		byte[] body = {1, 2, 3};
		cache.put(key, new CacheEntry(body, "etag", "date", 100));
		body[0] = 9;

		assertEquals(CacheState.FRESH, cache.lookup(key, policy, 110).state());
		assertEquals(CacheState.STALE, cache.lookup(key, policy, 111).state());
		assertEquals(CacheState.EXPIRED, cache.lookup(key, policy, 131).state());
		CacheEntry entry = cache.lookup(key, policy, 100).entry();
		byte[] exposed = entry.body();
		exposed[1] = 9;
		assertArrayEquals(new byte[]{1, 2, 3}, entry.body());
		assertSame(entry.payload(), entry.revalidated(200).payload());
	}

	@Test
	public void evictsByAccessOrderEntryCountAndTotalBytes() {
		var cache = new BoundedLruCache(2, 6);
		var policy = new CachePolicy(Duration.ofDays(1), Duration.ZERO);
		CacheKey a = key("a");
		CacheKey b = key("b");
		CacheKey c = key("c");
		cache.put(a, entry(3));
		cache.put(b, entry(3));
		cache.lookup(a, policy, 0);
		cache.put(c, entry(3));

		assertEquals(CacheState.MISS, cache.lookup(b, policy, 0).state());
		assertEquals(CacheState.FRESH, cache.lookup(a, policy, 0).state());
		assertEquals(CacheState.FRESH, cache.lookup(c, policy, 0).state());
		assertEquals(2, cache.size());
		assertEquals(6, cache.sizeBytes());

		assertFalse(cache.put(key("large"), entry(7)));
		assertTrue(cache.sizeBytes() <= 6);
	}

	@Test
	public void rejectsSingleEntryAboveDedicatedEntryBudget() {
		var cache = new BoundedLruCache(8, 32, 8);

		assertFalse(cache.put(key("large-entry"), entry(9)));
		assertEquals(0, cache.sizeBytes());
	}

	private static CacheKey key(String value) {
		return CacheKey.derive(SOURCE, "catalog", value);
	}

	private static CacheEntry entry(int size) {
		return new CacheEntry(new byte[size], null, null, 0);
	}
}
