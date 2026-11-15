package io.github.okww.weekly;

/** Normal-attack intent survives OCR latency, but never a skill/hold/switch or stale HUD. */
final class CombatNormalAttack {
    private boolean requested;
    private long lastTap=-1,observed=-1,interval=100;
    void request(){request(100);}
    void request(long intervalMs){
        if(intervalMs<0||intervalMs>1000)throw new IllegalArgumentException("普攻间隔越界");
        requested=true;interval=Math.max(100,intervalMs);
    }
    void observed(long capturedAt){observed=capturedAt;}
    void stop(){requested=false;}
    boolean due(long now,boolean permitted) {
        return requested&&permitted&&observed>=0&&now>=observed&&now-observed<=1200
                &&(lastTap<0||now>=lastTap&&now-lastTap>=interval);
    }
    void sent(long now){lastTap=now;}
}
