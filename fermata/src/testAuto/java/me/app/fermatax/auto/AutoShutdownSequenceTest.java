package me.app.fermatax.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class AutoShutdownSequenceTest {
	@Test
	public void participantFailureDoesNotBlockLaterCleanup() {
		List<String> calls = new ArrayList<>();
		List<String> failures = AutoShutdownSequence.run(
				new AutoShutdownSequence.Step("first", () -> calls.add("first")),
				new AutoShutdownSequence.Step("broken", () -> {
					calls.add("broken");
					throw new IllegalStateException("expected");
				}),
				new AutoShutdownSequence.Step("last", () -> calls.add("last")));

		assertEquals(List.of("first", "broken", "last"), calls);
		assertEquals(1, failures.size());
		assertTrue(failures.get(0).startsWith("broken:"));
	}

	@Test
	public void carDisplayStepPrecedesMirrorDisplayInShutdownSequence() {
		List<String> executed = new ArrayList<>();
		AutoShutdownSequence.run(
				new AutoShutdownSequence.Step("media", () -> executed.add("media")),
				new AutoShutdownSequence.Step("mirror_surface", () -> executed.add("mirror_surface")),
				new AutoShutdownSequence.Step("car_display", () -> executed.add("car_display")),
				new AutoShutdownSequence.Step("mirror_display", () -> executed.add("mirror_display")),
				new AutoShutdownSequence.Step("projection_service", () -> executed.add("projection_service")));

		assertEquals(List.of("media", "mirror_surface", "car_display", "mirror_display", "projection_service"), executed);
		int carDisplayIdx = executed.indexOf("car_display");
		int mirrorDisplayIdx = executed.indexOf("mirror_display");
		assertTrue(carDisplayIdx >= 0 && mirrorDisplayIdx >= 0 && carDisplayIdx < mirrorDisplayIdx);
	}
}
