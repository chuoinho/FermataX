package me.aap.fermata.diagnostics.android;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import me.aap.fermata.diagnostics.DiagnosticSanitizer;

final class EmergencyCrashReportFormatter {
	private static final int MAX_CAUSES = 8;
	private static final int MAX_FRAMES_PER_CAUSE = 64;
	private static final DiagnosticSanitizer SANITIZER = new DiagnosticSanitizer(2048);

	private EmergencyCrashReportFormatter() {
	}

	static byte[] format(long timestamp, String processName, int processId, Thread thread,
			Throwable error, List<String> breadcrumbs, int maxBytes) {
		if (maxBytes < 1024) throw new IllegalArgumentException("maxBytes must be >= 1024");
		StringBuilder out = new StringBuilder(Math.min(maxBytes, 16 * 1024));
		out.append('{');
		field(out, "schema", "1", false, false);
		field(out, "event", "uncaught_exception", true, true);
		field(out, "timestamp", Long.toString(timestamp), false, true);
		field(out, "process", safe(processName), true, true);
		field(out, "pid", Integer.toString(processId), false, true);
		field(out, "thread", safe((thread == null) ? null : thread.getName()), true, true);
		out.append(",\"throwables\":[");

		IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
		Throwable current = error;
		boolean firstCause = true;
		for (int cause = 0; (current != null) && (cause < MAX_CAUSES); cause++) {
			if (seen.put(current, Boolean.TRUE) != null) break;
			String entry = throwableEntry(current);
			if (!fits(out, entry.length() + (firstCause ? 0 : 1), maxBytes, 32)) break;
			if (!firstCause) out.append(',');
			out.append(entry);
			firstCause = false;
			current = current.getCause();
		}
		out.append("],\"breadcrumbs\":[");

		List<String> safeBreadcrumbs = (breadcrumbs == null) ? Collections.emptyList() : breadcrumbs;
		List<String> selected = new ArrayList<>();
		int selectedChars = 0;
		for (int i = safeBreadcrumbs.size() - 1; i >= 0; i--) {
			String breadcrumb = safeBreadcrumbs.get(i);
			String entry = quote(safe(SANITIZER.sanitize("breadcrumb", breadcrumb)));
			int delimiter = selected.isEmpty() ? 0 : 1;
			if (!fits(out, selectedChars + delimiter + entry.length(), maxBytes, 3)) break;
			selected.add(entry);
			selectedChars += delimiter + entry.length();
		}
		for (int i = selected.size() - 1; i >= 0; i--) {
			if (i != selected.size() - 1) out.append(',');
			out.append(selected.get(i));
		}
		out.append("]}");
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String throwableEntry(Throwable error) {
		StringBuilder out = new StringBuilder(2048);
		out.append("{\"type\":").append(quote(safe(error.getClass().getName())));
		out.append(",\"stack\":[");
		StackTraceElement[] stack = error.getStackTrace();
		int count = Math.min(stack.length, MAX_FRAMES_PER_CAUSE);
		for (int i = 0; i < count; i++) {
			if (i != 0) out.append(',');
			out.append(quote(safe(stack[i].toString())));
		}
		out.append("]}");
		return out.toString();
	}

	private static void field(StringBuilder out, String name, String value, boolean quoted,
			boolean comma) {
		if (comma) out.append(',');
		out.append(quote(name)).append(':');
		out.append(quoted ? quote(value) : value);
	}

	private static boolean fits(StringBuilder out, int candidateChars, int maxBytes, int reserve) {
		return out.length() + candidateChars + reserve <= maxBytes;
	}

	private static String safe(String value) {
		String sanitized = SANITIZER.sanitize(value);
		return (sanitized == null) ? "unknown" : sanitized;
	}

	private static String quote(String value) {
		StringBuilder out = new StringBuilder(value.length() + 16).append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"':
					out.append("\\\"");
					break;
				case '\\':
					out.append("\\\\");
					break;
				case '\n':
					out.append("\\n");
					break;
				case '\r':
					out.append("\\r");
					break;
				case '\t':
					out.append("\\t");
					break;
				default:
					out.append(((c >= 0x20) && (c <= 0x7e)) ? c : '?');
			}
		}
		return out.append('"').toString();
	}
}
