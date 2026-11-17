package io.github.okww.weekly;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;
import static io.github.okww.weekly.GardenRules.*;
import static io.github.okww.weekly.CombatTouchLayout.Control.*;

public class CombatHudTest {
    @Test public void transformationRequiresConsumedReadySkillAndObservedAnimation(){
        assertTrue(CombatHud.transformationConfirmed(CombatHud.Skill.READY,CombatHud.Skill.DIM,true));
        assertFalse(CombatHud.transformationConfirmed(CombatHud.Skill.READY,CombatHud.Skill.DIM,false));
        assertFalse(CombatHud.transformationConfirmed(CombatHud.Skill.DIM,CombatHud.Skill.DIM,true));
        assertFalse(CombatHud.transformationConfirmed(CombatHud.Skill.READY,CombatHud.Skill.READY,true));
        assertFalse(CombatHud.castConfirmed(CombatHud.Skill.READY,CombatHud.Skill.DIM));
    }
    static Text t(String text,float x,float y){return new Text(text,x-.005f,y-.005f,x+.005f,y+.005f);}
    static Screen battle(){return new Screen(new ArrayList<>(Arrays.asList(
            t("31443/314431",.495f,.937f),t("Lv.90",.58f,.938f),t("63ms",.92f,.116f),
            t("特征码:000000000",.914f,.98f),t("@清理无音区中浦现的残象:0/4",.147f,.29f),t("03:56:58",.51f,.14f))),new int[3],2400f/1080);}
    @Test public void requiresObjectiveTimerAndWorldHudTogether(){
        Screen s=battle();assertTrue(worldHud(s));assertTrue(CombatHud.tacetCombat(s));
        s.words.remove(5);assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.remove(4);assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.remove(3);assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.add(t("挑战成功",.5f,.3f));assertFalse(CombatHud.tacetCombat(s));
    }
    @Test public void ownedFightToleratesOneLostArenaLabelButNeverLostAccountOrBothLabels() {
        Screen s=battle();s.words.remove(5);assertTrue(CombatHud.tacetActive(s));assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.remove(4);assertTrue(CombatHud.tacetActive(s));assertFalse(CombatHud.tacetCombat(s));
        s.words.remove(4);assertFalse(CombatHud.tacetActive(s));
        s=battle();s.words.remove(3);assertFalse(CombatHud.tacetActive(s));
        s=battle();s.words.add(t("挑战失败",.5f,.3f));assertFalse(CombatHud.tacetActive(s));
        s=battle();s.words.add(t("提示",.30f,.32f));s.words.add(t("确认离开",.5f,.48f));
        assertFalse(CombatHud.tacetActive(s));assertFalse(CombatHud.tacetCombat(s));
    }
    static Text px(String text,int l,int t,int r,int b){return new Text(text,l/2400f,t/1080f,r/2400f,b/1080f);}
    /** 2026-11-16 second battle: countdown, enemy and HP visible, objective absent; UID sanitized. */
    static Screen repeatBattleWithoutObjective(){
        return new Screen(new ArrayList<>(Arrays.asList(
                px("LV. 87",470,350,536,377),px("03:29:13",1160,140,1288,163),
                px("令71mms",2169,117,2253,134),px("特征码: 000000000",2107,1051,2280,1069),
                px("1732/16557",1143,1005,1247,1019),px("|以90",1363,1005,1421,1025))),
                new int[3],2400f/1080);
    }
    @Test public void secondBattleReplayNeedsEnemyTimerUidAndHpWhenObjectiveIsAbsent(){
        Screen s=repeatBattleWithoutObjective();
        assertFalse(CombatHud.tacetCombat(s));assertTrue(CombatHud.tacetReentry(s));assertTrue(CombatHud.tacetActive(s));
        for(int index:new int[]{0,1,3,4,5}) {
            Screen missing=repeatBattleWithoutObjective();missing.words.remove(index);
            assertFalse("missing anchor "+index,CombatHud.tacetReentry(missing));
        }
        s.words.add(t("领取奖励",.48f,.5f));assertFalse(CombatHud.tacetReentry(s));
    }
    @Test public void recordedVictoryBannerStopsCombatWithoutBecomingARewardOffer(){
        Screen s=battle();
        s.words.add(px("挑战达成",1099,267,1306,324));
        assertTrue(CombatHud.tacetVictoryBanner(s));
        assertFalse(CombatHud.tacetContinued(s));
        assertFalse(CombatHud.tacetCombat(s));
        assertNull(TacetTreasureNavigation.interaction(s));
        assertFalse(DailyTacet.settlementPage(s));
        Screen post=new Screen(new ArrayList<>(s.words),new int[3],2400f/1080);
        post.words.remove(5);post.words.remove(4);
        assertFalse(TacetTreasureNavigation.postWorld(post));
        s.words.set(s.words.size()-1,t("挑战达成",.10f,.50f));
        assertFalse(CombatHud.tacetVictoryBanner(s));
        assertTrue(CombatHud.tacetCombat(s));
    }
    @Test public void recordedForeignLevelGlyphNeedsIndependentHpLatencyAndUniqueLevelRegion() {
        Screen s=battle();s.words.set(1,px("Lい90",1371,1002,1416,1024));
        s.words.set(0,px("6482/15980",1139,1005,1247,1019));
        s.words.set(5,px("G02:37:96",1120,137,1289,163));
        assertTrue(CombatHud.tacetCombat(s));assertTrue(CombatHud.tacetContinued(s));
        Screen post=new Screen(new ArrayList<>(s.words),new int[3],2400f/1080);post.words.remove(5);post.words.remove(4);
        assertTrue(TacetTreasureNavigation.postWorld(post));post.words.remove(2);assertFalse(TacetTreasureNavigation.postWorld(post));
        for(int index:new int[]{0,1,2,3}) {
            Screen missing=new Screen(new ArrayList<>(s.words),new int[3],2400f/1080);missing.words.remove(index);
            assertFalse("missing anchor "+index,CombatHud.tacetCombat(missing));
        }
        s.words.add(t("90",.57f,.935f));assertFalse(CombatHud.tacetCombat(s));s.words.remove(s.words.size()-1);
        s.words.set(0,t("6482/15980",.4f,.5f));assertFalse(CombatHud.tacetCombat(s));
    }
    @Test public void recordedUShapedLevelStillNeedsArenaTimerUidAndMeasuredRegion() {
        Screen s=battle();s.words.set(1,px("u.90",1372,1004,1416,1022));
        s.words.set(0,px("17382AS348",1134,1005,1252,1017));
        s.words.set(4,px("清理无音区中涌现的残家:01:",173,299,565,335));
        s.words.set(5,px("02:57:01",1159,140,1283,163));
        assertTrue(CombatHud.tacetActive(s));assertTrue(CombatHud.tacetContinued(s));
        assertTrue(worldHud(s));assertFalse(CombatHud.tacetCombat(s));
        s.words.remove(5);assertFalse(CombatHud.tacetActive(s));
        s=battle();s.words.set(1,t("u.90",.5f,.5f));assertFalse(CombatHud.tacetActive(s));
        s=battle();s.words.set(1,t("u.90",.58f,.938f));s.words.remove(3);assertFalse(CombatHud.tacetActive(s));
    }
    /** dev81 device OCR at the failed switch: both arena labels unread, HP line and account still present. */
    static Screen dev81LostLabels(){return new Screen(new ArrayList<>(Arrays.asList(
            px("Lv. 88",924,207,973,220),px("14846/18882",1137,1001,1250,1020),px("Lv90",1371,1002,1416,1025),
            px("特征码:444444444",2097,1049,2287,1069))),new int[3],2400f/1080);}
    @Test public void dev81LostArenaLabelsOnlyContinueThroughHpLineNeverEnterOrCountAsArena() {
        Screen s=dev81LostLabels();
        assertFalse(CombatHud.tacetActive(s));assertFalse(CombatHud.tacetCombat(s));
        assertTrue(CombatHud.tacetContinued(s));
        s=dev81LostLabels();s.words.remove(1);assertFalse(CombatHud.tacetContinued(s));
        s=dev81LostLabels();s.words.remove(3);assertFalse(CombatHud.tacetContinued(s));
        s=dev81LostLabels();s.words.remove(2);assertFalse(CombatHud.tacetContinued(s));
        s=dev81LostLabels();s.words.add(t("挑战成功",.5f,.3f));assertFalse(CombatHud.tacetContinued(s));
        s=dev81LostLabels();s.words.add(t("复苏",.5f,.5f));assertFalse(CombatHud.tacetContinued(s));
        s=dev81LostLabels();s.words.set(1,px("14846/18882",200,500,313,519));assertFalse(CombatHud.tacetContinued(s));
        s=battle();s.words.remove(5);s.words.remove(4);assertTrue(CombatHud.tacetContinued(s));
        assertEquals(8000,TacetCombatPort.ARENA_LABEL_GRACE_MS);
    }
    @Test public void arenaTimerClockGlyphAndBadLatencyStillRequireUidLevelAndObjective() {
        Screen s=battle();s.words.set(5,t("G02:50:76",.51f,.14f));s.words.set(2,t("令75nTs",.92f,.116f));
        assertTrue(CombatHud.tacetCombat(s));s.words.remove(1);assertFalse(CombatHud.tacetCombat(s));
    }
    @Test public void recordedChineseLevelGlyphNeedsUniqueMeasuredRegionAndArenaAnchors() {
        Screen s=battle();s.words.set(1,t("以90",1392f/2400,1015f/1080));
        s.words.set(2,t("令63nS",.92f,.116f));
        assertTrue(CombatHud.tacetCombat(s));
        assertEquals(Kind.BACK,new DailyRules(new DailyTacet()).next(s).kind);
        s.words.add(t("90",.57f,.935f));assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.set(1,t("以90",.5f,.5f));assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.set(1,t("以90",.58f,.938f));s.words.remove(3);assertFalse(CombatHud.tacetCombat(s));
        s=battle();s.words.set(1,t("以90",.58f,.938f));s.words.remove(5);s.words.remove(4);
        assertFalse(CombatHud.tacetActive(s));
    }
    @Test public void recordedSnowBackgroundObjectiveCanLoseCountButNotRegionOrOtherAnchors() {
        Screen s=battle();s.words.set(4,t("@清理无普区中消现的残象:0/",.147f,.29f));
        assertTrue(CombatHud.tacetCombat(s));s.words.set(4,t("@清理无普区中消现的残象:0/",.50f,.50f));
        assertFalse(CombatHud.tacetCombat(s));
    }
    @Test public void tinyHudDetailRetryIsScopedToKnownAccountBottomLevel() {
        Screen s=battle();s.words.set(1,t("L.90",.58f,.938f));s.words.set(2,t("令75nTs",.92f,.116f));
        assertTrue(DailyRules.needsHudDetail(s));s.words.remove(3);assertFalse(DailyRules.needsHudDetail(s));
        assertFalse(DailyRules.needsHudDetail(battle()));
    }
    @Test public void duplicateStatusFragmentsWaitButAmbiguousButtonsStillThrow() {
        Screen s=battle();s.words.set(1,t("L.90",.58f,.938f));s.words.remove(0);
        s.words.add(t("90",.57f,.935f));assertFalse(worldHud(s));
        assertFalse(CombatHud.tacetActive(s));assertFalse(DailyRules.needsHudDetail(s));
        s.words.add(t("领取",.8f,.8f));s.words.add(t("领取",.9f,.9f));
        assertThrows(IllegalStateException.class,()->s.one("领取"));
    }
    @Test public void recoveryEnlargesHpEvenWhenLevelAlreadyMakesWorldHudRecognizable() {
        Screen s=battle();s.words.remove(0);
        assertTrue(worldHud(s));assertFalse(DailyRules.needsHudDetail(s));
        assertTrue(DailyRules.needsHudDetail(s,true));
        s.words.remove(2);assertFalse(DailyRules.needsHudDetail(s,true));
        assertFalse(DailyRules.needsHudDetail(new Screen(battle().words,new int[3],16f/9),true));
    }
    @Test public void readyRequiresBrightIconAndNumericCooldownWins(){
        int[] pixels=new int[2400*1080];Screen s=battle();
        for(int y=920;y<946;y++)for(int x=1770;x<1790;x++)pixels[y*2400+x]=0xffffffff;
        PortraitMatcher.Pixels p=new PortraitMatcher.Pixels(2400,1080,pixels);
        CombatHud h=new CombatHud(p,s,0);assertEquals(CombatHud.Skill.READY,h.skills.get(RESONANCE));
        assertEquals(CombatHud.Skill.DIM,h.skills.get(LIBERATION));assertFalse(h.forteFull);
        s.words.add(t("9.1",1780f/2400,933f/1080));
        assertEquals(CombatHud.Skill.COOLDOWN,new CombatHud(p,s,0).skills.get(RESONANCE));
    }
    @Test public void concertoNeedsAlmostWholeElementColoredRing(){
        int[] pixels=new int[2400*1080];PortraitMatcher.Pixels p=new PortraitMatcher.Pixels(2400,1080,pixels);
        assertFalse(CombatHud.ringFull(p,990,979,0));
        for(int a=0;a<72;a++) {double v=a*Math.PI/36;int x=990+(int)Math.round(24*Math.cos(v)),y=979+(int)Math.round(24*Math.sin(v));pixels[y*2400+x]=0xffd2c864;}
        assertTrue(CombatHud.ringFull(p,990,979,0));assertFalse(CombatHud.ringFull(p,990,979,1));
    }
}
