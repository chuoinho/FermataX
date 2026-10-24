package me.aap.fermata.ui.glass.speedo;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the 1 Hz speedo stream into a few diagnostic events: every signal change at once, and per
 * 30 s a count of fixes by what happened to them. Enough to tell "no fixes arrive" from "fixes
 * arrive without a usable speed" without logging each fix. Single-threaded (the tracking worker).
 */
final class SpeedoDiagnostics {
    static final long SUMMARY_MS = 30_000;

    interface Sink {
        void event(String name, Map<String, Object> attributes);
    }

    private final Sink sink;
    private final EnumMap<TripEngine.Outcome, Integer> outcomes = new EnumMap<>(TripEngine.Outcome.class);
    private final EnumMap<FixNormalizer.SpeedQuality, Integer> qualities =
            new EnumMap<>(FixNormalizer.SpeedQuality.class);
    private int fixes, mock;
    private long windowStart = -1;
    private TripEngine.Signal signal;
    private boolean speedUnknown;

    SpeedoDiagnostics(Sink sink) {
        this.sink = sink;
    }

    void onFix(TripEngine.Outcome outcome, FixNormalizer.SpeedQuality quality, boolean mock) {
        fixes++;
        if (mock) this.mock++;
        outcomes.merge(outcome, 1, Integer::sum);
        qualities.merge(quality, 1, Integer::sum);
    }

    void onState(TripEngine.Signal signal, boolean speedUnknown, long nowMs) {
        if (signal == this.signal && speedUnknown == this.speedUnknown) return;
        this.signal = signal;
        this.speedUnknown = speedUnknown;
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("signal", signal.name());
        a.put("speed_unknown", speedUnknown);
        sink.event("speedo_signal_changed", a);
    }

    void flush(long nowMs) {
        if (windowStart < 0) windowStart = nowMs;
        if (nowMs - windowStart < SUMMARY_MS) return;
        if (fixes > 0) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("fixes", fixes);
            a.put("mock", mock);
            a.put("duration_ms", nowMs - windowStart);
            for (TripEngine.Outcome o : TripEngine.Outcome.values()) {
                a.put(o.name().toLowerCase(Locale.ROOT), outcomes.getOrDefault(o, 0));
            }
            for (FixNormalizer.SpeedQuality q : FixNormalizer.SpeedQuality.values()) {
                a.put("speed_" + q.name().toLowerCase(Locale.ROOT), qualities.getOrDefault(q, 0));
            }
            sink.event("speedo_fix_summary", a);
        }
        fixes = mock = 0;
        outcomes.clear();
        qualities.clear();
        windowStart = nowMs;
    }
}
