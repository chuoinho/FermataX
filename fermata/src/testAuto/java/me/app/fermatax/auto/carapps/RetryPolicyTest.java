package me.app.fermatax.auto.carapps;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RetryPolicyTest {

	@Test
	public void retryDelaysAreOneTwoFourThenStop() {
		assertEquals(1000L, RetryPolicy.delayMs(0));
		assertEquals(2000L, RetryPolicy.delayMs(1));
		assertEquals(4000L, RetryPolicy.delayMs(2));
		assertEquals(-1L, RetryPolicy.delayMs(3));
		assertEquals(-1L, RetryPolicy.delayMs(4));
		assertEquals(-1L, RetryPolicy.delayMs(100));
	}
}
