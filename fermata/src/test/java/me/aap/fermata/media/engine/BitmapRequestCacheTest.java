package me.aap.fermata.media.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.utils.async.Promise;

public class BitmapRequestCacheTest {
	@Test
	public void negativeEntryExpiresAndCanBeClearedAfterSuccess() {
		ExpiringFailureCache cache = new ExpiringFailureCache(100L);
		cache.record("image", 1_000L);

		assertTrue(cache.contains("image", 1_099L));
		assertFalse(cache.contains("image", 1_100L));

		cache.record("image", 2_000L);
		cache.remove("image");
		assertFalse(cache.contains("image", 2_001L));
	}

	@Test
	public void concurrentLoadsShareOneRequestAndCompletionAllowsRetry() {
		InFlightRequestCache<String> cache = new InFlightRequestCache<>();
		AtomicInteger loads = new AtomicInteger();
		Promise<String> first = new Promise<>();

		var one = cache.getOrLoad("image", () -> {
			loads.incrementAndGet();
			return first;
		});
		var two = cache.getOrLoad("image", () -> {
			loads.incrementAndGet();
			return new Promise<>();
		});

		assertSame(one, two);
		assertTrue(loads.get() == 1);
		first.complete("done");

		Promise<String> next = new Promise<>();
		var three = cache.getOrLoad("image", () -> {
			loads.incrementAndGet();
			return next;
		});
		assertSame(next, three);
		assertTrue(loads.get() == 2);
	}
}
