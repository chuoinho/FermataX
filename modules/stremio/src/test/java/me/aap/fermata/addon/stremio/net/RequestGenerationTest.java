package me.aap.fermata.addon.stremio.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import me.aap.fermata.addon.stremio.net.http.HttpDeadlines;

public class RequestGenerationTest {
	@Test
	public void newerRequestInvalidatesOlderToken() {
		var generation = new RequestGeneration();
		var first = generation.begin();
		var second = generation.begin();

		assertFalse(first.isCurrent());
		assertTrue(second.isCurrent());
		assertTrue(second.value() > first.value());
		assertThrows(CancellationException.class, first::throwIfStale);
	}

	@Test
	public void cancelAndCloseInvalidateTokens() {
		var generation = new RequestGeneration();
		var token = generation.begin();
		generation.cancelAll();
		assertFalse(token.isCurrent());

		var next = generation.begin();
		generation.close();
		assertFalse(next.isCurrent());
		assertThrows(IllegalStateException.class, generation::begin);
	}

	@Test
	public void invalidationObserversRunExactlyOnceAndCanDetach() throws Exception {
		var generation = new RequestGeneration();
		var token = generation.begin();
		var invoked = new AtomicInteger();
		AutoCloseable kept = token.onInvalidated(invoked::incrementAndGet);
		AutoCloseable detached = token.onInvalidated(invoked::incrementAndGet);
		detached.close();

		generation.begin();
		generation.cancelAll();
		kept.close();

		assertEquals(1, invoked.get());
	}

	@Test
	public void staleTokenObserverIsNotLostDuringRegistration() throws Exception {
		var generation = new RequestGeneration();
		var stale = generation.begin();
		generation.begin();
		var invoked = new AtomicInteger();

		AutoCloseable observation = stale.onInvalidated(invoked::incrementAndGet);
		observation.close();

		assertEquals(1, invoked.get());
	}

	@Test
	public void exposesFiniteBodyRedirectAndTimeLimits() {
		assertEquals(5, NetworkLimits.MAX_REDIRECTS);
		assertEquals(8, NetworkLimits.MAX_GLOBAL_JSON_CONCURRENCY);
		assertEquals(4, NetworkLimits.MAX_PER_HOST_JSON_CONCURRENCY);
		assertEquals(512L * 1024L, NetworkLimits.MAX_MANIFEST_BODY_BYTES);
		assertEquals(4L * 1024L * 1024L, NetworkLimits.MAX_JSON_BODY_BYTES);
		assertEquals(Duration.ofSeconds(5), NetworkLimits.DNS_TIMEOUT);
		assertEquals(Duration.ofSeconds(5), NetworkLimits.CONNECT_TIMEOUT);
		assertEquals(Duration.ofSeconds(8), NetworkLimits.HEADER_TIMEOUT);
		assertEquals(Duration.ofSeconds(12), NetworkLimits.BODY_TIMEOUT);
		assertEquals(Duration.ofSeconds(12), NetworkLimits.CALL_TIMEOUT);
		assertEquals(NetworkLimits.CONNECT_TIMEOUT, HttpDeadlines.DEFAULT.connect());
		assertEquals(NetworkLimits.HEADER_TIMEOUT, HttpDeadlines.DEFAULT.headers());
		assertEquals(NetworkLimits.BODY_TIMEOUT, HttpDeadlines.DEFAULT.body());
		assertEquals(NetworkLimits.CALL_TIMEOUT, HttpDeadlines.DEFAULT.call());
	}
}
