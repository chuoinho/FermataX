package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.location.Location;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = me.aap.utils.app.App.class)
public class TripLogRecorderTest {
	private File dir;

	@Before
	public void setUp() {
		dir = new File(System.getProperty("java.io.tmpdir"), "fx-trips-" + UUID.randomUUID());
		assertTrue(dir.mkdirs());
	}

	@After
	public void tearDown() {
		File[] files = dir.listFiles();
		if (files != null) for (File f : files) f.delete();
		dir.delete();
	}

	@Test
	public void openFailureDisablesRecordingUntilTheNextTrip() throws Exception {
		File blocked = new File(dir, "blocked");
		Files.write(blocked.toPath(), new byte[]{1});
		TripLogRecorder r = new TripLogRecorder(blocked, () -> true);
		r.event("start", 1_000);
		assertTrue(blocked.delete());
		assertTrue(blocked.mkdir());
		r.location(fix(21, 105, 2_000));
		r.event("pause", 3_000);
		assertEquals("no retry after a failed open", 0, blocked.listFiles().length);
		r.event("start", 4_000);
		r.event("stop", 5_000);
		assertEquals(1, blocked.listFiles().length);
		for (File f : blocked.listFiles()) assertTrue(f.delete());
		assertTrue(blocked.delete());
	}

	@Test
	public void flushFailureDoesNotReopenOrPruneDuringTheTrip() throws Exception {
		TripLogRecorder r = new TripLogRecorder(dir, () -> true);
		r.event("start", 1_000);
		java.lang.reflect.Field field = TripLogRecorder.class.getDeclaredField("out");
		field.setAccessible(true);
		r.close();
		field.set(r, new java.io.BufferedWriter(new java.io.Writer() {
			@Override public void write(char[] c, int o, int n) throws java.io.IOException {
				throw new java.io.IOException("disk full");
			}
			@Override public void flush() throws java.io.IOException { throw new java.io.IOException("disk full"); }
			@Override public void close() { }
		}));
		r.location(fix(21, 105, 2_000));
		assertEquals("failed writer is closed", null, field.get(r));
		r.location(fix(21, 105, 3_000));
		assertEquals("failed trip never reopens", null, field.get(r));
	}

	@Test
	public void writesDistanceNotCoordinates() throws Exception {
		TripLogRecorder r = new TripLogRecorder(dir, () -> true);
		r.event("start", 1_000);
		r.location(fix(21.0281, 105.8342, 2_000));
		r.location(fix(21.0291, 105.8342, 3_000));
		r.event("stop", 4_000);
		List<String> lines = onlyFileLines();
		assertEquals(TripLogRecorder.HEADER, lines.get(0));
		assertEquals("type,elapsedMs,provider,stepM,acc,speed,speedAcc,mock,source", lines.get(1));
		assertTrue(lines.toString(), lines.contains("L,2000,gps,,5.0,10.0,,0,"));
		assertTrue(lines.toString(), lines.contains("L,3000,gps,111,5.0,10.0,,0,"));
		String all = String.join("\n", lines);
		assertFalse(all.contains("21.02"));
		assertFalse(all.contains("105.83"));
	}

	@Test
	public void followsEnabledSwitch() throws Exception {
		AtomicBoolean enabled = new AtomicBoolean(false);
		TripLogRecorder r = new TripLogRecorder(dir, enabled::get);
		r.event("start", 1_000);
		r.location(fix(21.0281, 105.8342, 2_000));
		assertEquals(0, files().length);
		enabled.set(true);
		r.location(fix(21.0291, 105.8342, 3_000));
		assertEquals(1, files().length);
		enabled.set(false);
		r.location(fix(21.0301, 105.8342, 4_000));
		int lines = onlyFileLines().size();
		r.location(fix(21.0311, 105.8342, 5_000));
		assertEquals(lines, onlyFileLines().size());
		assertFalse(String.join("\n", onlyFileLines()).contains("L,5000"));
	}

	/** An export taken while the trip is still running must see the latest fixes. */
	@Test
	public void latestFixIsOnDiskBeforeTheTripEnds() throws Exception {
		TripLogRecorder r = new TripLogRecorder(dir, () -> true);
		r.event("start", 1_000);
		r.location(fix(21.0281, 105.8342, 2_000));
		assertTrue(onlyFileLines().toString(), onlyFileLines().contains("L,2000,gps,,5.0,10.0,,0,"));
	}

	@Test
	public void keepsNewestFiveFiles() throws Exception {
		for (int i = 0; i < 7; i++) {
			File old = new File(dir, "trip-old-" + i + ".csv");
			Files.write(old.toPath(), (TripLogRecorder.HEADER + "\n").getBytes(StandardCharsets.UTF_8));
			assertTrue(old.setLastModified(1_000_000L * (i + 1)));
		}
		TripLogRecorder r = new TripLogRecorder(dir, () -> true);
		r.event("start", 1_000);
		r.event("stop", 2_000);
		assertEquals(5, files().length);
		assertFalse(new File(dir, "trip-old-0.csv").exists());
		assertFalse(new File(dir, "trip-old-1.csv").exists());
		assertFalse(new File(dir, "trip-old-2.csv").exists());
	}

	private static Location fix(double lat, double lon, long elapsedMs) {
		Location l = new Location("gps");
		l.setLatitude(lat);
		l.setLongitude(lon);
		l.setAccuracy(5);
		l.setSpeed(10);
		l.setElapsedRealtimeNanos(elapsedMs * 1_000_000L);
		return l;
	}

	private File[] files() {
		File[] files = dir.listFiles((d, n) -> n.endsWith(".csv"));
		return (files == null) ? new File[0] : files;
	}

	private List<String> onlyFileLines() throws Exception {
		File[] files = files();
		assertEquals(1, files.length);
		return Files.readAllLines(files[0].toPath(), StandardCharsets.UTF_8);
	}

    @Test public void writesSourceColumn() throws Exception {
        TripLogRecorder r = new TripLogRecorder(dir, () -> true); r.event("start", 1000);
        r.location(fix(0,0,2000), me.aap.fermata.location.LocationSource.PLATFORM_FUSED);
        r.location(fix(0,0.0003,3000), me.aap.fermata.location.LocationSource.GPS);
        r.event("stop",4000);
        assertTrue(onlyFileLines().get(1).endsWith(",source"));
        assertTrue(onlyFileLines().contains("L,2000,gps,,5.0,10.0,,0,platform_fused"));
        assertTrue(onlyFileLines().contains("L,3000,gps,,5.0,10.0,,0,gps"));
    }

}
