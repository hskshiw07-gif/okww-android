package io.github.okww.weekly;

/** Short joystick steps toward a newly observed reward marker; no cached direction or blind search. */
final class TacetTreasureNavigation {
    private final String uid;
    private final long started;
    private long lastFrame=-1;
    private PortraitMatcher.Match candidate;
    private int missingFrames,pitchSearches;
    private int steps;

    TacetTreasureNavigation(String uid,long now) {
        if(uid==null||!uid.matches("[0-9]{9}")||now<0)throw new IllegalArgumentException("寻箱需要已核验账号和时钟");
        this.uid=uid;started=now;
    }
    static boolean postWorld(GardenRules.Screen s) {
        return postCombatHud(s)&&!s.has("领取奖励");
    }
    static GardenRules.Text interaction(GardenRules.Screen s) {
        return postCombatHud(s)?s.one("领取奖励",1530f/2400,430f/1080,1695f/2400,487f/1080):null;
    }
    private static boolean postCombatHud(GardenRules.Screen s) {
        return s.aspect>2.18f&&s.aspect<2.26f&&AccountDailyConfig.detectUid(s)!=null&&!s.automationPanelVisible()
                &&CombatHud.mobileStatus(s)
                &&s.one(".*清理无.*区.*",.02f,.16f,.40f,.42f)==null
                &&s.one("[^0-9]{0,2}[0-9]{2}:[0-9]{2}:[0-9]{2}",.42f,.08f,.59f,.18f)==null
                &&!s.has("提示|确认|取消|挑战成功|挑战失败|补充波片|兑换|购买|复苏|复活|暂停|教程");
    }
    void unconfirmedFrame(){candidate=null;missingFrames=0;}
    CombatGesture next(GardenRules.Screen screen,PortraitMatcher.Match marker,boolean ownedTreasure,
                       long captured,long now) throws InterruptedException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(now<started||now-started>45000||steps>=16)throw new IllegalStateException("寻箱未到明确交互，停止并保存现场");
        String seen=AccountDailyConfig.detectUid(screen);
        if(seen!=null&&!uid.equals(seen))throw new IllegalStateException("寻箱账号变化");
        boolean fresh=captured>=0&&captured>lastFrame&&now>=captured&&now-captured<=1500;
        boolean consecutive=lastFrame>=0&&captured-lastFrame<=2500;
        if(captured>lastFrame&&captured<=now)lastFrame=captured;
        if(!fresh||!ownedTreasure||!uid.equals(seen)||!postWorld(screen)) {unconfirmedFrame();return null;}
        if(marker==null||marker.score<.8) {
            candidate=null;missingFrames=consecutive?missingFrames+1:1;
            // Hiyuki's ending camera can point at the ground and put the marker below the HUD.
            // Only the owned post-combat phase may try two camera-only pitch corrections; never walk blind.
            if(missingFrames<2||pitchSearches>=2)return null;
            missingFrames=0;pitchSearches++;steps++;
            return new CombatGesture(2400,1080,new CombatGesture.Contact(1500f/2400,600f/1080,
                    1500f/2400,450f/1080,0,300));
        }
        missingFrames=0;
        PortraitMatcher.Match previous=candidate;candidate=marker;
        if(!consecutive||previous==null||Math.abs(previous.x-marker.x)>.025f||Math.abs(previous.y-marker.y)>.025f)return null;
        candidate=null;steps++;
        // Mobile clamps an off-screen treasure to the side with a chevron. Strafing toward that
        // screen position circles the arena; first turn the camera, then reobserve the actual target.
        // Measured on this layout: a left 150px camera drag brought the left-edge marker into view.
        if(marker.x<.25f||marker.x>.75f||marker.y>.75f) {
            // Bottom-clamped down-chevron means the reward is behind us, even with centered X.
            // Measured 450px left drag brings that rear marker into view.
            float turn=marker.y>.75f?-450:marker.x<.5f?-150:150;
            return new CombatGesture(2400,1080,new CombatGesture.Contact(1500f/2400,600f/1080,
                    (1500+turn)/2400,600f/1080,0,marker.y>.75f?450:300));
        }
        float dx=0,dy=0;
        if(Math.abs(marker.x-.5f)>.04f)dx=marker.x>.5f?100:-100;
        // Screen Y depends on mobile camera pitch, not whether we have passed the treasure.
        // Dev113 reached 6m then oscillated at Y=.55; keep approaching the aligned marker
        // until the separately verified interaction replaces it.
        else dy=-100;
        // shortcut: neutral joystick (375,808) is measured on Xiaomi 2400x1080; other controls need calibration.
        return new CombatGesture(2400,1080,new CombatGesture.Contact(375f/2400,808f/1080,
                (375+dx)/2400,(808+dy)/1080,0,650));
    }
}
