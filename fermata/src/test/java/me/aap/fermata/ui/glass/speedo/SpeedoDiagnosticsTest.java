package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import me.aap.fermata.diagnostics.DiagnosticClock;
import me.aap.fermata.diagnostics.DiagnosticConfig;
import me.aap.fermata.diagnostics.DiagnosticRecorder;

public class SpeedoDiagnosticsTest {
	private final List<String> names = new ArrayList<>();
	private final List<Map<String, Object>> attrs = new ArrayList<>();
	private final SpeedoDiagnostics.Sink sink = (name, a) -> {
		names.add(name);
		attrs.add(new LinkedHashMap<>(a));
	};

	@Test
	public void noDeliveryProducesOnlySignalChange() {
		SpeedoDiagnostics d = new SpeedoDiagnostics(sink);
		noDelivery(d, now -> { });
		assertEquals(List.of("speedo_signal_changed", "speedo_signal_changed"), names);
		assertEquals("WAITING", attrs.get(0).get("signal"));
		assertEquals("STALE", attrs.get(1).get("signal"));
	}

	@Test
	public void fixesWithoutDopplerAreCounted() {
		SpeedoDiagnostics d = new SpeedoDiagnostics(sink);
		noDoppler(d, now -> { });
		int summary = names.indexOf("speedo_fix_summary");
		assertTrue(names.toString(), summary >= 0);
		Map<String, Object> s = attrs.get(summary);
		assertEquals(30, s.get("fixes"));
		assertEquals(30, s.get("accepted"));
		assertEquals(30, s.get("speed_missing"));
		assertEquals(0, s.get("speed_ok"));
		assertEquals(0, s.get("rejected"));
		assertTrue(attrs.toString(), attrs.contains(Map.of("signal", "TRACKING", "speed_unknown", true)));
	}

	@Test
	public void hourOfFixesStaysUnderBudget() {
		SpeedoDiagnostics d = new SpeedoDiagnostics(sink);
		for (long t = 0; t < 3_600_000; t += 1_000) {
			d.onFix(TripEngine.Outcome.ACCEPTED, FixNormalizer.SpeedQuality.OK, false);
			d.onState(TripEngine.Signal.TRACKING, false, t);
			d.flush(t);
		}
		assertTrue(String.valueOf(names.size()), names.size() <= 121);
	}

	/** The fixtures are also the input of tools/diagnostics/analyze.test.mjs. */
	@Test
	public void goldenFixtures() throws Exception {
		golden("speedo-no-delivery.jsonl", record(this::noDelivery));
		golden("speedo-no-doppler.jsonl", record(this::noDoppler));
	}

	/** On mismatch the actual journal is written to build/golden/ for review before updating the fixture. */
	private static void golden(String name, String actual) throws Exception {
		String expected = fixture(name);
		if (!expected.equals(actual)) {
			File out = new File("build/golden/" + name);
			out.getParentFile().mkdirs();
			Files.write(out.toPath(), actual.getBytes(StandardCharsets.UTF_8));
		}
		assertEquals(expected, actual);
	}

	private interface Clock {
		void at(long now);
	}

	private interface Scenario {
		void run(SpeedoDiagnostics d, Clock clock);
	}

	private void noDelivery(SpeedoDiagnostics d, Clock clock) {
		clock.at(0);
		d.onState(TripEngine.Signal.WAITING, false, 0);
		for (long t = 1_000; t <= 60_000; t += 1_000) {
			clock.at(t);
			d.onState(t < 8_000 ? TripEngine.Signal.WAITING : TripEngine.Signal.STALE, false, t);
			d.flush(t);
		}
	}

	private void noDoppler(SpeedoDiagnostics d, Clock clock) {
		clock.at(0);
		d.onState(TripEngine.Signal.WAITING, false, 0);
		for (long t = 1_000; t <= 30_000; t += 1_000) {
			clock.at(t);
			d.onFix(TripEngine.Outcome.ACCEPTED, FixNormalizer.SpeedQuality.MISSING, false);
			d.onState(TripEngine.Signal.TRACKING, t >= 4_000, t);
			d.flush(t);
		}
		clock.at(31_000);
		d.flush(31_000);
	}

	/** Runs a scenario through a real recorder and returns its journal without volatile fields. */
	private static String record(Scenario scenario) throws Exception {
		File dir = new File(System.getProperty("java.io.tmpdir"), "fx-speedo-" + UUID.randomUUID());
		assertTrue(dir.mkdirs());
		long[] now = {0};
		DiagnosticClock clock = new DiagnosticClock() {
			@Override public long wallTimeMillis() { return 1_000_000 + now[0]; }
			@Override public long elapsedRealtimeMillis() { return now[0]; }
		};
		DiagnosticRecorder recorder = DiagnosticRecorder.builder(dir).clock(clock)
				.config(DiagnosticConfig.builder().flushIntervalMillis(20L).build())
				.sessionId("s").process("main", 1).build();
		try {
			recorder.setDetailedEnabled(true);
			scenario.run(new SpeedoDiagnostics((name, a) -> recorder.recordDetailed("speedo", name, null, a)),
					t -> now[0] = t);
			assertTrue(recorder.flush(2000L));
			StringBuilder out = new StringBuilder();
			File[] files = dir.listFiles();
			if (files != null) for (File f : files) {
				for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
					if (line.isBlank()) continue;
					out.append(line.replaceAll(
							"\"(sequence|timestamp_ms|session_id|process|pid|thread|thread_id)\":(\"[^\"]*\"|-?\\d+),", ""))
							.append('\n');
				}
			}
			return out.toString();
		} finally {
			recorder.close();
			File[] files = dir.listFiles();
			if (files != null) for (File f : files) f.delete();
			dir.delete();
		}
	}

	private static String fixture(String name) throws Exception {
		try (InputStream in = SpeedoDiagnosticsTest.class.getResourceAsStream("/diagnostics/" + name)) {
			return (in == null) ? "<missing " + name + ">" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
