package me.aap.fermata.addon.tv.stalker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class StalkerHealthTest {
	@Test
	public void recordsSanitizedPassResult() {
		StalkerHealth health = new StalkerHealth();
		health.record(StalkerHealth.Stage.HANDSHAKE, true, 12, 200);
		health.setCatalogCounts(2, 8);
		health.incrementStreamAttempts();
		health.completePass(new StalkerChannel("7", "News", null, "2",
				"ffmpeg https://stream.invalid/live?token=secret"), 206);

		assertEquals(StalkerHealth.Status.PASS, health.getStatus());
		assertEquals(2, health.getCategoryCount());
		assertEquals(8, health.getChannelCount());
		assertEquals(1, health.getStreamAttempts());
		assertEquals(206, health.getStreamStatusCode());
		assertEquals("News", health.getTestedChannelName());
		assertFalse(health.toString().contains("secret"));
		assertThrows(UnsupportedOperationException.class, () -> health.getSteps().put(
				StalkerHealth.Stage.PROFILE, new StalkerHealth.Step(true, 1, 200)));
	}

	@Test
	public void recordsDegradedResultWithoutExposingFailureDetails() {
		StalkerHealth health = new StalkerHealth();
		health.setCatalogCounts(1, 3);
		health.incrementStreamAttempts();
		health.recordStreamFailureStatus(404);
		health.completeDegraded("No sampled stream could be opened");

		assertTrue(health.isDegraded());
		assertEquals(404, health.getStreamStatusCode());
		assertEquals("No sampled stream could be opened", health.getWarning());
		assertEquals(Map.of(), health.getSteps());
	}
}
