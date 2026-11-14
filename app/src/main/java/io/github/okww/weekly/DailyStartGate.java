package io.github.okww.weekly;

/** A confirmed console launch expires instead of starting later in another task. */
final class DailyStartGate {
    enum Result { WAIT, START, CANCEL }
    private final long generation, deadline;
    static final long STABLE_FOREGROUND_MS = 1500;
    private boolean consumed;
    private long foregroundSince = -1;
    DailyStartGate(long generation, long now) { this.generation = generation; deadline = now + 15000; }
    Result next(long now, long currentGeneration, boolean daily, boolean ready, boolean busy, boolean foreground) {
        if (consumed || now >= deadline || generation != currentGeneration || !daily || !ready || busy) {
            consumed = true; return Result.CANCEL;
        }
        if (!foreground) { foregroundSince = -1; return Result.WAIT; }
        if (foregroundSince < 0) foregroundSince = now;
        if (now - foregroundSince < STABLE_FOREGROUND_MS) return Result.WAIT;
        consumed = true; return Result.START;
    }
}
