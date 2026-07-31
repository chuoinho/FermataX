package me.aap.fermata.diagnostics.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class DiagnosticsCrashHandlerTest {
	@Test
	public void writesOnceAndDelegatesPreviousHandlerOnce() throws Exception {
		File directory = Files.createTempDirectory("fermata-crash-test").toFile();
		AtomicInteger delegated = new AtomicInteger();
		DiagnosticsCrashHandler handler = new DiagnosticsCrashHandler(directory, "process", 7,
				Collections::emptyList, (thread, error) -> delegated.incrementAndGet());

		handler.uncaughtException(Thread.currentThread(), new IllegalStateException("first"));
		handler.uncaughtException(Thread.currentThread(), new IllegalStateException("second"));

		File[] reports = directory.listFiles();
		assertTrue(reports != null);
		assertEquals(1, reports.length);
		assertTrue(reports[0].length() <= EmergencyCrashWriter.MAX_REPORT_BYTES);
		assertEquals(1, delegated.get());
	}

	@Test
	public void delegatesOriginalFailureWhenEmergencyPathThrowsError() throws Exception {
		File directory = Files.createTempDirectory("fermata-crash-oom-test").toFile();
		AtomicInteger delegated = new AtomicInteger();
		DiagnosticsCrashHandler handler = new DiagnosticsCrashHandler(directory, "process", 7,
				() -> { throw new OutOfMemoryError("simulated"); },
				(thread, error) -> delegated.incrementAndGet());

		handler.uncaughtException(Thread.currentThread(), new IllegalStateException("original"));

		assertEquals(1, delegated.get());
	}
}
