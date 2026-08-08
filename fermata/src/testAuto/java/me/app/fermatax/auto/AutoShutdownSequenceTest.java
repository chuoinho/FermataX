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
}
