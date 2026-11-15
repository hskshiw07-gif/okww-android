package io.github.okww.weekly;

/** Retry ignored skill touches only on a newer, still-ready HUD; animations retain their own wait. */
final class CombatSkillRetry {
    enum Step { WAIT, TAP, FINISHED }
    private long lastTap;
    private int attempts=1;
    CombatSkillRetry(long firstTap){lastTap=firstTap;}
    Step next(long now,long capturedAt,boolean ready,boolean animation) {
        if(animation||!ready||capturedAt<=lastTap||now<capturedAt||now-capturedAt>1200||now-lastTap<250)
            return Step.WAIT;
        return attempts>=3?Step.FINISHED:Step.TAP;
    }
    void sent(long now){lastTap=now;attempts++;}
}
