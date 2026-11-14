package io.github.okww.weekly;

/** Short joystick steps toward a newly observed reward marker; no cached direction or blind search. */
final class TacetTreasureNavigation {
    private final String uid;
    private final long started;
    private long lastFrame=-1;
    private PortraitMatcher.Match candidate;
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
                &&s.hasOne("(?i)(?:[Lliwvta]{1,2}[.]?|[.]|以)?[0-9]{1,3}",.55f,.90f,.61f,.96f)
                &&s.one(".*清理无.*区.*",.02f,.16f,.40f,.42f)==null
                &&s.one("[^0-9]{0,2}[0-9]{2}:[0-9]{2}:[0-9]{2}",.42f,.08f,.59f,.18f)==null
                &&!s.has("提示|确认|取消|挑战成功|挑战失败|补充波片|兑换|购买|复苏|复活|暂停|教程");
    }
    void unconfirmedFrame(){candidate=null;}
    CombatGesture next(GardenRules.Screen screen,PortraitMatcher.Match marker,boolean ownedTreasure,
                       long captured,long now) throws InterruptedException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(now<started||now-started>45000||steps>=16)throw new IllegalStateException("寻箱未到明确交互，停止并保存现场");
        String seen=AccountDailyConfig.detectUid(screen);
        if(seen!=null&&!uid.equals(seen))throw new IllegalStateException("寻箱账号变化");
        boolean fresh=captured>=0&&captured>lastFrame&&now>=captured&&now-captured<=1500;
        boolean consecutive=lastFrame>=0&&captured-lastFrame<=2500;
        if(captured>lastFrame&&captured<=now)lastFrame=captured;
        if(!fresh||!ownedTreasure||!uid.equals(seen)||!postWorld(screen)||marker==null||marker.score<.8) {candidate=null;return null;}
        PortraitMatcher.Match previous=candidate;candidate=marker;
        if(!consecutive||previous==null||Math.abs(previous.x-marker.x)>.025f||Math.abs(previous.y-marker.y)>.025f)return null;
        candidate=null;steps++;
        float dx=0,dy=0;
        if(Math.abs(marker.x-.5f)>.04f)dx=marker.x>.5f?100:-100;
        else dy=marker.y>.55f?100:-100;
        // shortcut: neutral joystick (375,808) is measured on Xiaomi 2400x1080; other controls need calibration.
        return new CombatGesture(2400,1080,new CombatGesture.Contact(375f/2400,808f/1080,
                (375+dx)/2400,(808+dy)/1080,0,650));
    }
}
