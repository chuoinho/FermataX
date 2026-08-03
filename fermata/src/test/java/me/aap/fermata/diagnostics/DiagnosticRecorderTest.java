package me.aap.fermata.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DiagnosticRecorderTest {
	private File directory;

	@Before
	public void setUp() {
		directory = new File(System.getProperty("java.io.tmpdir"), "fx-recorder-" + UUID.randomUUID());
		assertTrue(directory.mkdirs());
	}

	@After
	public void tearDown() {
		delete(directory);
	}

	@Test
	public void facadeRecordsEssentialAndHonorsDetailedToggle() throws Exception {
		DiagnosticConfig config = DiagnosticConfig.builder()
				.maxFileBytes(4096)
				.maxTotalBytes(8192)
				.flushIntervalMillis(20L)
				.build();
		DiagnosticRecorder recorder = DiagnosticRecorder.builder(directory)
				.config(config)
				.sessionId("session-test")
				.process("main", 77)
				.build();
		try {
			assertTrue(recorder.record(DiagnosticEvent.builder("application", "application_initialized").build()));
			assertFalse(recorder.record(DiagnosticEvent.builder("engine", "engine_callback_rejected")
					.scope(DiagnosticScope.DETAILED).build()));
			recorder.setDetailedEnabled(true);
			assertTrue(recorder.record(DiagnosticEvent.builder("engine", "engine_callback_rejected")
					.scope(DiagnosticScope.DETAILED).put("url", "https://user:pass@host/x?q=1").build()));
			assertTrue(recorder.flush(2000L));

			String journal = readJournal(directory);
			assertTrue(journal, journal.contains("\"event\":\"application_initialized\""));
			assertTrue(journal.contains("\"event\":\"engine_callback_rejected\""));
			assertTrue(journal.contains("\"url\":\"[fingerprint:"));
			assertFalse(journal.contains("https://"));
			assertFalse(journal.contains("user:pass"));
		} finally {
			recorder.close();
		}
	}

	@Test
	public void callerOwnedExpiryControlsDetailedEvents() {
		MutableClock clock = new MutableClock(1000L);
		DiagnosticRecorder recorder = DiagnosticRecorder.builder(directory)
				.clock(clock)
				.detailedState(now -> now < 2000L)
				.build();
		try {
			DiagnosticEvent detail = DiagnosticEvent.builder("voice", "session_state")
					.scope(DiagnosticScope.DETAILED).build();
			assertTrue(recorder.record(detail));
			clock.wallTime = 2000L;
			assertFalse(recorder.record(detail));
		} finally {
			recorder.close();
		}
	}

	@Test
	public void breadcrumbsAreBoundedAndClearRunsOnWorker() {
		DiagnosticConfig config = DiagnosticConfig.builder()
				.maxFileBytes(4096)
				.maxTotalBytes(8192)
				.breadcrumbCapacity(2)
				.flushIntervalMillis(20L)
				.build();
		DiagnosticRecorder recorder = DiagnosticRecorder.create(directory, config);
		try {
			recorder.record(DiagnosticEvent.builder("navigation", "navigation_started").build());
			recorder.record(DiagnosticEvent.builder("navigation", "navigation_completed").build());
			recorder.record(DiagnosticEvent.builder("navigation", "navigation_failed").build());
			List<String> breadcrumbs = recorder.snapshotBreadcrumbs();
			assertEquals(2, breadcrumbs.size());
			assertFalse(breadcrumbs.get(0).contains("\"event\":\"navigation_started\""));
			assertTrue(breadcrumbs.get(0).contains("\"event\":\"navigation_completed\""));
			assertTrue(recorder.clear(2000L));
			assertTrue(recorder.snapshotBreadcrumbs().isEmpty());
			assertEquals("", readJournal(directory));
		} catch (Exception e) {
			throw new AssertionError(e);
		} finally {
			recorder.close();
		}
	}

	@Test
	public void operationWritesCorrelatedTerminalEventOnce() throws Exception {
		DiagnosticRecorder recorder = DiagnosticRecorder.create(directory,
				DiagnosticConfig.builder().maxFileBytes(4096).maxTotalBytes(8192).build());
		try {
			DiagnosticOperation operation = recorder.beginOperation("stremio_protocol", "protocol_request",
					Collections.singletonMap("item", "fingerprint-1"));
			assertTrue(operation.state("engine_selected", Collections.emptyMap()));
			assertTrue(operation.complete(Collections.singletonMap("duration_ms", 12)));
			assertFalse(operation.cancel(Collections.emptyMap()));
			assertTrue(recorder.flush(2000L));
			String journal = readJournal(directory);
			assertTrue(journal.contains("\"event\":\"operation_started\""));
			assertTrue(journal.contains("\"event\":\"operation_completed\""));
			assertFalse(journal.contains("\"event\":\"operation_cancelled\""));
			assertTrue(journal.contains("\"operation_id\":\"" + operation.getId() + "\""));
		} finally {
			recorder.close();
		}
	}

	@Test
	public void rejectedTerminalDoesNotPermanentlyCloseOperation() {
		DiagnosticRecorder recorder = DiagnosticRecorder.create(directory,
				DiagnosticConfig.builder().maxQueueBytes(1).build());
		try {
			DiagnosticOperation operation = recorder.beginOperation("stremio_protocol",
					"protocol_request", Collections.emptyMap());
			assertFalse(operation.complete(Collections.emptyMap()));
			assertFalse(operation.hasTerminalOutcome());
			assertFalse(operation.cancel(Collections.emptyMap()));
			assertFalse(operation.hasTerminalOutcome());
		} finally {
			recorder.close();
		}
	}

	@Test
	public void flushDoesNotStarveWhileEventsKeepArriving() throws Exception {
		DiagnosticRecorder recorder = DiagnosticRecorder.create(directory,
				DiagnosticConfig.builder().maxFileBytes(64 * 1024).maxTotalBytes(128 * 1024)
						.flushIntervalMillis(10L).build());
		AtomicBoolean running = new AtomicBoolean(true);
		Thread producer = new Thread(() -> {
			int sequence = 0;
			while (running.get()) recorder.record(DiagnosticEvent.builder("process", "process_started")
					.put("generation", sequence++).build());
		});
		producer.start();
		try {
			Thread.sleep(50L);
			assertTrue(recorder.flush(2000L));
		} finally {
			running.set(false);
			producer.join(2000L);
			recorder.close();
		}
	}

	@Test
	public void snapshotIsStableAndClearPreservesLaterEvents() throws Exception {
		DiagnosticRecorder recorder = DiagnosticRecorder.create(directory,
				DiagnosticConfig.builder().maxFileBytes(4096).maxTotalBytes(8192)
						.flushIntervalMillis(10L).build());
		File snapshot = new File(directory.getParentFile(), "fx-snapshot-" + UUID.randomUUID());
		try {
			recorder.record(DiagnosticEvent.builder("navigation", "navigation_started").build());
			assertTrue(recorder.createSnapshot(snapshot, 2000L));
			recorder.record(DiagnosticEvent.builder("navigation", "navigation_completed").build());
			assertTrue(recorder.flush(2000L));

			String frozen = readJournal(snapshot);
			assertTrue(frozen.contains("navigation_started"));
			assertFalse(frozen.contains("navigation_completed"));

			assertTrue(recorder.clear(2000L));
			assertTrue(recorder.record(DiagnosticEvent.builder("navigation", "navigation_failed").build()));
			assertTrue(recorder.flush(2000L));
			// Observe the post-clear state through the writer-owned snapshot command rather than
			// racing a direct directory scan against any final writer bookkeeping.
			File currentSnapshot = new File(directory.getParentFile(), "fx-current-" + UUID.randomUUID());
			assertTrue(recorder.createSnapshot(currentSnapshot, 2000L));
			String current = readJournal(currentSnapshot);
			delete(currentSnapshot);
			assertFalse(current.contains("navigation_started"));
			assertTrue(current, current.contains("navigation_failed"));
		} finally {
			recorder.close();
			delete(snapshot);
		}
	}

	@Test
	public void transientStorageFailureRecoversAfterDirectoryBecomesAvailable() throws Exception {
		File blocked = new File(directory, "blocked-store");
		Files.write(blocked.toPath(), new byte[]{1});
		MutableClock clock = new MutableClock(1000L);
		DiagnosticRecorder recorder = DiagnosticRecorder.builder(blocked)
				.clock(clock)
				.config(DiagnosticConfig.builder().flushIntervalMillis(10L).build())
				.build();
		try {
			recorder.record(DiagnosticEvent.builder("diagnostics", "report_create_failed").build());
			assertFalse(recorder.flush(1000L));
			assertTrue(blocked.delete());
			assertTrue(blocked.mkdirs());
			clock.wallTime += 65_000L;
			assertTrue(recorder.record(DiagnosticEvent.builder("diagnostics", "report_save_failed").build()));
			assertTrue(recorder.flush(2000L));
			assertTrue(readJournal(blocked).contains("report_save_failed"));
		} finally {
			recorder.close();
		}
	}

	@Test
	public void deadWriterIsRestartedByNextRecord() throws Exception {
		OneShotFaultClock clock = new OneShotFaultClock();
		DiagnosticRecorder recorder = DiagnosticRecorder.builder(directory)
				.clock(clock)
				.config(DiagnosticConfig.builder().flushIntervalMillis(10L).build())
				.build();
		try {
			Thread.sleep(50L);
			assertTrue(recorder.record(DiagnosticEvent.builder("diagnostics", "report_share_failed").build()));
			assertTrue(recorder.flush(2000L));
			assertTrue(readJournal(directory).contains("report_share_failed"));
		} finally {
			recorder.close();
		}
	}

	private static String readJournal(File directory) throws Exception {
		StringBuilder result = new StringBuilder();
		File[] files = directory.listFiles((dir, name) -> name.endsWith(".jsonl"));
		if (files == null) return "";
		for (File file : files) {
			result.append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		}
		return result.toString();
	}

	private static void delete(File file) {
		if ((file == null) || !file.exists()) return;
		File[] children = file.listFiles();
		if (children != null) for (File child : children) delete(child);
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	private static final class MutableClock implements DiagnosticClock {
		private long wallTime;

		MutableClock(long wallTime) {
			this.wallTime = wallTime;
		}

		@Override
		public long wallTimeMillis() {
			return wallTime;
		}

		@Override
		public long elapsedRealtimeMillis() {
			return wallTime;
		}
	}

	private static final class OneShotFaultClock implements DiagnosticClock {
		private final AtomicBoolean fail = new AtomicBoolean(true);
		private final AtomicLong time = new AtomicLong(1000L);

		@Override
		public long wallTimeMillis() {
			return time.incrementAndGet();
		}

		@Override
		public long elapsedRealtimeMillis() {
			if (fail.compareAndSet(true, false)) throw new IllegalStateException("simulated worker death");
			return time.incrementAndGet();
		}
	}
}
