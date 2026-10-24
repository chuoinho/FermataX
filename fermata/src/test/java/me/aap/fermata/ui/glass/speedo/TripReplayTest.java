package me.aap.fermata.ui.glass.speedo;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Golden trips: every {@code trips/NAME.csv} test resource has a {@code NAME.properties} with the
 * reference {@code distanceMeters} (car odometer or map) and {@code tolerancePct}; optionally
 * {@code totalTimeMs} with {@code timeToleranceMs}.
 */
public class TripReplayTest {
    @Test public void goldenTripsStayWithinTolerance() throws Exception {
        URL dir = getClass().getClassLoader().getResource("trips");
        assertTrue("missing test resources: trips/", dir != null);
        File[] logs = new File(dir.toURI()).listFiles((d, n) -> n.endsWith(".csv"));
        assertTrue("no golden trips found", logs != null && logs.length > 0);
        StringBuilder failures = new StringBuilder();
        for (File log : logs) {
            Properties expected = new Properties();
            File props = new File(log.getParent(), log.getName().replace(".csv", ".properties"));
            try (InputStream in = new FileInputStream(props)) {
                expected.load(in);
            }
            TripEngine.State s;
            try (InputStream in = new FileInputStream(log)) {
                s = TripReplay.replay(new InputStreamReader(in, StandardCharsets.UTF_8)).state();
            }
            double truth = Double.parseDouble(expected.getProperty("distanceMeters"));
            double tolerance = Double.parseDouble(expected.getProperty("tolerancePct", "2"));
            double errorPct = (s.distanceMeters() - truth) * 100 / truth;
            if (Math.abs(errorPct) > tolerance) {
                failures.append(String.format("%s: %.0f m vs %.0f m (%+.1f%%, limit %.1f%%)%n",
                        log.getName(), s.distanceMeters(), truth, errorPct, tolerance));
            }
            String time = expected.getProperty("totalTimeMs");
            if (time != null) {
                long limit = Long.parseLong(expected.getProperty("timeToleranceMs", "2000"));
                if (Math.abs(s.totalTimeMs() - Long.parseLong(time)) > limit) {
                    failures.append(String.format("%s: time %d ms vs %s ms%n", log.getName(), s.totalTimeMs(), time));
                }
            }
        }
        assertTrue(failures.toString(), failures.length() == 0);
    }

    @Test public void oldCoordinateLogsAreRejected() {
        String v1 = "type,elapsedMs,provider,lat,lon,acc,speed,speedAcc,mock\nE,1000,start\n";
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> TripReplay.replay(new java.io.StringReader(v1)));
    }

    @Test public void distanceOnlyLogReplays() throws Exception {
        // 60 s at 10 m/s with 10 m between fixes: 600 m of road.
        StringBuilder v2 = new StringBuilder(TripLogRecorder.HEADER)
                .append("\ntype,elapsedMs,provider,stepM,acc,speed,speedAcc,mock\nE,1000,start\n");
        for (int i = 0; i <= 60; i++) {
            v2.append("L,").append(2000 + i * 1000).append(",gps,").append(i == 0 ? "" : "10")
                    .append(",5,10,0.5,0\n");
        }
        v2.append("E,63000,stop\n");
        TripEngine.State s = TripReplay.replay(new java.io.StringReader(v2.toString())).state();
        org.junit.Assert.assertEquals(600, s.distanceMeters(), 12);
    }
}
