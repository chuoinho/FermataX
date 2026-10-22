package me.aap.fermata.ui.glass.speedo;

/**
 * Trip time accounting. Time only accumulates while RUNNING; SUSPENDED is a system stop
 * (Android Auto disconnected, dashboard closed) and PAUSED is an explicit user pause.
 */
final class TripClock {
    enum Mode { PAUSED, RUNNING, SUSPENDED }

    private Mode mode = Mode.PAUSED;
    private long totalMs, lastTick = -1, startedAt, suspendedAt = -1, carriedSuspendMs;
    // A new trip does not count time spent waiting for the very first fix.
    private boolean counting;

    Mode mode() { return mode; }
    boolean running() { return mode == Mode.RUNNING; }
    boolean suspended() { return mode == Mode.SUSPENDED; }
    long totalMs() { return totalMs; }
    long startedAt() { return startedAt; }

    void start(long now) {
        mode = Mode.RUNNING;
        lastTick = startedAt = now;
        suspendedAt = -1;
        carriedSuspendMs = 0;
    }

    void pause(long now) {
        tick(now);
        mode = Mode.PAUSED;
    }

    void suspend(long now) {
        tick(now);
        mode = Mode.SUSPENDED;
        suspendedAt = now;
    }

    long suspendedFor(long now) {
        if (mode != Mode.SUSPENDED) return 0;
        return carriedSuspendMs + ((suspendedAt >= 0 && now > suspendedAt) ? now - suspendedAt : 0);
    }

    void tick(long now) {
        if (mode != Mode.RUNNING) return;
        if (counting && lastTick >= 0 && now > lastTick) totalMs += now - lastTick;
        lastTick = Math.max(lastTick, now);
    }

    void firstFix(long fixTime, long now) {
        if (counting) return;
        counting = true;
        totalMs += Math.max(0, now - Math.max(startedAt, fixTime));
        lastTick = now;
    }

    void reset(long now) {
        totalMs = 0;
        counting = false;
        lastTick = startedAt = now;
    }

    /** Restored trips never resume RUNNING by themselves and never count unobserved time. */
    void restore(long totalMs, boolean hasData, boolean suspended, long suspendedForMs) {
        this.totalMs = Math.max(0, totalMs);
        counting = hasData;
        mode = suspended ? Mode.SUSPENDED : Mode.PAUSED;
        suspendedAt = -1;
        carriedSuspendMs = Math.max(0, suspendedForMs);
        lastTick = -1;
    }
}
