package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class EmergencyCrashReportFormatterTest {
	@Test
	public void reportIsBoundedAndOmitsThrowableMessages() {
		RuntimeException error = new RuntimeException(
				"password=secret https://example.test/private?token=hidden");
		List<String> breadcrumbs = new ArrayList<>();
		for (int i = 0; i < 1_000; i++) {
			breadcrumbs.add("Authorization: Bearer private-token event=" + i);
		}

		byte[] report = EmergencyCrashReportFormatter.format(10L, "process", 20,
				Thread.currentThread(), error, breadcrumbs, 8 * 1024);
		String json = new String(report, StandardCharsets.UTF_8);

		assertTrue(report.length <= 8 * 1024);
		assertTrue(json.startsWith("{"));
		assertTrue(json.endsWith("}"));
		assertTrue(json.contains("java.lang.RuntimeException"));
		assertFalse(json.contains("secret"));
		assertFalse(json.contains("private-token"));
		assertFalse(json.contains("hidden"));
		assertTrue(json.contains("event=999"));
		assertFalse(json.contains("event=0\\\""));
	}
}
