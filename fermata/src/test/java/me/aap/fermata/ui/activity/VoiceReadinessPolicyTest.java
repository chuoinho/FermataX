package me.aap.fermata.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoiceReadinessPolicyTest {
	@Test
	public void waitsForDynamicAddonWithoutBusyPosting() {
		long deadline = VoiceReadinessPolicy.deadline(1_000L);
		assertEquals(31_000L, deadline);
		assertEquals(100L, VoiceReadinessPolicy.RETRY_DELAY_MS);
		assertTrue(VoiceReadinessPolicy.shouldRetry(30_999L, deadline, true));
		assertFalse(VoiceReadinessPolicy.shouldRetry(deadline, deadline, true));
	}

	@Test
	public void destroyedActivityCancelsRetryImmediately() {
		assertFalse(VoiceReadinessPolicy.shouldRetry(1L, 10_000L, false));
	}
}
