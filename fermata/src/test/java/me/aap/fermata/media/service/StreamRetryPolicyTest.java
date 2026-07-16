package me.aap.fermata.media.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamRetryPolicyTest {
	@Test
	public void shortFailuresConsumeBoundedRetryBudget() {
		assertEquals(1, StreamRetryPolicy.nextAttempt(0, false, 0));
		assertEquals(2, StreamRetryPolicy.nextAttempt(1, true, 1_000));
		assertEquals(3, StreamRetryPolicy.nextAttempt(2, true, 1_000));
		assertEquals(4, StreamRetryPolicy.nextAttempt(3, true, 1_000));
		assertTrue(StreamRetryPolicy.canRetry(3));
		assertFalse(StreamRetryPolicy.canRetry(4));
	}

	@Test
	public void stablePlaybackAndDifferentStreamResetBudget() {
		assertEquals(1, StreamRetryPolicy.nextAttempt(3, true, 30_000));
		assertEquals(1, StreamRetryPolicy.nextAttempt(3, false, 500));
	}

	@Test
	public void retryBackoffIsOneThreeAndEightSeconds() {
		assertEquals(1_000L, StreamRetryPolicy.delay(1));
		assertEquals(3_000L, StreamRetryPolicy.delay(2));
		assertEquals(8_000L, StreamRetryPolicy.delay(3));
	}
}
