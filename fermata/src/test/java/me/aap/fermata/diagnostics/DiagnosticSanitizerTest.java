package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class DiagnosticSanitizerTest {
	private final DiagnosticSanitizer sanitizer = new DiagnosticSanitizer(4096);

	@Test
	public void schemaRecognizesProductionEnvelopeIdentifiers() {
		assertTrue(DiagnosticSchema.isCategory("application"));
		assertTrue(DiagnosticSchema.isCategory("hardware_input"));
		assertTrue(DiagnosticSchema.isEvent("application_initialized"));
		assertTrue(DiagnosticSchema.isEvent("input_received"));
		assertTrue(DiagnosticSchema.isEvent("database_open_retry"));
		assertTrue(DiagnosticSchema.isOperation("protocol_request"));
		assertFalse(DiagnosticSchema.isCategory("private_movie"));
		assertFalse(DiagnosticSchema.isEvent("private_query"));
	}

	@Test
	public void stripsUrlCredentialsQueryAndFragment() {
		String clean = sanitizer.sanitize("request https://alice:hunter2@example.com/video/42" +
				"?token=secret&id=7#private finished");

		assertTrue(clean.contains("[fingerprint:"));
		assertFalse(clean.contains("example.com"));
		assertFalse(clean.contains("alice"));
		assertFalse(clean.contains("hunter2"));
		assertFalse(clean.contains("token=secret"));
	}

	@Test
	public void stripsHeadersAssignmentsAndLocalPaths() {
		String clean = sanitizer.sanitize("Authorization: Bearer abc.def password=hunter2\n" +
				"Cookie: sid=private; second=also-private\n" +
				"C:\\Users\\name\\prefs.xml /data/user/0/app/shared_prefs/x.xml");

		assertFalse(clean.contains("abc.def"));
		assertFalse(clean.contains("sid=private"));
		assertFalse(clean.contains("also-private"));
		assertFalse(clean.contains("hunter2"));
		assertFalse(clean.contains("Users\\name"));
		assertFalse(clean.contains("/data/user"));
		assertTrue(clean.contains("[path]"));
	}

	@Test
	public void redactsSensitiveKeysRecursivelyAndBreaksCycles() {
		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("accessToken", "top-secret");
		nested.put("voiceTranscript", "play private station");
		nested.put("safe", "ready");
		nested.put("self", nested);

		Map<String, Object> clean = sanitizer.sanitizeAttributes(nested);
		assertEquals(DiagnosticSanitizer.REDACTED, clean.get("accessToken"));
		assertEquals(DiagnosticSanitizer.REDACTED, clean.get("voiceTranscript"));
		assertEquals("ready", clean.get("safe"));
		assertEquals("[cycle]", clean.get("self"));
	}

	@Test
	public void fileAndMagnetUrisNeverLeakPayload() {
		String clean = sanitizer.sanitize("file:///data/user/0/private.db " +
				"magnet:?xt=urn:btih:0123456789abcdef");

		assertTrue(clean.matches("\\[fingerprint:[0-9a-f]+] \\[fingerprint:[0-9a-f]+]"));
	}

	@Test
	public void privateSourceKeysAndSecretPathSegmentsAreRedacted() {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("rssUrl", "https://feeds.example/private.xml");
		input.put("page", "https://example.com/token/abc123/watch");

		Map<String, Object> clean = sanitizer.sanitizeAttributes(input);
		assertEquals(DiagnosticSanitizer.REDACTED, clean.get("rssUrl"));
		assertTrue(String.valueOf(clean.get("page")).startsWith("[fingerprint:"));
		assertFalse(String.valueOf(clean.get("page")).contains("example.com"));
	}

	@Test
	public void throwableStackIsBoundedAndSanitized() {
		RuntimeException error = new RuntimeException(
				"Authorization: Bearer private-token https://user:pass@example.com/watch?q=secret");
		StackTraceElement[] stack = new StackTraceElement[100];
		for (int i = 0; i < stack.length; i++) {
			stack[i] = new StackTraceElement("Example" + i, "run", "Example.java", i + 1);
		}
		error.setStackTrace(stack);

		Map<String, Object> clean = sanitizer.sanitizeThrowable(error);
		String trace = String.valueOf(clean.get("stack"));
		assertFalse(clean.containsKey("message"));
		assertFalse(trace.contains("private-token"));
		assertFalse(trace.contains("user:pass"));
		assertTrue(trace.contains("...36 more"));
		assertFalse(trace.contains("Example99"));
	}

	@Test
	public void aggregateEventPayloadIsBounded() {
		Map<String, Object> input = new LinkedHashMap<>();
		String large = "x".repeat(4096);
		for (int i = 0; i < 20; i++) input.put("field_" + i, large);

		Map<String, Object> clean = sanitizer.sanitizeAttributes(input);
		int chars = clean.entrySet().stream()
				.mapToInt(entry -> entry.getKey().length() + String.valueOf(entry.getValue()).length())
				.sum();
		assertTrue(chars < 9000);
		assertTrue(clean.containsKey("_truncated"));
	}

	@Test
	public void eventSchemaDropsUnknownFieldsAndFingerprintsPrivateValues() {
		Map<String, Object> clean = sanitizer.sanitizeEventAttributes("search", "started",
				Map.of("query", "private movie", "unknown_payload", "must-not-leak",
						"result_count", 3, "stale", true));

		assertTrue(String.valueOf(clean.get("query")).startsWith("[fingerprint:"));
		assertEquals(3, clean.get("result_count"));
		assertEquals(true, clean.get("stale"));
		assertFalse(clean.containsKey("unknown_payload"));
	}

	@Test
	public void hardwareInputSchemaKeepsOnlyNonTextControlMetadata() {
		Map<String, Object> attributes = new LinkedHashMap<>();
		attributes.put("key_code", 87);
		attributes.put("key_action", 0);
		attributes.put("repeat_count", 0);
		attributes.put("scan_code", 0);
		attributes.put("device_id", 4);
		attributes.put("input_source", 257);
		attributes.put("input_origin", "MEDIA_SESSION");
		attributes.put("mapped_key", "MEDIA_NEXT");
		attributes.put("session_active", true);
		attributes.put("playback_revision", 9);
		attributes.put("playback_state", 3);
		attributes.put("supported_actions", 48);
		attributes.put("unknown_text", "private input");
		Map<String, Object> clean = sanitizer.sanitizeEventAttributes(
				"hardware_input", "input_received", attributes);

		assertEquals(87, clean.get("key_code"));
		assertEquals("MEDIA_SESSION", clean.get("input_origin"));
		assertEquals("MEDIA_NEXT", clean.get("mapped_key"));
		assertEquals(true, clean.get("session_active"));
		assertFalse(clean.containsKey("unknown_text"));
		assertFalse(clean.toString().contains("private input"));
	}

	@Test
	public void eventSchemaRejectsArbitraryNestedValues() {
		Map<String, Object> clean = sanitizer.sanitizeEventAttributes("playback", "state",
				Map.of("state", Map.of("title", "private title", "query", "private query"),
						"status", java.util.List.of("https://private.example/watch")));

		assertFalse(clean.containsKey("state"));
		assertFalse(clean.containsKey("status"));
		assertFalse(clean.toString().contains("private title"));
		assertFalse(clean.toString().contains("private query"));
		assertFalse(clean.toString().contains("private.example"));
	}

	@Test
	public void throwableKeepsOnlyBoundedStructuredErrorFields() {
		Map<String, Object> clean = sanitizer.sanitizeEventAttributes("engine", "failed",
				Map.of("error", new IllegalStateException("private title query")));

		assertTrue(clean.get("error") instanceof Map);
		assertEquals(2, ((Map<?, ?>) clean.get("error")).size());
		assertFalse(clean.toString().contains("private title query"));
	}
}
