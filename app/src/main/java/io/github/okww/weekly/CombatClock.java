package io.github.okww.weekly;

/** Active combat time advances only across two continuously observed, non-frozen HUD frames. */
final class CombatClock {
    private long last = -1, active;
    private boolean advancing, gap;
    long observe(long now, boolean frozen, boolean identifiedHud) {
        if (now < 0 || (last >= 0 && now <= last)) throw new IllegalArgumentException("战斗画面时间戳未递增");
        gap=last>=0 && now-last>1500;
        // A slow observation is a gap, not proof of active or frozen time; resume only from the new frame.
        if (last >= 0 && advancing && !frozen && identifiedHud && now - last <= 1500) {
            active += now - last;
        }
        last = now; advancing = !frozen && identifiedHud;
        return active;
    }
    long activeTimeMs() { return active; }
    boolean observationGap() { return gap; }
}
