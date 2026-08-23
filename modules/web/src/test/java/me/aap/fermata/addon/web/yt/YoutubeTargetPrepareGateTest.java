package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeTargetPrepareGateTest {
	@Test
	public void onlyLatestVideoAndGenerationCanComplete() {
		YoutubeTargetPrepareGate gate = new YoutubeTargetPrepareGate();
		long first = gate.begin("A", 10L);
		long second = gate.begin("B", 11L);

		assertFalse(gate.accepts("A", 10L));
		assertFalse(gate.accepts("B", 10L));
		assertTrue(gate.accepts("B", 11L));
		assertFalse(gate.cancel(first));
		assertFalse(gate.complete("A", 10L));
		assertFalse(gate.complete("B", 10L));
		assertTrue(gate.complete("B", 11L));
		assertTrue(gate.accepts("auto-next", 0L));
		assertFalse(gate.cancel(second));
	}

	@Test
	public void timeoutCanOnlyCancelItsOwnRequest() {
		YoutubeTargetPrepareGate gate = new YoutubeTargetPrepareGate();
		long request = gate.begin("B", 20L);

		assertTrue(gate.cancel(request));
		assertFalse(gate.complete("B", 20L));
		assertFalse(gate.cancel(request));
	}
}
