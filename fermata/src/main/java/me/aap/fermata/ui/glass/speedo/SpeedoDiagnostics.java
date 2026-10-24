package me.aap.fermata.ui.glass.speedo;

import java.util.EnumMap;
import me.aap.fermata.location.LocationSource;
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
    static final long FLAG_HOLD_MS = 3_000;

    interface Sink {
        void event(String name, Map<String, Object> attributes);
    }

    private final Sink sink;
    private final EnumMap<TripEngine.Outcome, Integer> outcomes = new EnumMap<>(TripEngine.Outcome.class);
    private final EnumMap<FixNormalizer.SpeedQuality, Integer> qualities =
            new EnumMap<>(FixNormalizer.SpeedQuality.class);
    private final EnumMap<LocationSource, Integer> sources = new EnumMap<>(LocationSource.class);
    private int fixes, mock;
    private long windowStart = -1;
    private TripEngine.Signal signal;
    private boolean speedUnknown;
    private long flagSince = -1, lastFlagAt = -1;

    SpeedoDiagnostics(Sink sink) {
        this.sink = sink;
    }

    void onFix(TripEngine.Outcome outcome, FixNormalizer.SpeedQuality quality, boolean mock) {
        onFix(outcome, quality, mock, null);
    }

    void onFix(TripEngine.Outcome outcome, FixNormalizer.SpeedQuality quality, boolean mock, LocationSource source) {
        if (source != null) sources.merge(source, 1, Integer::sum);
        fixes++;
        if (mock) this.mock++;
        outcomes.merge(outcome, 1, Integer::sum);
        qualities.merge(quality, 1, Integer::sum);
    }

    /**
     * A signal change is recorded at once. A change of only the "no speed to show" flag, which
     * flaps when Doppler comes and goes, is recorded once it has held for {@link #FLAG_HOLD_MS}
     * and at most once per {@link #SUMMARY_MS}.
     */
    void onState(TripEngine.Signal signal, boolean speedUnknown, long nowMs) {
        if (signal != this.signal) {
            emitState(signal, speedUnknown);
            return;
        }
        if (speedUnknown == this.speedUnknown) {
            flagSince = -1;
            return;
        }
        if (flagSince < 0) flagSince = nowMs;
        if (nowMs - flagSince >= FLAG_HOLD_MS && (lastFlagAt < 0 || nowMs - lastFlagAt >= SUMMARY_MS)) {
            lastFlagAt = nowMs;
            emitState(signal, speedUnknown);
        }
    }

    private void emitState(TripEngine.Signal signal, boolean speedUnknown) {
        this.signal = signal;
        this.speedUnknown = speedUnknown;
        flagSince = -1;
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("signal", signal.name());
        a.put("speed_unknown", speedUnknown);
        sink.event("speedo_signal_changed", a);
    }

    void flush(long nowMs) {
        flush(nowMs, false);
    }

    void flushFinal(long nowMs) {
        flush(nowMs, true);
    }

    private void flush(long nowMs, boolean force) {
        if (windowStart < 0) windowStart = nowMs;
        if (!force && nowMs - windowStart < SUMMARY_MS) return;
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
            for (LocationSource source : LocationSource.values()) a.put("src_" + source.id(), sources.getOrDefault(source, 0));
            sink.event("speedo_fix_summary", a);
        }
        fixes = mock = 0;
        outcomes.clear();
        qualities.clear();
        sources.clear();
        windowStart = nowMs;
    }
}
