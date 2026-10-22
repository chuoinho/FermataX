package me.aap.fermata.ui.glass.speedo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays a trip log written by TripLogRecorder through a TripEngine, driving tick/motion at
 * 1 Hz between rows exactly like the controller heartbeat.
 *
 * <pre>
 * L,elapsedMs,provider,lat,lon,acc,speed,speedAcc,mock   location (empty = missing value)
 * I,elapsedMs,quiet                                      IMU window, quiet = 1 when still
 * E,elapsedMs,start|stop|pause|reset                     lifecycle event
 * </pre>
 */
final class TripReplay {
    private TripReplay() {}

    static TripEngine replay(Reader csv) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(csv)) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("type,")) continue;
                rows.add(line.split(",", -1));
            }
        }
        TripEngine e = new TripEngine();
        long clock = -1;
        boolean quiet = false;
        for (String[] row : rows) {
            long t = Long.parseLong(row[1]);
            if (clock < 0) clock = t;
            while (clock + 1_000 <= t) {
                clock += 1_000;
                e.tick(clock);
                e.motion(clock, quiet, Double.NaN);
            }
            switch (row[0]) {
                case "L" -> e.accept(FixNormalizer.of(t, d(row[3]), d(row[4]),
                        !row[5].isEmpty(), (float) d(row[5]), !row[6].isEmpty(), (float) d(row[6]),
                        !row[7].isEmpty(), (float) d(row[7])), t);
                case "I" -> quiet = "1".equals(row[2]);
                case "E" -> {
                    switch (row[2]) {
                        case "start" -> e.start(t);
                        case "stop" -> e.suspend(t);
                        case "pause" -> e.pause(t);
                        case "reset" -> e.reset(t);
                        default -> { }
                    }
                }
                default -> { }
            }
        }
        return e;
    }

    private static double d(String s) { return s.isEmpty() ? Double.NaN : Double.parseDouble(s); }
}
