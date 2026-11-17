package io.github.okww.weekly;

import java.util.EnumMap;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

/** Pixel/CD readings in the measured 2400x1080 mobile HUD, not desktop key-label boxes. */
final class CombatHud {
    enum Skill { READY, COOLDOWN, DIM }
    static boolean castConfirmed(Skill before,Skill after){return before==Skill.READY&&after==Skill.COOLDOWN;}
    static boolean transformationConfirmed(Skill before,Skill after,boolean animation){
        return before==Skill.READY&&after==Skill.DIM&&animation;
    }
    final EnumMap<CombatTouchLayout.Control, Skill> skills = new EnumMap<>(CombatTouchLayout.Control.class);
    final boolean concertoFull, forteFull;
    CombatHud(PortraitMatcher.Pixels frame, GardenRules.Screen screen, int element) {
        this(frame, screen, element, tacetContinued(screen));
    }
    CombatHud(PortraitMatcher.Pixels frame, GardenRules.Screen screen, int element, boolean verifiedHud) {
        if (frame.width != 2400 || frame.height != 1080 || !verifiedHud || screen.automationPanelVisible())
            throw new IllegalStateException("战斗HUD未核验，未读取技能就绪");
        CombatTouchLayout layout = CombatTouchLayout.forXiaomi13Pro();
        for (CombatTouchLayout.Control c : new CombatTouchLayout.Control[]{RESONANCE,LIBERATION,ECHO}) {
            CombatGesture.Contact p=layout.point(c);int x=Math.round(p.x*2400),y=Math.round(p.y*1080);
            boolean cd=screen.one("[0-9]{1,2}(?:[.,][0-9])?[sS]?",(x-40)/2400f,(y-32)/1080f,(x+40)/2400f,(y+32)/1080f)!=null;
            skills.put(c,cd ? Skill.COOLDOWN : white(frame,x-20,y-20,x+21,y+21) >= .025 ? Skill.READY : Skill.DIM);
        }
        // Measured ring and forte endpoint in arena-shore.png; source color thresholds are retained.
        concertoFull=ringFull(frame,990,979,element);
        forteFull=white(frame,1310,974,1340,987)>.08;
    }
    static double white(PortraitMatcher.Pixels p,int l,int t,int r,int b) {
        int n=0;
        for(int y=t;y<b;y++)for(int x=l;x<r;x++){
            int c=p.argb[y*p.width+x],red=c>>16&255,green=c>>8&255,blue=c&255;
            if(red>=244&&green>=246&&blue>=250)n++;
        }
        return n/(double)((r-l)*(b-t));
    }
    private static final int[][] COLORS={{205,235,190,222,90,130},{150,190,95,140,210,249},
            {200,230,100,130,75,105},{60,95,150,180,210,245},{70,110,215,250,155,190},{190,220,65,105,145,175}};
    static boolean ringFull(PortraitMatcher.Pixels p,int cx,int cy,int element) {
        if(element<0||element>=COLORS.length)throw new IllegalStateException("协奏属性尚未识别");
        int[] range=COLORS[element];int filled=0;
        for(int angle=0;angle<72;angle++) {
            double theta=angle*Math.PI/36;boolean found=false;
            for(int radius=22;radius<=26;radius++) {
                int x=cx+(int)Math.round(radius*Math.cos(theta)),y=cy+(int)Math.round(radius*Math.sin(theta));
                int c=p.argb[y*p.width+x],r=c>>16&255,g=c>>8&255,b=c&255;
                found|=r>=range[0]&&r<=range[1]&&g>=range[2]&&g<=range[3]&&b>=range[4]&&b<=range[5];
            }
            if(found)filled++;
        }
        return filled>=70;
    }
    static boolean tacetCombat(GardenRules.Screen s) {
        return tacetActive(s) && objective(s) && timer(s);
    }
    static boolean tacetReentry(GardenRules.Screen s) {
        return anchors(s)&&timer(s)
                &&s.hasOne("[0-9]{1,6}/[0-9]{3,6}",.46f,.91f,.535f,.955f)
                &&s.text(.08f,.16f,.90f,.70f).matches("(?i).*(?:^|\\|)Lv[.]?[0-9]{1,3}(?:\\||$).*");
    }
    static boolean tacetVictoryBanner(GardenRules.Screen s) {
        return s.aspect>2.18f&&s.aspect<2.26f&&!s.automationPanelVisible()
                &&s.hasOne("挑战达成",.44f,.23f,.56f,.32f);
    }
    // Only the already-entered combat Port uses this; initial entry still requires both arena anchors.
    static boolean tacetActive(GardenRules.Screen s) {
        return anchors(s) && (objective(s)||timer(s));
    }
    /** Device dev81: skill effects hid both arena labels mid-fight; the caller must bound this by a recent tacetActive frame. */
    static boolean tacetContinued(GardenRules.Screen s) {
        return anchors(s) && (objective(s)||timer(s)||s.one("[0-9]{3,6}/[0-9]{3,6}",.46f,.91f,.535f,.955f)!=null);
    }
    static boolean mobileStatus(GardenRules.Screen s) {
        // Dev111 read Lv.90 as Lい90. An unknown token also needs independent numeric HP and latency.
        return s.hasOne(".{1,6}",.55f,.90f,.61f,.96f)
                && (s.hasOne("(?i)(?:[Lliwvuta]{1,2}[.]?|[.]|[|丨]?以)?[0-9]{1,3}",.55f,.90f,.61f,.96f)
                    || s.hasOne("[0-9]{1,6}/[0-9]{3,6}",.46f,.91f,.535f,.955f)
                    && s.hasOne(".{0,2}[0-9]{1,4}ms",.84f,.08f,.98f,.17f));
    }
    private static boolean anchors(GardenRules.Screen s) {
        return s.aspect>2.18f && s.aspect<2.26f && !s.automationPanelVisible() && AccountDailyConfig.detectUid(s)!=null
                && !tacetVictoryBanner(s)
                && mobileStatus(s)
                && s.one("提示",.28f,.28f,.34f,.36f)==null
                && !s.has("挑战成功|挑战失败|领取奖励|补充波片|复苏|复活");
    }
    private static boolean objective(GardenRules.Screen s) {return s.one(".*清理无[音普]区.*残[象像].*",.02f,.16f,.40f,.42f)!=null;}
    private static boolean timer(GardenRules.Screen s) {return s.one("[^0-9]{0,2}[0-9]{2}:[0-9]{2}:[0-9]{2}",.42f,.08f,.59f,.18f)!=null;}
}
