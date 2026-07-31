package me.aap.fermata.diagnostics;

import java.util.concurrent.atomic.AtomicLong;

/** Process-local correlation identifiers; values contain no user or transport data. */
public final class DiagnosticIds {
	private static final AtomicLong NEXT = new AtomicLong();

	private DiagnosticIds() {
	}

	public static String next() {
		long value = NEXT.updateAndGet(previous ->
				(previous == Long.MAX_VALUE) ? 1L : previous + 1L);
		return Long.toString(value);
	}
}
