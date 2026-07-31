package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticJsonTest {
	@Test
	public void serializesVersionedEnvelopeAndEscapesPayload() {
		DiagnosticEvent raw = DiagnosticEvent.builder("playback", "playback_owner_adopted")
				.operationId("op-7")
				.priority(DiagnosticPriority.WARN)
				.put("title", "line 1\n\"line 2\"")
				.put("position", 42)
				.build();
		DiagnosticEvent event = raw.withEnvelope(9L, 1000L, 22L, "session-1",
				"main", 123, "UI", 5L, new DiagnosticSanitizer());

		String json = DiagnosticJson.encode(event);
		assertTrue(json.startsWith("{\"schema_version\":1,"));
		assertTrue(json.contains("\"sequence\":9"));
		assertTrue(json.contains("\"operation_id\":\"op-7\""));
		assertTrue(json.contains("\"scope\":\"essential\""));
		assertTrue(json.contains("\"priority\":\"warn\""));
		assertTrue(json.contains("\"title\":\"[fingerprint:"));
		assertFalse(json.contains("line 2"));
		assertFalse(json.contains("line 1\n"));
		assertTrue(json.endsWith("}"));
	}

	@Test
	public void nonFiniteNumbersBecomeJsonNull() {
		DiagnosticEvent event = DiagnosticEvent.builder("engine", "engine_error")
				.put("position", Double.NaN)
				.build()
				.withEnvelope(1L, 2L, 3L, "s", "p", 1, "t", 2L,
						new DiagnosticSanitizer());

		assertTrue(DiagnosticJson.encode(event).contains("\"position\":null"));
	}

	@Test
	public void invalidEnvelopeIdentifiersFailClosed() {
		DiagnosticEvent event = DiagnosticEvent.builder("private_movie", "private_query")
				.operationId("https://private.example/watch?q=secret")
				.put("operation", "another private title")
				.build()
				.withEnvelope(1L, 2L, 3L, "s", "p", 1, "t", 2L,
						new DiagnosticSanitizer());

		String json = DiagnosticJson.encode(event);
		assertTrue(json.contains("\"category\":\"invalid\""));
		assertTrue(json.contains("\"event\":\"invalid\""));
		assertTrue(json.contains("\"operation_id\":\"invalid\""));
		assertTrue(json.contains("\"operation\":\"invalid\""));
		assertFalse(json.contains("private_movie"));
		assertFalse(json.contains("private_query"));
		assertFalse(json.contains("private.example"));
	}
}
