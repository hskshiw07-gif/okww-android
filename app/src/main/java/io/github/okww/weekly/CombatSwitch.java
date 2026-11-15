package io.github.okww.weekly;

/** Bounded portrait-switch retry policy; only a fresh same-account HUD may authorize input or confirmation. */
final class CombatSwitch {
    enum Kind { WAIT, TAP, CONFIRMED }
    static final class Step {
        final Kind kind;
        final float x,y;
        private Step(Kind kind,float x,float y){this.kind=kind;this.x=x;this.y=y;}
    }
    private static final Step WAIT=new Step(Kind.WAIT,0,0),CONFIRMED=new Step(Kind.CONFIRMED,0,0);
    private final int source,target;
    private final long started;
    private long lastFrame=-1,lastTap=-1,lastNow;
    private int attempts;

    CombatSwitch(int source,int target,long started) {
        if(source<0||source>2||target<0||target>2||source==target||started<0)
            throw new IllegalArgumentException("切人角色或开始时间异常");
        this.source=source;this.target=target;this.started=started;lastNow=started;
    }
    int attempts(){return attempts;}

    Step next(long now,long capturedAt,boolean hud,boolean sameUid,ActiveCharacterRecognizer.State identity) throws InterruptedException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(now<lastNow)throw new IllegalArgumentException("切人时钟倒退");
        lastNow=now;
        long elapsed=now-started;
        // Recorded mobile intro/switch settles after 7.3s. Input still closes at 5s;
        // the remaining time permits fresh same-account observation only.
        if(elapsed>=10000 || elapsed>=5000&&attempts==0)
            throw new IllegalStateException("切人未由新鲜头像确认，不继续按目标角色输出");
        boolean fresh=capturedAt>=0&&capturedAt<=now&&now-capturedAt<=1200&&capturedAt>lastFrame;
        if(capturedAt>=0&&capturedAt<=now&&capturedAt>lastFrame)lastFrame=capturedAt;
        if(!fresh||!hud||!sameUid||identity==null||(attempts>0&&capturedAt<=lastTap))return WAIT;
        if(identity.active==target)return attempts>0?CONFIRMED:WAIT;
        if(identity.active!=source)throw new IllegalStateException("切人期间出现非目标角色，停止而非继续点击");
        if(elapsed>=5000||attempts>=3||(attempts>0&&now-lastTap<1000))return WAIT;
        if(identity.standby==null||identity.x==null||identity.y==null
                ||identity.standby.length!=2||identity.x.length!=2||identity.y.length!=2)return WAIT;
        int slot=identity.standby[0]==target?0:identity.standby[1]==target?1:-1;
        if(slot<0||identity.standby[0]==identity.standby[1])return WAIT;
        float x=identity.x[slot],y=identity.y[slot];
        if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>=1||y<0||y>=1)return WAIT;
        // Count an issued decision before dispatch; dispatch/cancellation failures propagate and end this switch, never retry blindly.
        attempts++;lastTap=now;
        return new Step(Kind.TAP,x,y);
    }
}
