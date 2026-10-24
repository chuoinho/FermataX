package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

public class LogFailureLimiterTest {

	@Test public void stoppedWarningLoopStillReportsItsDropsAfterTheWindow() throws Exception {
		java.io.File dir = java.nio.file.Files.createTempDirectory("log-drops").toFile();
		DiagnosticRecorder recorder = DiagnosticRecorder.builder(dir)
				.config(DiagnosticConfig.builder().flushIntervalMillis(10).build()).build();
		try {
			recorder.setDetailedEnabled(true);
			LogFailureLimiter limiter = new LogFailureLimiter(1, 10, 50);
			StackTraceElement caller = new StackTraceElement("Example", "loop", "Example.java", 1);
			assertTrue(limiter.record(recorder, false, null, caller, 0));
			assertFalse(limiter.record(recorder, false, null, caller, 1));
			assertFalse(limiter.record(recorder, false, null, caller, 2));
			long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
			String journal = "";
			do {
				recorder.flush(1000);
				StringBuilder text = new StringBuilder();
				for (java.io.File f : dir.listFiles((d, n) -> n.endsWith(".jsonl"))) {
					text.append(new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8));
				}
				journal = text.toString();
				if (journal.contains("\"event\":\"log_dropped\"")) break;
				Thread.sleep(10);
			} while (System.nanoTime() < deadline);
			assertTrue(journal, journal.contains("\"event\":\"log_dropped\""));
			assertTrue(journal, journal.contains("\"count\":2"));
		} finally { recorder.close(); }
	}

	@Test
	public void limitsEachCallSiteAndReportsDropped() {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("A.f:1", 0));
		assertFalse(limiter.tryAcquire("A.f:1", 0));
		assertTrue(limiter.tryAcquire("B.g:2", 0));
		assertEquals(Map.of("A.f:1", 1), limiter.drainDropped());
		assertEquals(Collections.emptyMap(), limiter.drainDropped());
	}

	@Test
	public void limitsTotalAcrossCallSites() {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 50; i++) assertTrue(limiter.tryAcquire("S.m:" + i, 0));
		assertFalse(limiter.tryAcquire("S.m:50", 0));
		assertEquals(Map.of("S.m:50", 1), limiter.drainDropped());
	}

	@Test
	public void nextWindowStartsFresh() {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 6; i++) limiter.tryAcquire("A.f:1", 0);
		assertFalse(limiter.tryAcquire("A.f:1", 9_999));
		assertTrue(limiter.tryAcquire("A.f:1", 10_000));
	}
}
