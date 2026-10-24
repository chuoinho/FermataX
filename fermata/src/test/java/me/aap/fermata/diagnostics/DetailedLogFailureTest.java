package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DetailedLogFailureTest {
	private static final StackTraceElement FRAME =
			new StackTraceElement("me.aap.fermata.player.DetailedLogFailureTest", "x", "DetailedLogFailureTest.java", 12);
	private File directory;
	private DiagnosticRecorder recorder;

	@Before
	public void setUp() {
		directory = new File(System.getProperty("java.io.tmpdir"), "fx-logfail-" + UUID.randomUUID());
		assertTrue(directory.mkdirs());
		recorder = DiagnosticRecorder.builder(directory).config(DiagnosticConfig.builder()
				.flushIntervalMillis(20L).build()).sessionId("s").process("main", 1).build();
	}

	@After
	public void tearDown() {
		recorder.close();
		File[] files = directory.listFiles();
		if (files != null) for (File f : files) f.delete();
		directory.delete();
	}

	@Test
	public void detailedWarningIsRecordedByCallSite() throws Exception {
		recorder.setDetailedEnabled(true);
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		assertTrue(limiter.record(recorder, false, null, FRAME, 0));
		assertTrue(limiter.record(recorder, true, new IllegalStateException("Failed to load My Song"), FRAME, 0));
		String journal = journal();
		assertTrue(journal, journal.contains("\"category\":\"log\""));
		assertTrue(journal.contains("\"event\":\"log_warning\""));
		assertTrue(journal.contains("\"event\":\"log_error\""));
		assertTrue(journal.contains("\"call_site\":\"DetailedLogFailureTest.x:12\""));
		assertTrue(journal.contains("IllegalStateException"));
		assertFalse(journal.contains("My Song"));
	}

	@Test
	public void droppedWarningsAreCounted() throws Exception {
		recorder.setDetailedEnabled(true);
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		for (int i = 0; i < 6; i++) limiter.record(recorder, false, null, FRAME, 0);
		limiter.record(recorder, false, null, FRAME, 10_000);
		String journal = journal();
		assertTrue(journal, journal.contains("\"event\":\"log_dropped\""));
		assertTrue(journal.contains("\"count\":1"));
	}

	@Test
	public void nothingIsRecordedWhenDetailedIsOff() throws Exception {
		LogFailureLimiter limiter = new LogFailureLimiter(5, 50, 10_000);
		assertFalse(limiter.record(recorder, false, null, FRAME, 0));
		assertFalse(journal().contains("\"category\":\"log\""));
	}

	private String journal() throws Exception {
		assertTrue(recorder.flush(2000L));
		StringBuilder text = new StringBuilder();
		File[] files = directory.listFiles();
		if (files != null) for (File f : files) {
			if (f.isFile()) text.append(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
		}
		return text.toString();
	}
}
