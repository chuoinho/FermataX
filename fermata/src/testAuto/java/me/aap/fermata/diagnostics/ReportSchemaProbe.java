package me.aap.fermata.diagnostics;

import java.util.Map;

/** Lets tests outside this package see what the report keeps of an event. */
public final class ReportSchemaProbe {
	private static final DiagnosticSanitizer SANITIZER = new DiagnosticSanitizer();

	private ReportSchemaProbe() {
	}

	public static boolean keepsName(String category, String event) {
		return DiagnosticSchema.isCategory(category) && DiagnosticSchema.isEvent(event);
	}

	public static Map<String, Object> keptAttributes(String category, String event, Map<String, ?> attributes) {
		return SANITIZER.sanitizeEventAttributes(category, event, attributes);
	}
}
