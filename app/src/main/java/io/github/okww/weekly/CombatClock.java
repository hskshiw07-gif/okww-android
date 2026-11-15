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
    boolean attackPending(long activeStart,long wallStart,long now,long duration) {
        if(activeStart>active||now<wallStart||duration<0)throw new IllegalArgumentException("普攻时钟或时长越界");
        // A slow device may never provide adjacent HUD frames. End this burst without
        // inventing active time; the caller must still verify combat before continuing.
        return active-activeStart<duration&&now-wallStart<duration+7000;
    }
    boolean observationGap() { return gap; }
}
