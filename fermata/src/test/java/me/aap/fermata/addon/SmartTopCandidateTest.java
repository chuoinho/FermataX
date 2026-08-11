package me.aap.fermata.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SmartTopCandidateTest {
	@Test
	public void resumeRequiresMeaningfulFiniteProgress() {
		SmartTopCandidate candidate = candidate(SmartTopCandidate.Kind.RESUME,
				120_000L, 600_000L, false);
		assertTrue(candidate.isMeaningfulResume());
		assertThrows(IllegalArgumentException.class, () -> candidate(
				SmartTopCandidate.Kind.RESUME, 10_000L, 600_000L, false));
		assertThrows(IllegalArgumentException.class, () -> candidate(
				SmartTopCandidate.Kind.RESUME, 570_000L, 600_000L, false));
		assertThrows(IllegalArgumentException.class, () -> candidate(
				SmartTopCandidate.Kind.RESUME, 120_000L, 600_000L, true));
	}

	@Test
	public void displayPayloadRejectsAddressesAndBoundsText() {
		assertThrows(SecurityException.class, () -> new SmartTopCandidate(
				"addon", 1L, "opaque", SmartTopCandidate.Kind.RECOMMENDED, true,
				0L, 0L, false, "https://secret.example", "", false, false, 1L));
		String title = "x".repeat(SmartTopCandidate.MAX_TEXT_CHARS + 20);
		SmartTopCandidate bounded = new SmartTopCandidate("addon", 1L, "opaque",
				SmartTopCandidate.Kind.RECOMMENDED, true, 0L, 0L, false,
				title, "subtitle", false, false, 1L);
		assertEquals(SmartTopCandidate.MAX_TEXT_CHARS, bounded.title().length());
	}

	@Test
	public void favoriteValueRequiresAuthoritativeState() {
		assertThrows(IllegalArgumentException.class, () -> new SmartTopCandidate(
				"addon", 1L, "opaque", SmartTopCandidate.Kind.RECOMMENDED, true,
				0L, 0L, false, "Title", "", false, true, 1L));
	}

	private static SmartTopCandidate candidate(SmartTopCandidate.Kind kind,
			long position, long duration, boolean completed) {
		return new SmartTopCandidate("addon", 1L, "opaque", kind, true,
				position, duration, completed, "Title", "Subtitle", false, false, 1L);
	}
}
