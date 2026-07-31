package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticIdsTest {
	@Test
	public void idsAreUniqueBoundedCorrelationIdentifiers() {
		String first = DiagnosticIds.next();
		String second = DiagnosticIds.next();
		assertNotEquals(first, second);
		assertTrue(first.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"));
		assertTrue(second.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"));
	}
}
