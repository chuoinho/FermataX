package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Map;

import org.junit.Test;

import me.aap.fermata.diagnostics.DiagnosticPriority;

public class ApplicationExitRecordTest {
	@Test
	public void mapsKnownAndFutureReasons() {
		assertEquals("crash", ApplicationExitRecord.reasonName(4));
		assertEquals("native_crash", ApplicationExitRecord.reasonName(5));
		assertEquals("anr", ApplicationExitRecord.reasonName(6));
		assertEquals("reason_99", ApplicationExitRecord.reasonName(99));
	}

	@Test
	public void exportsOnlySanitizedAllowlistedMetadata() {
		ApplicationExitRecord record = new ApplicationExitRecord(6, 1, 200,
				"me.app.fermataX:auto", 100L, 200L, 300L);
		Map<String, Object> fields = record.toAttributes();

		assertEquals(DiagnosticPriority.ERROR, record.getPriority());
		assertEquals("anr", fields.get("reason"));
		assertEquals(300L, fields.get("timestamp"));
		assertFalse(fields.containsKey("description"));
		assertFalse(fields.containsKey("trace"));
		assertFalse(fields.containsKey("tombstone"));
	}
}
