package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

public class LogFailureLimiterTest {

	@Test
	public void limitsEachCallSiteAndReportsDropped() {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("A.f:1", 0));
		assertFalse(limiter.tryAcquire("A.f:1", 0));
		assertTrue(limiter.tryAcquire("B.g:2", 0));
		assertEquals(Map.of("A.f:1", 1), limiter.drainDropped());
		assertEquals(Collections.emptyMap(), limiter.drainDropped());
	}

	@Test
	public void limitsTotalAcrossCallSites() {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 50; i++) assertTrue(limiter.tryAcquire("S.m:" + i, 0));
		assertFalse(limiter.tryAcquire("S.m:50", 0));
		assertEquals(Map.of("S.m:50", 1), limiter.drainDropped());
	}

	@Test
	public void nextWindowStartsFresh() {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 6; i++) limiter.tryAcquire("A.f:1", 0);
		assertFalse(limiter.tryAcquire("A.f:1", 9_999));
		assertTrue(limiter.tryAcquire("A.f:1", 10_000));
	}
}
